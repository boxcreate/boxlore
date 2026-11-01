package cx.aswin.boxlore.feature.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReviewPromptRecorderTest {
    @Test
    fun `a decided prompt is recorded once, when it shows`() {
        // Regression: the milestone was used up when the prompt was decided, even if boxlore closed
        // before Home (and the sheet) came back.
        val recorder = ReviewPromptRecorder()
        val record = ReviewPromptRecord(promptType = "milestone_review", source = "episode_milestone", completedEpisodes = 5)

        recorder.decided(record)

        assertEquals(record, recorder.displayed())
        assertNull(recorder.displayed())
    }

    @Test
    fun `a prompt shown without a decision records nothing`() {
        assertNull(ReviewPromptRecorder().displayed())
    }
}
