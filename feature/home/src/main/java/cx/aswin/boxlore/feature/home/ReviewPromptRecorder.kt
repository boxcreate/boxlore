package cx.aswin.boxlore.feature.home

import java.util.concurrent.atomic.AtomicReference

/** What a decided review prompt reports and uses up once the listener can see it. */
internal data class ReviewPromptRecord(
    val promptType: String,
    val source: String,
    val completedEpisodes: Int? = null,
    val npsScore: Int? = null,
)

/**
 * Holds a decided review prompt until its sheet is on screen. Home can decide one while another
 * screen is on top or the app is in the background; recording it then would use up the milestone
 * (and its cooldown) for a prompt nobody saw if boxlore closed before Home came back.
 */
internal class ReviewPromptRecorder {
    private val decided = AtomicReference<ReviewPromptRecord?>(null)

    fun decided(record: ReviewPromptRecord) = decided.set(record)

    /** The decided prompt, once: null when it was already recorded or nothing was decided. */
    fun displayed(): ReviewPromptRecord? = decided.getAndSet(null)
}
