package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.wearlink.EpisodeListReply
import cx.aswin.boxlore.core.wearlink.EpisodeListRequest
import cx.aswin.boxlore.core.wearlink.LinkItem
import cx.aswin.boxlore.core.wearlink.PhoneLibrary
import cx.aswin.boxlore.core.wearlink.PhoneNowPlaying
import cx.aswin.boxlore.core.wearlink.WatchNowPlaying
import cx.aswin.boxlore.core.wearlink.WearLinkCodec
import cx.aswin.boxlore.core.wearlink.WearLinkCommand
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import cx.aswin.boxlore.core.wearlink.WearLinkProtocol
import cx.aswin.boxlore.core.wearlink.WearLinkSettings
import cx.aswin.boxlore.core.wearlink.WearLinkTransport
import cx.aswin.boxlore.wear.data.WatchDownloads
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.WatchSettings
import cx.aswin.boxlore.wear.model.WatchDownload
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the rest of the watch app sees of the phone. */
interface WatchLink {
    /** Whether the phone app can be reached right now. */
    val phoneReachable: StateFlow<Boolean>

    /** The phone's player as last heard, or null when the phone has never said. */
    val phoneNowPlaying: StateFlow<PhoneNowPlaying?>

    /** The phone's saved episodes of a show, or null when it is out of reach or has none. */
    suspend fun episodes(showId: String): List<WearLinkEpisode>?

    /** Sends a player command to the phone. [onLost] runs when it could not be handed over. */
    fun send(command: WearLinkCommand, onLost: () -> Unit = {})
}

/** A watch with no phone link. */
object NoWatchLink : WatchLink {
    override val phoneReachable: StateFlow<Boolean> = MutableStateFlow(false)
    override val phoneNowPlaying: StateFlow<PhoneNowPlaying?> = MutableStateFlow(null)

    override suspend fun episodes(showId: String): List<WearLinkEpisode>? = null

    override fun send(command: WearLinkCommand, onLost: () -> Unit) = onLost()
}

/** What the link needs to know about the watch's own player. */
data class PlayerGlance(val loadedEpisodeId: String?, val isPlaying: Boolean)

/** How long the watch waits before telling the phone about a change. */
internal object PublishPacing {
    /** Lets a burst of changes settle into one message. */
    const val SETTLE_MS = 2_000L

    /** While the watch plays, progress goes out about this often instead of with every save. */
    const val PLAYING_INTERVAL_MS = 60_000L

    fun waitMs(isPlaying: Boolean, sinceLastMs: Long): Long =
        if (isPlaying) (PLAYING_INTERVAL_MS - sinceLastMs).coerceIn(SETTLE_MS, PLAYING_INTERVAL_MS) else SETTLE_MS
}

/**
 * The live phone link. It merges what the phone publishes into the watch's library, tells the
 * phone what changed on the watch, and keeps the two sides' watch settings the same. Everything
 * it publishes is kept by the system, so it reaches the phone the next time the two connect.
 */
