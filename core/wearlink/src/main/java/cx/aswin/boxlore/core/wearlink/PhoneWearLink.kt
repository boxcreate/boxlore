package cx.aswin.boxlore.core.wearlink

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** When the phone tells the watch that its player changed. */
object PhoneLinkRules {
    /** A position this far from where steady playback would be is a seek, not drift. */
    const val JUMP_MS = 5_000L

    /**
     * Whether [next] says something the watch cannot work out from [last]: another episode, its
     * length once the player knows it, another queue, a start or stop, another speed, or a jump. Steady playback
     * is never news, so the phone does not talk to the watch while an episode simply plays.
     */
    fun changed(last: PhoneNowPlaying?, next: PhoneNowPlaying, now: Long): Boolean =
        last == null ||
            last.episode?.episodeId != next.episode?.episodeId ||
            last.episode?.durationMs != next.episode?.durationMs ||
            last.upNext.map(WearLinkEpisode::episodeId) != next.upNext.map(WearLinkEpisode::episodeId) ||
            last.isPlaying != next.isPlaying ||
            last.speed != next.speed ||
            abs(last.positionAt(now) - next.positionMs) > JUMP_MS

    /**
     * Whether the phone starting to play takes over from a playing watch, so the watch has to be
     * paused and the listener offered the watch back. A start the watch itself asked for within
     * [COMMAND_GRACE_MS] is the listener's own choice of the phone, and is no take-over.
     */
    fun takesOver(last: PhoneNowPlaying?, next: PhoneNowPlaying, watch: WatchNowPlaying?, sinceWatchCommandMs: Long): Boolean =
        next.isPlaying && last?.isPlaying != true && watch?.isPlaying == true && sinceWatchCommandMs > COMMAND_GRACE_MS

    const val COMMAND_GRACE_MS = 10_000L

    /** Whether the change also moves listening history: another episode, or a start or stop. */
    fun movesHistory(last: PhoneNowPlaying?, next: PhoneNowPlaying): Boolean =
        last?.episode?.episodeId != next.episode?.episodeId || last?.isPlaying != next.isPlaying
}

/**
 * The phone's end of the watch link. It does nothing until boxlore on a watch has made contact
 * once, so a phone without a watch never pays for it. From then on it keeps the watch told about
 * followed shows, recent listening and the player, merges what the listener did on the watch,
 * answers the watch's questions, and keeps the watch's settings the same on both sides.
 */
