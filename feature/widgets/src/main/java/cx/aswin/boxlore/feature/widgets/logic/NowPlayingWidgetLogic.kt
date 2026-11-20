package cx.aswin.boxlore.feature.widgets.logic

import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.widgets.NowPlayingWidgetSnapshot
import cx.aswin.boxlore.feature.widgets.R
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
        fallbackArtworkUrls = state.fallbackArtworkUrls,
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
        previous.podcastTitle != next.podcastTitle ||
        hasProgressChange(previous, next) ||
        previous.seekBackwardMs != next.seekBackwardMs ||
        previous.seekForwardMs != next.seekForwardMs

    /**
     * The position moves many times a second, but the ring and the time-left label only change now and
     * then. Repainting on those steps keeps a playing widget to about one update a minute.
     */
    private fun hasProgressChange(
        previous: NowPlayingWidgetSnapshot,
        next: NowPlayingWidgetSnapshot,
    ): Boolean = WidgetProgress.ringStep(previous.positionMs, previous.durationMs) !=
        WidgetProgress.ringStep(next.positionMs, next.durationMs) ||
        WidgetProgress.minutesLeft(previous.positionMs, previous.durationMs) !=
        WidgetProgress.minutesLeft(next.positionMs, next.durationMs)
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

/** What a screen reader hears for the whole widget: one whole sentence per case, so each language orders it. */
object WidgetSemantics {
    /** Indexed by [variant]: with show and playing, with show and paused, playing, paused. */
    private val WITHOUT_TIME = intArrayOf(
        R.string.widget_description_playing_from,
        R.string.widget_description_paused_from,
        R.string.widget_description_playing,
        R.string.widget_description_paused,
    )
    private val WITH_MINUTES_LEFT = intArrayOf(
        R.plurals.widget_description_playing_from_left,
        R.plurals.widget_description_paused_from_left,
        R.plurals.widget_description_playing_left,
        R.plurals.widget_description_paused_left,
    )

    fun contentDescription(snapshot: NowPlayingWidgetSnapshot): UiText {
        if (!snapshot.hasEpisode) return UiText.res(R.string.widget_description_empty)
        val minutes = WidgetProgress.minutesLeft(snapshot.positionMs, snapshot.durationMs)?.takeIf { it > 0 }
        val hasShow = snapshot.podcastTitle.isNotBlank()
        val variant = (if (hasShow) 0 else 2) + (if (snapshot.isPlaying) 0 else 1)
        val titles = if (hasShow) listOf(snapshot.episodeTitle, snapshot.podcastTitle) else listOf(snapshot.episodeTitle)
        return if (minutes == null) {
            UiText.Res(WITHOUT_TIME[variant], titles)
        } else {
            UiText.Plural(WITH_MINUTES_LEFT[variant], minutes, titles + minutes)
        }
    }
}
