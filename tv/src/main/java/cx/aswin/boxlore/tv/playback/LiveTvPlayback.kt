package cx.aswin.boxlore.tv.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cx.aswin.boxlore.tv.data.LiveTvLibrary
import cx.aswin.boxlore.tv.data.TvLibraryStore
import cx.aswin.boxlore.tv.data.TvPlayback
import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Playback on the TV, through [TvPlaybackService]. Everything here runs on the main thread: [scope]
 * must be a main-thread scope, as the media controller requires.
 *
 * It connects to the service on first use, keeps the listener's place in [store], and moves on to
 * the next episode in Up next when one ends.
 */
class LiveTvPlayback(
    private val context: Context,
    private val library: LiveTvLibrary,
    private val store: TvLibraryStore,
    private val scope: CoroutineScope,
) : TvPlayback {
    private val state = MutableStateFlow(restingState(store))
    private var controller: MediaController? = null
    private var connecting = false

    // Everything asked for while the player connects, in order: a second press must not replace the first.
    private val whenConnected = ArrayDeque<(MediaController) -> Unit>()
    private var current: TvEpisode? = state.value?.episode

    /** The episode in the player itself; null while Resume only shows where the listener left off. Read by the cloud sync off the main thread. */
    @Volatile
    var loadedEpisodeId: String? = null
        private set
    private var sleepTimer = SleepTimerChoice.Off
    private var sleepJob: Job? = null
    private var ticker: Job? = null

    override val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()

    init {
        // While nothing is loaded, Resume follows the newest episode listened to, here or (after a sync) elsewhere.
        scope.launch {
            store.state.map { it.jumpBackIn.firstOrNull()?.id }.distinctUntilChanged().collect {
                if (loadedEpisodeId == null) {
                    state.value = restingState(store)
                    current = state.value?.episode
                }
            }
        }
    }

    override fun play(episodeId: String) {
        val episode = library.known(episodeId) ?: return
        if (episode.audioUrl == null) return
        withController { player ->
            if (PlaybackRules.savesPlaceBeforeStart(loadedEpisodeId, episode.id, player.isPlaying)) saveProgress(player)
            store.update { it.dequeue(episode.id) }
            current = episode
            loadedEpisodeId = episode.id
            player.setMediaItem(mediaItem(episode), store.state.value.positions[episode.id] ?: 0L)
            player.prepare()
            player.play()
            publish(player)
        }
    }

    /** Plays Daily Mix from its first episode; the rest wait in Up next, in the mix's order. */
    override fun playDailyMix() {
        val mix = store.state.value.dailyMix
        val first = mix.firstOrNull() ?: return
        store.update { saved -> mix.drop(1).fold(saved) { next, episode -> next.addToUpNext(episode) } }
        play(first.id)
    }

    override fun togglePlayPause() {
        val episode = current ?: return
        withController { player ->
            when {
                player.mediaItemCount == 0 -> play(episode.id)
                player.isPlaying || player.playWhenReady -> player.pause()
                else -> player.play()
            }
        }
    }

    override fun seekBack() = withController { it.seekBack() }

    override fun seekForward() = withController { it.seekForward() }

    override fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    override fun setSleepTimer(choice: SleepTimerChoice) {
        sleepTimer = choice
        sleepJob?.cancel()
        sleepJob =
            PlaybackRules.sleepDelayMs(choice)?.let { delayMs ->
                scope.launch {
                    delay(delayMs)
                    sleepTimer = SleepTimerChoice.Off
                    controller?.pause()
                }
            }
        controller?.let(::publish) ?: run { state.value = state.value?.copy(sleepTimer = choice) }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val ready = controller
        if (ready != null) {
            action(ready)
            return
        }
        whenConnected.addLast(action)
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, TvPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    // No player: what was asked for is dropped, and the next press connects afresh.
                    whenConnected.clear()
                    return@addListener
                }
                controller = connected
                connected.addListener(PlayerEvents(connected))
                while (whenConnected.isNotEmpty()) whenConnected.removeFirst().invoke(connected)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private inner class PlayerEvents(private val player: MediaController) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker(player) else stopTicker(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) onEnded(player) else publish(player)
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = publish(player)

        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (PlaybackRules.savesPlaceOnJump(reason == Player.DISCONTINUITY_REASON_SEEK, player.isPlaying)) saveProgress(player)
            publish(player)
        }
    }

    private fun startTicker(player: MediaController) {
        ticker?.cancel()
        ticker =
            scope.launch {
                var sinceSave = 0L
                while (isActive) {
                    publish(player)
                    delay(TICK_MS)
                    sinceSave += TICK_MS
                    if (sinceSave >= SAVE_EVERY_MS) {
                        saveProgress(player)
                        sinceSave = 0L
                    }
                }
            }
    }

    private fun stopTicker(player: MediaController) {
        ticker?.cancel()
        ticker = null
        saveProgress(player)
        publish(player)
    }

    private fun onEnded(player: MediaController) {
        val finished = current ?: return
        store.update { it.markPlayed(finished, System.currentTimeMillis()) }
        val continues = PlaybackRules.continuesAfterEnd(sleepTimer)
        sleepTimer = SleepTimerChoice.Off
        // Read and taken in one step, so an Up next changed meanwhile cannot hand over the wrong episode.
        var next: TvEpisode? = null
        if (continues) {
            store.update { saved ->
                val (episode, rest) = saved.takeNext()
                next = episode
                rest
            }
        }
        val following = next
        if (following != null) play(following.id) else publish(player)
    }

    private fun saveProgress(player: MediaController) {
        val episode = current ?: return
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) return
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        store.update { it.progress(episode, player.currentPosition, duration, System.currentTimeMillis()) }
    }

    private fun publish(player: MediaController) {
        val episode = current ?: return
        if (player.mediaItemCount == 0) return
        val snapshot =
            PlayerSnapshot(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it > 0 } ?: 0L,
                isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
                speed = player.playbackParameters.speed,
            )
        state.value = PlaybackRules.nowPlaying(store.state.value.shown(episode), snapshot, sleepTimer)
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val SAVE_EVERY_MS = 10_000L
    }
}

/** Before anything is loaded, Resume shows the episode the listener last stopped in, paused. */
private fun restingState(store: TvLibraryStore): NowPlaying? {
    val saved = store.state.value
    val episode = saved.jumpBackIn.firstOrNull() ?: return null
    return PlaybackRules.resting(episode, saved.positions[episode.id] ?: 0L)
}

private fun mediaItem(episode: TvEpisode): MediaItem {
    val metadata =
        MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.showTitle)
            .setArtworkUri(episode.artworkUrl?.let(Uri::parse))
            .build()
    return MediaItem.Builder().setMediaId(episode.id).setUri(episode.audioUrl?.let(Uri::parse) ?: Uri.EMPTY).setMediaMetadata(metadata).build()
}