class LiveWatchLink(
    private val transport: WearLinkTransport,
    private val store: WatchLibraryStore,
    private val settings: WatchSettings,
    private val downloads: WatchDownloads,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Receives one line per exchange with the phone, for the log. */
    private val log: (String) -> Unit = {},
) : WatchLink {
    private val phonePlayer = MutableStateFlow<PhoneNowPlaying?>(null)
    private var lastPublishedAt = 0L

    /** Set once the watch's player exists. */
    @Volatile
    var player: () -> PlayerGlance = { PlayerGlance(null, isPlaying = false) }

    override val phoneReachable: StateFlow<Boolean> = transport.peerReachable
    override val phoneNowPlaying: StateFlow<PhoneNowPlaying?> = phonePlayer.asStateFlow()

    fun start() {
        store.update { if (it.watchId.isBlank()) it.copy(watchId = UUID.randomUUID().toString()) else it }
        scope.launch { transport.refreshPeer() }
        scope.launch { transport.changes(WearLinkProtocol.PHONE_PREFIX).collect(::take) }
        scope.launch { transport.current(WearLinkProtocol.PHONE_PREFIX).forEach(::take) }
        scope.launch { publishChanges() }
        scope.launch {
            settings.linkSettings.collect { transport.publish(WearLinkProtocol.PATH_WATCH_SETTINGS, WearLinkCodec.encode(it)) }
        }
    }

    /** Tells the phone whether the watch's own player is playing, so the two never play at once unnoticed. */
    fun publishPlaying(isPlaying: Boolean, episodeId: String?) {
        scope.launch {
            val note = WatchNowPlaying(isPlaying = isPlaying, episodeId = episodeId, at = clock())
            transport.publish(WearLinkProtocol.PATH_WATCH_NOW_PLAYING, WearLinkCodec.encode(note))
        }
    }

    override suspend fun episodes(showId: String): List<WearLinkEpisode>? {
        if (!transport.peerReachable.value) return null
        val answer = transport.request(WearLinkProtocol.PATH_EPISODES, WearLinkCodec.encode(EpisodeListRequest(showId)))
        return WearLinkCodec.decode<EpisodeListReply>(answer)?.episodes?.takeIf { it.isNotEmpty() }
    }

    override fun send(command: WearLinkCommand, onLost: () -> Unit) {
        scope.launch {
            val handedOver = transport.send(WearLinkProtocol.PATH_COMMAND, WearLinkCodec.encode(command))
            log("command ${command.action}: sent=$handedOver")
            if (!handedOver) onLost()
        }
    }

    private fun take(item: LinkItem) {
        when (item.path) {
            WearLinkProtocol.PATH_PHONE_LIBRARY -> WearLinkCodec.decode<PhoneLibrary>(item.bytes)?.let(::merge)
            WearLinkProtocol.PATH_PHONE_NOW_PLAYING -> {
                val playing = WearLinkCodec.decode<PhoneNowPlaying>(item.bytes)
                phonePlayer.value = playing
                log("phone player: ${playing?.episode?.episodeId} playing=${playing?.isPlaying} at ${playing?.positionMs} ms")
            }
            WearLinkProtocol.PATH_PHONE_SETTINGS ->
                WearLinkCodec.decode<WearLinkSettings>(item.bytes)?.let { remote ->
                    val applied = settings.applyRemote(remote)
                    log("phone settings: layout ${remote.showsLayout}, limit ${remote.storageLimitMb} MB, taken=$applied")
                }
        }
    }

    private fun merge(library: PhoneLibrary) {
        val loaded = player().loadedEpisodeId
        val before = store.state.value.played
        store.update { WatchLinkRules.withPhone(it, library, loaded) }
        val after = store.state.value
        val newest = library.history.firstOrNull()
        log("phone library: ${library.shows.size} shows, ${library.history.size} rows, confirmed ${library.acked[after.watchId] ?: 0L}, newest ${newest?.episodeId} at ${newest?.progressMs} ms done=${newest?.isCompleted}")
        // An episode finished on the phone no longer needs its audio on the watch.
        (after.played - before)
            .filter { it != loaded && after.episodes[it]?.download == WatchDownload.Done }
            .forEach(downloads::remove)
    }

    private suspend fun publishChanges() {
        store.state
            .map { LinkKey(it.revision, it.pendingShows.size, it.pendingEpisodes.size, it.storedBytes) }
            .distinctUntilChanged()
            .collectLatest {
                delay(PublishPacing.waitMs(player().isPlaying, clock() - lastPublishedAt))
                val changes = WatchLinkRules.changes(store.state.value)
                val sent = transport.publish(WearLinkProtocol.PATH_WATCH_CHANGES, WearLinkCodec.encode(changes))
                if (sent) lastPublishedAt = clock()
                log("watch changes: revision ${changes.revision}, ${changes.subscriptions.size} follows, ${changes.history.size} rows, sent=$sent")
            }
    }

    /** The parts of the library whose change means the phone has something new to hear. */
    private data class LinkKey(val revision: Long, val pendingShows: Int, val pendingEpisodes: Int, val storedBytes: Long)
}
