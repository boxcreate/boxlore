package cx.aswin.boxlore.feature.briefing

internal sealed interface BriefingPlaybackAction {
    data class StartBriefing(
        val initialPositionMs: Long?,
    ) : BriefingPlaybackAction

    data object Pause : BriefingPlaybackAction

    data object Resume : BriefingPlaybackAction

    data class SeekToStory(
        val positionMs: Long,
        val resumeAfterSeek: Boolean,
    ) : BriefingPlaybackAction
}

internal fun resolveBriefingPlaybackAction(
    isCurrentBriefing: Boolean,
    isPlaying: Boolean,
    requestedPositionMs: Long?,
): BriefingPlaybackAction = when {
    !isCurrentBriefing -> BriefingPlaybackAction.StartBriefing(requestedPositionMs)
    requestedPositionMs != null ->
        BriefingPlaybackAction.SeekToStory(
            positionMs = requestedPositionMs,
            resumeAfterSeek = !isPlaying,
        )

    isPlaying -> BriefingPlaybackAction.Pause
    else -> BriefingPlaybackAction.Resume
}
