package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.prefs.NpsSurveyRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The debug screen says plainly what the automatic survey still waits for. */
class NpsSurveyDebugTest {
    @Test
    fun `nothing missing reads as ready for the next finish`() {
        assertEquals(
            "Ready: the survey appears after the next episode you finish, once nothing is playing.",
            npsSurveyDebugStatus(emptyList(), finished = 12),
        )
    }

    @Test
    fun `each missing rule is named, with the finished count`() {
        assertEquals(
            "Waiting for: 3 episodes finished in boxlore (1 so far); 90 days since it last appeared.",
            npsSurveyDebugStatus(listOf(NpsSurveyRule.FinishedEpisodes, NpsSurveyRule.AskAgainGap), finished = 1),
        )
    }
}
