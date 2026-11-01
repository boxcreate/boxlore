package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.home.NpsScoreBand
import cx.aswin.boxlore.feature.home.NpsSurveyStep
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The survey sheet's static choices: Echo's part per step (all in Echo's manifest), its size, and the score circles. */
class NpsSurveySheetPolicyTest {
    private val manifest: JsonObject by lazy {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "core/designsystem/src/main/assets/echo-character/manifest.json").isFile }
        Json.parseToJsonElement(File(root, "core/designsystem/src/main/assets/echo-character/manifest.json").readText()).jsonObject
    }

    private fun clipsOf(sequence: String): List<String> {
        val steps = manifest.getValue("sequences").jsonObject.getValue(sequence).jsonObject.getValue("steps").jsonArray
        return steps.mapNotNull { it.jsonObject["clip"]?.jsonPrimitive?.content }
    }

    private val all = NpsSurveyStep.entries.flatMap { step -> (NpsScoreBand.entries + null).map { step to it } }

    @Test
    fun `every step's Echo sequence is in the manifest and uses existing clips`() {
        val clips = manifest.getValue("clips").jsonObject.keys
        all.forEach { (step, band) ->
            val sequence = npsEchoSequence(step, band)
            assertTrue(sequence in manifest.getValue("sequences").jsonObject.keys, "$step/$band: $sequence")
            clipsOf(sequence).forEach { assertTrue(it in clips, "$sequence: clip $it") }
        }
    }

    @Test
    fun `Echo acts out each step as approved, and only rests after a low score`() {
        assertEquals(listOf("ask"), clipsOf(npsEchoSequence(NpsSurveyStep.Score, null)))
        assertEquals(listOf("notebook"), clipsOf(npsEchoSequence(NpsSurveyStep.FollowUp, NpsScoreBand.Low)))
        assertEquals(listOf("think-chin"), clipsOf(npsEchoSequence(NpsSurveyStep.FollowUp, NpsScoreBand.Middle)))
        assertEquals(listOf("magnifier"), clipsOf(npsEchoSequence(NpsSurveyStep.FollowUp, NpsScoreBand.High)))
        assertEquals(emptyList<String>(), clipsOf(npsEchoSequence(NpsSurveyStep.Thanks, NpsScoreBand.Low)))
        assertEquals(listOf("thumbs-up"), clipsOf(npsEchoSequence(NpsSurveyStep.Thanks, NpsScoreBand.Middle)))
        assertEquals(listOf("story-celebrate"), clipsOf(npsEchoSequence(NpsSurveyStep.Thanks, NpsScoreBand.High)))
    }

    @Test
    fun `Echo shrinks while typing and on short windows`() {
        assertEquals(172.dp, npsEchoSize(typing = false, shortWindow = false))
        assertEquals(132.dp, npsEchoSize(typing = true, shortWindow = false))
        assertEquals(132.dp, npsEchoSize(typing = false, shortWindow = true))
        assertEquals(72.dp, npsEchoSize(typing = true, shortWindow = true))
    }

    @Test
    fun `score circles are 52dp where six fit, and shrink to fit narrow windows`() {
        assertEquals(52.dp, npsScoreButtonSize(342.dp))
        assertEquals(52.dp, npsScoreButtonSize(592.dp))
        assertEquals(47.dp, npsScoreButtonSize(312.dp))
        assertEquals(36.dp, npsScoreButtonSize(180.dp))
    }

    @Test
    fun `score bands follow PostHog's NPS split`() {
        assertEquals(List(7) { NpsScoreBand.Low } + List(2) { NpsScoreBand.Middle } + List(2) { NpsScoreBand.High }, (0..10).map(NpsScoreBand::of))
    }
}
