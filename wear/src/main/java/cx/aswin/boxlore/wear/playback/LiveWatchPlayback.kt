package cx.aswin.boxlore.wear.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cx.aswin.boxlore.wear.data.LiveWatchLibrary
import cx.aswin.boxlore.wear.data.WatchDownloads
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.data.WatchPlayback
import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.model.WatchEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Playback on the watch, through [WatchPlaybackService]. Everything here runs on the main thread:
 * [scope] must be a main-thread scope, as the media controller requires.
 *
 * It connects to the service on first use, keeps the listener's place in [WatchLibraryStore], and
 * moves on to the next queued episode when one ends.
 */
class LiveWatchPlayback(
    private val context: Context,
    private val library: LiveWatchLibrary,
    private val store: WatchLibraryStore,
    private val downloads: WatchDownloads,
    private val scope: CoroutineScope,
) : WatchPlayback {
    private val state = MutableStateFlow(restingState(store))
    private var controller: MediaController? = null
    private var connecting = false

    // Everything asked for while the player connects, in order: a second tap must not replace the first.
    private val whenConnected = ArrayDeque<(MediaController) -> Unit>()
    private var current: WatchEpisode? = state.value?.episode
    private var sleepTimer = SleepTimerChoice.Off
    private var sleepJob: Job? = null
    private var ticker: Job? = null

    override val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()

    /** The episode in the player itself; null while Home only shows where the listener left off. */
    @Volatile
    var loadedEpisodeId: String? = null
        private set
    override val hasDailyMix: Boolean = false
    override val canSwitchPlace: Boolean = false

    override fun play(episodeId: String) {
        val episode = library.known(episodeId) ?: return
        if (episode.audioUrl == null && downloads.file(episodeId) == null) return
        withController { player ->
            saveProgress(player)
            store.update { it.dequeue(episode.id) }
            current = episode
            loadedEpisodeId = episode.id
            player.setMediaItem(mediaItem(episode, downloads), store.state.value.positions[episode.id] ?: 0L)
            player.prepare()
            player.play()
            publish(player)
        }
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

    override fun pause() = withController { it.pause() }

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
        controller?.let(::publish)
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
        val token = SessionToken(context, ComponentName(context, WatchPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    // No player: what was asked for is dropped, and the next tap connects afresh.
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

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = publish(player)

        override fun onPlayerError(error: PlaybackException) = publish(player)

        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) = publish(player)
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
        // Played to the end: the stored audio has done its job.
        if (downloads.file(finished.id) != null) downloads.remove(finished.id)
        val continues = PlaybackRules.continuesAfterEnd(sleepTimer)
        sleepTimer = SleepTimerChoice.Off
        // Read and taken in one step, so a queue changed meanwhile cannot hand over the wrong episode.
        var next: WatchEpisode? = null
        if (continues) {
            store.update { state ->
                val (episode, rest) = state.takeNext()
                next = episode
                rest
            }
        }
        val following = next
        if (following != null) {
            play(following.id)
        } else {
            publish(player)
        }
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
                failed = player.playerError != null,
            )
        state.value = PlaybackRules.nowPlaying(episode, snapshot, sleepTimer, store.state.value.queue.size)
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val SAVE_EVERY_MS = 10_000L
    }
}

/** Before anything is loaded, the card shows the episode the listener last stopped in, paused. */
private fun restingState(store: WatchLibraryStore): NowPlaying? {
    val saved = store.state.value
    val episode = saved.jumpBackIn.firstOrNull() ?: return null
    return PlaybackRules.resting(episode, saved.positions[episode.id] ?: 0L, saved.queue.size)
}

/** Stored audio plays from the watch; anything else streams from the episode's own address. */
private fun mediaItem(episode: WatchEpisode, downloads: WatchDownloads): MediaItem {
    val uri = downloads.file(episode.id)?.let(Uri::fromFile) ?: episode.audioUrl?.let(Uri::parse) ?: Uri.EMPTY
    val metadata =
        MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.showTitle)
            .setArtworkUri(episode.artworkUrl?.let(Uri::parse))
            .build()
    return MediaItem.Builder().setMediaId(episode.id).setUri(uri).setMediaMetadata(metadata).build()
}
