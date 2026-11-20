package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

/** What a listener-started queue publishes and signals before the player reports back. */
internal object QueueStartState {
    /**
     * [state] showing [current] as playing at [startPosMs] in [queue], ahead of the MediaController
     * callback. A mixed list started under a placeholder (All Downloads) starts under its first episode's show.
     */
    @Suppress("LongParameterList")
    fun optimistic(
        state: PlayerState,
        current: Episode,
        podcast: Podcast,
        startPosMs: Long,
        queue: List<Episode>,
        isLiked: Boolean,
        controllerSpeed: Float,
    ): PlayerState = PlaybackControlSync.withSyncedPlaybackSpeed(
        state.copy(
            currentEpisode = current,
            currentPodcast = QueueShowPolicy.startShow(current, podcast),
            isPlaying = true,
            position = startPosMs,
            duration = current.duration.toLong() * 1000,
            queue = queue,
            isLiked = isLiked,
        ),
        controllerSpeed = controllerSpeed,
    )

    /** Labels how the start episode begins (a resume seek, or a pending start from zero) and records the start request. */
    fun markStartSignals(startEpisodeId: String?, startPosMs: Long, initialPositionMs: Long?) {
        if (startPosMs > 0L) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .setSeekSource("resume")
        } else if (startEpisodeId != null) {
            PlaybackLifecycleSignals.markPendingZeroStart(startEpisodeId)
        }
        if (startEpisodeId != null) {
            PlaybackActivationRequest.set(startEpisodeId, startPosMs, applyIntroTrim = initialPositionMs == null && startPosMs == 0L)
        }
    }

    /**
     * Loads [items] into [player] at [index] and [positionMs], runs [beforeStart], then plays, or
     * leaves them paused when [startPaused]: paused before the new items, so a playing queue doesn't
     * play them on prepare.
     */
    @Suppress("LongParameterList")
    fun load(player: Player, items: List<MediaItem>, index: Int, positionMs: Long, startPaused: Boolean, beforeStart: () -> Unit) {
        if (startPaused) player.pause()
        player.setMediaItems(items, index, positionMs)
        player.prepare()
        beforeStart()
        if (!startPaused) player.play()
    }
}
