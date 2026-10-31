package cx.aswin.boxlore.feature.history.story

import cx.aswin.boxlore.core.designsystem.echo.parseEchoCharacterManifest
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every Echo the story calls for ships in the design system's manifest, with an outline to place it by. */
class StoryEchoAssetsTest {
    private val spec = parseEchoCharacterManifest(File("../../core/designsystem/src/main/assets/echo-character/manifest.json").readText())

    @Test
    fun everyOutfitActsThenRestsOnItsOwnPose() {
        EchoOutfit.entries.forEach { outfit ->
            val sequence = spec.sequence(outfit.asset)
            assertTrue(sequence.steps.first().clip in spec.clips, "${outfit.asset} opens with a shipped clip")
            assertEquals(0L, sequence.steps.last().millis, "${outfit.asset} rests at the end")
            assertNotNull(spec.reaches[sequence.lastStill], "${outfit.asset} has an outline to place Echo by")
            assertNotNull(StoryEchoStage.square(spec, outfit.asset, StoryEchoBox(0f, 0f, 200f)))
            assertTrue(StoryLogic.tileSequence(outfit) in spec.sequences, "${outfit.asset} has a still for its tile")
        }
    }

    @Test
    fun theHopTheInsightsCardAndTheirOutlinesShip() {
        assertTrue(StoryEchoStage.HOP in spec.sequences)
        assertNotNull(StoryEchoStage.square(spec, StoryEchoStage.HOP, StoryEchoBox(0f, 0f, 200f)))
        assertTrue("story-entry" in spec.sequences)
        assertNotNull(spec.reaches["front-rest"])
    }
}
