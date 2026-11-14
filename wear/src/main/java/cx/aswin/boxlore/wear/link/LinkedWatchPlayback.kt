package cx.aswin.boxlore.wear.link

import cx.aswin.boxlore.core.wearlink.PhoneNowPlaying
import cx.aswin.boxlore.core.wearlink.WearLinkCodec
import cx.aswin.boxlore.core.wearlink.WearLinkCommand
import cx.aswin.boxlore.core.wearlink.WearLinkEpisode
import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.WatchPlayback
import cx.aswin.boxlore.wear.data.handOver
import cx.aswin.boxlore.wear.data.takeOver
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.model.WatchEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a tap on play or pause does while the watch shows the phone's player. */
internal enum class PhoneTap { TellPhone, PlayHere }

/** Which player the watch shows and controls: its own, or the phone's. */
internal object PlaceRules {
    private const val MINUTE_MS = 60_000L

    /**
     * A paused phone that cannot be reached cannot be told to play, so the episode carries on here
     * from where the phone left it. Anything else is the phone's to do.
     */
    fun onToggle(phonePlaying: Boolean, phoneReachable: Boolean): PhoneTap =
        if (!phonePlaying && !phoneReachable) PhoneTap.PlayHere else PhoneTap.TellPhone

    /**
     * What Up next lists while [shown] is the phone's player: the phone's queue, each episode as the
     * watch's own copy where it has one. Null when the watch's own queue applies.
     */
    fun phoneQueue(shown: NowPlaying?, phone: PhoneNowPlaying?, state: WatchLibraryState): List<WatchEpisode>? =
        if (shown?.place != PlaybackPlace.Phone) null else phone?.upNext.orEmpty().map { state.merged(WatchLinkRules.watchEpisode(it)) }

    /**
     * What the watch goes by of the phone's last report. A phone out of reach may have stopped
     * since and cannot be told to, so its episode is held where it was when the phone was last
     * heard ([lostAt]; 0 when that moment was not seen) and Play carries it on here.
     */
    fun heard(phone: PhoneNowPlaying?, reachable: Boolean, lostAt: Long): PhoneNowPlaying? =
        if (phone == null || reachable || !phone.isPlaying) phone else phone.copy(isPlaying = false, positionMs = phone.positionAt(lostAt))

    /** Taking the phone's episode over needs only the watch; handing one to the phone needs the phone in reach. */
    fun canSwitch(onPhone: Boolean, phoneReachable: Boolean): Boolean = onPhone || phoneReachable

    /**
     * The player to show. A playing device always wins, the watch first. When both are still, it
     * is whichever the listener used last: [watchAt] is when the watch's episode was last played.
     * [known] is the watch's own copy of the phone's episode, when it has one: the phone may know
     * the episode under another ID, or not know its length yet.
     */
    fun shown(watch: NowPlaying?, watchAt: Long, phone: PhoneNowPlaying?, now: Long, known: WatchEpisode? = null): NowPlaying? {
        val episode = phone?.episode
        return when {
            watch?.isPlaying == true -> watch
            phone == null || episode == null -> watch
            phone.isPlaying || watch == null || phone.at > watchAt -> onPhone(phone, episode, now, known)
            else -> watch
        }
    }

    private fun onPhone(phone: PhoneNowPlaying, episode: WearLinkEpisode, now: Long, known: WatchEpisode?): NowPlaying {
        val duration = episode.durationMs.takeIf { it > 0L } ?: known?.durationMs?.coerceAtLeast(0L) ?: 0L
        val position = phone.positionAt(now).coerceIn(0L, duration.coerceAtLeast(0L))
        val left = (duration - position).coerceAtLeast(0L)
        return NowPlaying(
            episode = known ?: WatchLinkRules.watchEpisode(episode),
            isPlaying = phone.isPlaying,
            place = PlaybackPlace.Phone,
            progress = if (duration > 0L) position.toFloat() / duration else 0f,
            minutesLeft = if (left == 0L) 0 else ((left + MINUTE_MS - 1) / MINUTE_MS).toInt(),
            speed = phone.speed,
            upNextCount = phone.upNext.size,
        )
    }
}

