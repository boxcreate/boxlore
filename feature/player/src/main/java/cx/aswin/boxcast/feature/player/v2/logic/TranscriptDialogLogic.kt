package cx.aswin.boxcast.feature.player.v2.logic

internal data class TranscriptDialogState(
    val estimatedTime: String,
    val remainingGenerations: Int?
) {
    val limitReached: Boolean get() = remainingGenerations == 0
    val canGenerate: Boolean get() = remainingGenerations == null || remainingGenerations > 0
    val supportingText: String
        get() = if (limitReached) {
            "Daily AI limit reached. Please try again tomorrow."
        } else {
            "AI transcription is in beta and may contain errors."
        }
}

internal fun estimateTranscriptTime(durationSeconds: Long): String = when {
    durationSeconds <= 0 -> "~1-2 min"
    durationSeconds < 600 -> "~30s"
    durationSeconds < 1800 -> "~1 min"
    durationSeconds < 3600 -> "~1-2 min"
    else -> "~2-3 min"
}
