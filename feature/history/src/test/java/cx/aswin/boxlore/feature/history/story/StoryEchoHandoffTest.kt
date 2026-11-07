package cx.aswin.boxlore.feature.history.story

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoryEchoHandoffTest {
    @Test
    fun theActStaysHiddenThroughTheHopThenFadesInOverTheLandedHop() {
        // Regression: the act took over in one frame at the hop's end, showing the hop's last frame at
        // the act's scale (Echo shrank) and fading it out over the act (a ghost).
        val end = StoryMotion.HOP_PHASE_MS
        assertEquals(0f, StoryEchoHandoff.actAlpha(0))
        assertEquals(0f, StoryEchoHandoff.actAlpha(end))
        assertEquals(0.5f, StoryEchoHandoff.actAlpha(end + StoryMotion.HANDOFF_MS / 2), 0.01f)
        assertEquals(1f, StoryEchoHandoff.actAlpha(end + StoryMotion.HANDOFF_MS))
        assertEquals(1f, StoryEchoHandoff.actAlpha(StoryMotion.CARD_MS))
        // The hop's Echo stays under it until the act has fully faded in, so nothing shows through.
        assertTrue(StoryEchoHandoff.hopShown(0))
        assertTrue(StoryEchoHandoff.hopShown(end + StoryMotion.HANDOFF_MS - 1))
        assertFalse(StoryEchoHandoff.hopShown(end + StoryMotion.HANDOFF_MS))
        // Echo's line still comes once he has landed and started his act.
        assertTrue(StoryMotion.ECHO_LINE_MS >= end + StoryMotion.HANDOFF_MS)
    }

    @Test
    fun barIconsReadOnEveryCardColour() {
        assertTrue(StoryWindowLogic.darkIcons(Color(0xFFF4EFFF)), "Dark icons on a light card")
        assertFalse(StoryWindowLogic.darkIcons(Color(0xFF3E3572)), "Light icons on a dark card")
        assertFalse(StoryWindowLogic.darkIcons(Color(0xFF4F45C8)), "Light icons on a vivid card")
    }
}
