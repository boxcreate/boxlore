package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.feature.info.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastInfoJumpPillTest {
    @Test
    fun `resume prefix used when ongoing session`() {
        assertEquals(R.string.podcast_info_jump_resume, resolveJumpPillPrefix(isOngoing = true))
    }

    @Test
    fun `jump to prefix used when not ongoing session`() {
        assertEquals(R.string.podcast_info_jump_to, resolveJumpPillPrefix(isOngoing = false))
    }
}