/**
 * The watch's player with the phone's beside it. Home and the player screen show whichever is in
 * use; the same buttons then control that one. Moving playback carries the position across.
 */
class LinkedWatchPlayback(
    private val watch: WatchPlayback,
    private val link: WatchLink,
    private val store: WatchLibraryStore,
    /** The player's own thread: what happens after a command is lost runs here, never on the link's. */
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : WatchPlayback by watch {
    /** When the phone went out of reach; 0 until that has been seen happen. */
    private var lostAt = 0L
    private var reached = false

    /** The phone's player as far as the watch can rely on it; see [PlaceRules.heard]. */
    private val phone: StateFlow<PhoneNowPlaying?> =
        combine(link.phoneNowPlaying, link.phoneReachable.onEach(::noteReach)) { phone, reachable -> PlaceRules.heard(phone, reachable, lostAt) }
            .stateIn(scope, SharingStarted.Eagerly, PlaceRules.heard(link.phoneNowPlaying.value, link.phoneReachable.value, lostAt = 0L))

    override val nowPlaying: StateFlow<NowPlaying?> =
        combine(watch.nowPlaying, phone, ticksWhilePlaying(phone)) { mine, phone, _ ->
            // The watch's own copy carries the ID its lists use, so the playing row is marked there too.
            val known = phone?.episode?.let { store.state.value.merged(WatchLinkRules.watchEpisode(it)) }
            PlaceRules.shown(mine, lastPlayedAt(mine), phone, clock(), known)
        }.stateIn(scope, SharingStarted.Eagerly, watch.nowPlaying.value)

    override val phoneUpNext: StateFlow<List<WatchEpisode>?> =
        combine(nowPlaying, phone, store.state, PlaceRules::phoneQueue).stateIn(scope, SharingStarted.Eagerly, null)

    override val hasDailyMix: Boolean get() = watch.hasDailyMix || store.state.value.dailyMix.isNotEmpty()

    override val canSwitchPlace: Boolean
        get() = nowPlaying.value != null && PlaceRules.canSwitch(onPhone, link.phoneReachable.value)

    private val onPhone: Boolean get() = nowPlaying.value?.place == PlaybackPlace.Phone

    override val phoneIsPlaying: Boolean
        get() = link.phoneReachable.value && link.phoneNowPlaying.value?.isPlaying == true

    override fun playOnPhone(episode: WatchEpisode) {
        val command =
            WearLinkCommand(
                action = WearLinkCommand.PLAY_EPISODE,
                positionMs = store.state.value.positions[episode.id] ?: 0L,
                episode = WatchLinkRules.episode(episode),
            )
        // The listener asked to hear it; a phone that never got the order must not mean silence.
        link.send(command) { scope.launch { play(episode.id) } }
    }

    /** A player order the phone sent: pause, or play this episode here with its place and queue. */
    fun fromPhone(bytes: ByteArray) {
        val command = WearLinkCodec.decode<WearLinkCommand>(bytes) ?: return
        scope.launch {
            when (command.action) {
                WearLinkCommand.PAUSE -> watch.pause()
                WearLinkCommand.PLAY_EPISODE -> command.episode?.let { takeFromPhone(it, command.positionMs ?: 0L, command.upNext) }
            }
        }
    }

    private fun takeFromPhone(episode: WearLinkEpisode, positionMs: Long, upNext: List<WearLinkEpisode>) {
        val now = clock()
        val mine = WatchLinkRules.watchEpisode(episode)
        store.update { state ->
            val playing = state.merged(mine)
            state.progress(playing, positionMs, episode.durationMs, now).takeOver(upNext.map { WatchLinkRules.watchEpisode(it) }, playingId = playing.id)
        }
        watch.play(store.state.value.merged(mine).id)
    }

    override fun play(episodeId: String) {
        // Starting something on the watch takes over from the phone, so the two never play at once.
        if (link.phoneNowPlaying.value?.isPlaying == true) link.send(WearLinkCommand(WearLinkCommand.PAUSE))
        watch.play(episodeId)
    }

    override fun playDailyMix() {
        val mix = store.state.value.dailyMix
        val first = mix.firstOrNull() ?: return watch.playDailyMix()
        // The rest of the mix waits in Up next, ahead of what was queued before.
        store.update { it.takeOver(mix.drop(1), playingId = first.id) }
        play(first.id)
    }

    override fun togglePlayPause() {
        if (!onPhone) return watch.togglePlayPause()
        val phonePlaying = phone.value?.isPlaying == true
        when (PlaceRules.onToggle(phonePlaying, link.phoneReachable.value)) {
            PhoneTap.PlayHere -> moveToWatch()
            // The watch may not know yet that the phone is gone; a Play that never arrived starts here instead.
            PhoneTap.TellPhone -> link.send(WearLinkCommand(WearLinkCommand.TOGGLE)) { if (!phonePlaying) scope.launch { moveToWatch() } }
        }
    }

    override fun seekBack() = if (onPhone) link.send(WearLinkCommand(WearLinkCommand.SEEK_BACK)) else watch.seekBack()

    override fun seekForward() = if (onPhone) link.send(WearLinkCommand(WearLinkCommand.SEEK_FORWARD)) else watch.seekForward()

    override fun setSpeed(speed: Float) =
        if (onPhone) link.send(WearLinkCommand(WearLinkCommand.SET_SPEED, speed = speed)) else watch.setSpeed(speed)

    override fun switchPlace() = if (onPhone) moveToWatch() else moveToPhone()

    /** Picks up on the watch where the phone is right now, with the phone's queue after it, and stops the phone. */
    private fun moveToWatch() {
        val phone = phone.value ?: return
        val episode = phone.episode ?: return
        val now = clock()
        val mine = WatchLinkRules.watchEpisode(episode)
        val queue = phone.upNext.map(WatchLinkRules::watchEpisode)
        store.update { state ->
            val playing = state.merged(mine)
            state.progress(playing, phone.positionAt(now), episode.durationMs, now).takeOver(queue, playingId = playing.id)
        }
        link.send(WearLinkCommand(WearLinkCommand.PAUSE))
        watch.play(store.state.value.merged(mine).id)
    }

    /** Starts the watch's episode on the phone at the watch's saved position, its queue after it, and stops the watch. */
    private fun moveToPhone() {
        val mine = watch.nowPlaying.value ?: return
        val wasPlaying = mine.isPlaying
        watch.pause()
        val state = store.state.value
        val episode = state.episodes[mine.episode.id] ?: mine.episode
        val command =
            WearLinkCommand(
                action = WearLinkCommand.PLAY_EPISODE,
                positionMs = state.positions[episode.id] ?: 0L,
                episode = WatchLinkRules.episode(episode),
                upNext = state.upNext.map(WatchLinkRules::episode),
            )
        val queued = state.upNext
        store.update { it.handOver() }
        // A phone that never got the episode must not leave the listener in silence, or without
        // the queue: the watch takes both back and plays on.
        link.send(command) {
            scope.launch {
                store.update { it.takeOver(queued) }
                if (wasPlaying && watch.nowPlaying.value?.isPlaying == false) watch.togglePlayPause()
            }
        }
    }

    /**
     * When the watch itself last played. The episode's own "last played" would not do: listening
     * merged from the phone moves it, and the watch would then look like the device used last.
     */
    private fun lastPlayedAt(mine: NowPlaying?): Long = if (mine == null) 0L else store.state.value.playedHereAt

    private fun noteReach(reachable: Boolean) {
        if (!reachable && reached) lostAt = clock()
        reached = reachable
    }
}

private const val REDRAW_MS = 15_000L

/** While the phone plays, its position moves on without news from it, so the card is redrawn. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun ticksWhilePlaying(phone: Flow<PhoneNowPlaying?>): Flow<Unit> =
    phone.flatMapLatest { playing ->
        if (playing?.isPlaying == true) {
            flow {
                while (true) {
                    emit(Unit)
                    delay(REDRAW_MS)
                }
            }
        } else {
            flowOf(Unit)
        }
    }
