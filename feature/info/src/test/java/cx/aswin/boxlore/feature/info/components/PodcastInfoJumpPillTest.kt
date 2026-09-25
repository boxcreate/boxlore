package cx.aswin.boxlore.feature.info.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastInfoJumpPillTest {
    @Test
    fun `resume prefix used when ongoing session`() {
        assertEquals("Resume: ", resolveJumpPillPrefix(isOngoing = true))
    }

    @Test
    fun `jump to prefix used when not ongoing session`() {
        assertEquals("Jump to: ", resolveJumpPillPrefix(isOngoing = false))
    }
}