class PhoneWearLink(
    private val transport: WearLinkTransport,
    private val library: PhoneLibraryPort,
    private val player: PhonePlayerPort,
    private val store: PhoneLinkStore,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val started = AtomicBoolean(false)
    private val merging = Mutex()

    // A conflated channel, not a shared flow: a request made before the publisher is listening must not be lost.
    private val libraryRequests = Channel<Unit>(Channel.CONFLATED)
    private val linkStatus = MutableStateFlow(WearLinkStatus(store.watchKnown, transport.peerReachable.value, store.watchStatus))
    private val watchSettings = MutableStateFlow(store.settings)
    private var lastPlaying: PhoneNowPlaying? = null
    private val watchPlayer = MutableStateFlow<WatchNowPlaying?>(null)
    private val offer = MutableStateFlow(false)

    @Volatile
    private var watchCommandAt = 0L

    /**
     * True from the moment this phone started playing while the watch was, and the watch was
     * paused for it: the listener may have meant the watch. Cleared by [dismissPlayOnWatchOffer]
     * or [playOnWatch].
     */
    val playOnWatchOffer: StateFlow<Boolean> = offer.asStateFlow()

    val status: StateFlow<WearLinkStatus> = linkStatus.asStateFlow()
    val settings: StateFlow<WearLinkSettings> = watchSettings.asStateFlow()

    /** Begins keeping the watch up to date. Safe to call often; it only ever starts once. */
    fun start() {
        if (!store.watchKnown || !started.compareAndSet(false, true)) return
        scope.launch { transport.peerReachable.collect { reachable -> linkStatus.value = linkStatus.value.copy(reachable = reachable) } }
        scope.launch { library.showsChanged.collect { libraryRequests.trySend(Unit) } }
        scope.launch { player.state.collect(::onPlayer) }
        scope.launch {
            libraryRequests.receiveAsFlow().collectLatest {
                delay(SETTLE_MS)
                publishLibrary()
            }
        }
        scope.launch {
            transport.refreshPeer()
            transport.current(WearLinkProtocol.WATCH_PREFIX).forEach { onItem(it) }
            publishSettings()
            libraryRequests.trySend(Unit)
        }
    }

    fun dismissPlayOnWatchOffer() {
        offer.value = false
    }

    /**
     * Moves what the phone is playing to the watch: the episode, its place and the queue after it.
     * The phone only stops once the watch has been handed the episode, so a watch out of reach
     * never leaves the listener in silence.
     */
    fun playOnWatch() {
        offer.value = false
        scope.launch {
            // Asked afresh: the last report can be seconds old, and a hand-over must not rewind.
            val live = attempt { player.state.first() }
            val episode = live?.episode ?: lastPlaying?.episode ?: return@launch
            val position = live?.positionMs ?: lastPlaying?.positionAt(clock()) ?: 0L
            val upNext = (live?.upNext ?: lastPlaying?.upNext).orEmpty().take(PhoneNowPlaying.UP_NEXT_LIMIT)
            val command = WearLinkCommand(WearLinkCommand.PLAY_EPISODE, positionMs = position, episode = episode, upNext = upNext)
            if (transport.send(WearLinkProtocol.PATH_WATCH_COMMAND, WearLinkCodec.encode(command))) attempt { player.pause() }
        }
    }

    fun setShowsLayout(layout: String) = changeSettings { it.copy(showsLayout = layout) }

    fun setStorageLimitMb(megabytes: Int) {
        if (megabytes in WearLinkSettings.STORAGE_LIMIT_CHOICES_MB) changeSettings { it.copy(storageLimitMb = megabytes) }
    }

    /** Something the watch published, handed over by the system. */
    suspend fun onItem(item: LinkItem) {
        when (item.path) {
            WearLinkProtocol.PATH_WATCH_CHANGES -> WearLinkCodec.decode<WatchChanges>(item.bytes)?.let { onChanges(it) }
            WearLinkProtocol.PATH_WATCH_SETTINGS -> WearLinkCodec.decode<WearLinkSettings>(item.bytes)?.let { onWatchSettings(it) }
            WearLinkProtocol.PATH_WATCH_NOW_PLAYING -> watchPlayer.value = WearLinkCodec.decode<WatchNowPlaying>(item.bytes)
        }
    }

    /** A player command the watch sent. */
    suspend fun onCommand(bytes: ByteArray) {
        val command = WearLinkCodec.decode<WearLinkCommand>(bytes) ?: return
        // A start the watch asks for is the listener choosing the phone, not the phone taking over.
        if (command.action == WearLinkCommand.PLAY_EPISODE || command.action == WearLinkCommand.TOGGLE) watchCommandAt = clock()
        // A player that fails must never take the app down with it: this runs on the app's own scope.
        attempt {
            when (command.action) {
                WearLinkCommand.TOGGLE -> player.togglePlayPause()
                WearLinkCommand.PAUSE -> player.pause()
                WearLinkCommand.SEEK_BACK -> player.seekBack()
                WearLinkCommand.SEEK_FORWARD -> player.seekForward()
                WearLinkCommand.SET_SPEED -> command.speed?.let { player.setSpeed(it) }
                WearLinkCommand.PLAY_EPISODE -> command.episode?.let { player.play(it, command.positionMs ?: 0L, command.upNext) }
            }
        }
    }

    /** A question the watch asked; null when it is not one this app answers. */
    suspend fun onRequest(path: String, bytes: ByteArray): ByteArray? {
        if (path != WearLinkProtocol.PATH_EPISODES) return null
        val request = WearLinkCodec.decode<EpisodeListRequest>(bytes) ?: return null
        val episodes = attempt { library.episodes(request.podcastId, request.limit.coerceIn(1, MAX_EPISODES)) }.orEmpty()
        return WearLinkCodec.encode(EpisodeListReply(request.podcastId, episodes))
    }

    private suspend fun onChanges(changes: WatchChanges) {
        store.watchKnown = true
        store.watchStatus = WatchStatus(changes.storedBytes, changes.downloads, clock())
        linkStatus.value = linkStatus.value.copy(watchKnown = true, watch = store.watchStatus)
        start()
        val merged =
            merging.withLock {
                val acked = store.acked()[changes.watchId] ?: 0L
                // A watch whose count went backwards started over; everything it holds is new again.
                val from = if (changes.revision < acked) 0L else acked
                val subscriptions = changes.subscriptions.filter { it.revision > from }.map { it.change }
                val history = changes.history.filter { it.revision > from }.map { it.row }
                val applied = (subscriptions.isEmpty() && history.isEmpty()) || attempt { library.apply(subscriptions, history) } != null
                if (applied && changes.revision != acked) store.setAcked(changes.watchId, changes.revision)
                applied && changes.revision != acked
            }
        // The answer carries the confirmation, so the watch can stop repeating itself.
        if (merged) libraryRequests.trySend(Unit)
    }

    private suspend fun onWatchSettings(remote: WearLinkSettings) {
        store.watchKnown = true
        linkStatus.value = linkStatus.value.copy(watchKnown = true)
        start()
        if (remote.changedAt <= watchSettings.value.changedAt) return
        store.settings = remote
        watchSettings.value = remote
        publishSettings()
    }

    private fun changeSettings(change: (WearLinkSettings) -> WearLinkSettings) {
        val next = change(watchSettings.value).copy(changedAt = clock())
        store.settings = next
        watchSettings.value = next
        scope.launch { publishSettings() }
    }

    private suspend fun publishSettings() {
        transport.publish(WearLinkProtocol.PATH_PHONE_SETTINGS, WearLinkCodec.encode(watchSettings.value))
    }

    private suspend fun publishLibrary() {
        val rows = attempt { library.library(HISTORY_LIMIT) } ?: return
        val snapshot = PhoneLibrary(shows = rows.shows, history = rows.history, acked = store.acked())
        transport.publish(WearLinkProtocol.PATH_PHONE_LIBRARY, WearLinkCodec.encodeLibrary(snapshot))
    }

    private suspend fun onPlayer(state: PhonePlayerState) {
        val now = clock()
        val upNext = state.upNext.take(PhoneNowPlaying.UP_NEXT_LIMIT)
        val next = PhoneNowPlaying(state.episode, state.isPlaying, state.positionMs, state.speed, now, upNext)
        val last = lastPlaying
        if (PhoneLinkRules.takesOver(last, next, watchPlayer.value, now - watchCommandAt)) {
            // The two never play at once: the watch stops, and the listener is offered the watch back.
            watchPlayer.value = watchPlayer.value?.copy(isPlaying = false)
            transport.send(WearLinkProtocol.PATH_WATCH_COMMAND, WearLinkCodec.encode(WearLinkCommand(WearLinkCommand.PAUSE)))
            offer.value = true
        }
        if (!PhoneLinkRules.changed(last, next, now)) return
        lastPlaying = next
        transport.publish(WearLinkProtocol.PATH_PHONE_NOW_PLAYING, WearLinkCodec.encode(next))
        if (PhoneLinkRules.movesHistory(last, next)) libraryRequests.trySend(Unit)
    }

    companion object {
        /** Recent history rows the watch is told about: Jump back in and what was just finished. */
        const val HISTORY_LIMIT = 30
        const val MAX_EPISODES = 50

        /** Lets a burst of changes settle into one message. */
        const val SETTLE_MS = 2_000L
    }
}

/** A failure reading or writing the library, or in the player, must not take the app down with it. */
private suspend fun <T> attempt(call: suspend () -> T): T? =
    runCatching { call() }
        .onFailure { if (it is CancellationException) throw it }
        .getOrNull()
