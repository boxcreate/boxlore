package cx.aswin.boxlore.feature.history.story

import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.designsystem.echo.EchoAccents
import cx.aswin.boxlore.core.designsystem.echo.parseEchoCharacterManifest
import java.io.File
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
    fun theActGrowsInFromTheLandedHopsSizeSoEchoNeverJumps() {
        // Regression (recorded on a phone): the act's Echo stood about 8% taller than the landed hop's.
        val end = StoryMotion.HOP_PHASE_MS
        assertEquals(0.93f, StoryEchoHandoff.actScale(end, from = 0.93f), 1e-4f)
        assertEquals(1f, StoryEchoHandoff.actScale(end + StoryMotion.HANDOFF_GROW_MS, from = 0.93f), 1e-4f)
        val mid = StoryEchoHandoff.actScale(end + StoryMotion.HANDOFF_GROW_MS / 2, from = 0.93f)
        assertTrue(mid > 0.93f && mid < 1f)

        val spec = parseEchoCharacterManifest(File("../../core/designsystem/src/main/assets/echo-character/manifest.json").readText())
        val acts = EchoOutfit.entries.map { it.asset } + StoryEchoStage.FRONT
        acts.forEach { act ->
            val cast = StoryEchoCast(act, StoryEchoBox(134f, 540f, 120f), EchoAccents.Theme, EchoIdles.Genre)
            val scale = StoryEchoStage.handoffScale(spec, cast)
            assertTrue(scale in 0.85f..1.05f, "$act grows in from $scale of its size, about the landed hop's")
        }
    }

    @Test
    fun barIconsReadOnEveryCardColour() {
        assertTrue(StoryWindowLogic.darkIcons(Color(0xFFF4EFFF)), "Dark icons on a light card")
        assertFalse(StoryWindowLogic.darkIcons(Color(0xFF3E3572)), "Light icons on a dark card")
        assertFalse(StoryWindowLogic.darkIcons(Color(0xFF4F45C8)), "Light icons on a vivid card")
    }
}
