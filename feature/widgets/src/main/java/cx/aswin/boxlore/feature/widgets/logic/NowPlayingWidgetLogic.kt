package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetSnapshot
import cx.aswin.boxlore.feature.widgets.WidgetControl
import cx.aswin.boxlore.feature.widgets.WidgetPlaybackState

object NowPlayingWidgetMapper {
    fun fromPlayback(
        state: WidgetPlaybackState,
        nowMs: Long = System.currentTimeMillis(),
        artworkCachePath: String? = null,
    ): NowPlayingWidgetSnapshot = NowPlayingWidgetSnapshot(
        episodeId = state.episodeId,
        episodeTitle = state.episodeTitle.orEmpty(),
        podcastTitle = state.podcastTitle.orEmpty(),
        artworkUrl = state.artworkUrl,
        artworkCachePath = artworkCachePath,
        isPlaying = state.isPlaying,
        isLoading = state.isLoading,
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        seekForwardMs = state.seekForwardMs,
        seekBackwardMs = state.seekBackwardMs,
        updatedAtMs = nowMs,
    )
}

object WidgetUpdatePolicy {
    fun shouldRender(
        previous: NowPlayingWidgetSnapshot?,
        next: NowPlayingWidgetSnapshot,
    ): Boolean = previous == null || hasPresentationChange(previous, next)

    private fun hasPresentationChange(
        previous: NowPlayingWidgetSnapshot,
        next: NowPlayingWidgetSnapshot,
    ): Boolean = previous.episodeId != next.episodeId ||
        previous.isPlaying != next.isPlaying ||
        previous.isLoading != next.isLoading ||
        previous.artworkCachePath != next.artworkCachePath ||
        previous.episodeTitle != next.episodeTitle ||
        previous.podcastTitle != next.podcastTitle
}

object WidgetOptimisticAction {
    fun apply(
        snapshot: NowPlayingWidgetSnapshot,
        control: WidgetControl,
        nowMs: Long = System.currentTimeMillis(),
    ): NowPlayingWidgetSnapshot = when (control) {
        WidgetControl.TOGGLE ->
            snapshot.copy(
                isPlaying = !snapshot.isPlaying,
                updatedAtMs = nowMs,
            )
        WidgetControl.SKIP_BACK ->
            snapshot.copy(
                positionMs = (snapshot.positionMs - snapshot.seekBackwardMs).coerceAtLeast(0L),
                updatedAtMs = nowMs,
            )
        WidgetControl.SKIP_FORWARD -> {
            val maxPosition =
                if (snapshot.durationMs > 0L) snapshot.durationMs else Long.MAX_VALUE
            snapshot.copy(
                positionMs = (snapshot.positionMs + snapshot.seekForwardMs).coerceAtMost(maxPosition),
                updatedAtMs = nowMs,
            )
        }
        WidgetControl.PREVIOUS,
        WidgetControl.NEXT,
        WidgetControl.OPEN_APP,
        -> snapshot
    }
}

object WidgetSemantics {
    fun contentDescription(snapshot: NowPlayingWidgetSnapshot): String = if (!snapshot.hasEpisode) {
        "boxlore now playing widget, no episode"
    } else {
        buildString {
            append(snapshot.episodeTitle)
            if (snapshot.podcastTitle.isNotBlank()) {
                append(" from ")
                append(snapshot.podcastTitle)
            }
            append(if (snapshot.isPlaying) ", playing" else ", paused")
        }
    }
}
