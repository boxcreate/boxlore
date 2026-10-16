package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoFloorLine
import cx.aswin.boxlore.core.designsystem.echo.at
import cx.aswin.boxlore.core.designsystem.echo.echoFaceStep
import cx.aswin.boxlore.core.designsystem.echo.echoGrid
import cx.aswin.boxlore.core.designsystem.echo.echoIdlePoseAt
import cx.aswin.boxlore.core.designsystem.echo.parseEchoCharacterManifest
import java.io.File
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoMomentsTest {
    private val spec = parseEchoCharacterManifest(File("../../core/designsystem/src/main/assets/echo-character/manifest.json").readText())
    private val idles = EchoOnboardingPhase.entries.associateWith { echoOnboardingMoment(it).idle }

    @Test fun `every onboarding moment plays shipped clips and comes back to rest`() {
        EchoOnboardingPhase.entries.forEach { phase ->
            val sequence = spec.sequence(echoOnboardingMoment(phase).sequence)
            val steps = sequence.steps + sequence.loop
            assertTrue(steps.all { step -> step.clip == null || step.clip in spec.clips }, "$phase uses an unknown clip")
            assertTrue(steps.filter { it.clip == null }.all { it.frame in spec.screens }, "$phase rests on an unknown still")
            // Stills between clips hold long enough for the eyes to do something, and never so long Echo goes quiet.
            assertTrue(sequence.loop.filter { it.clip == null }.all { it.millis in 600..4_000 }, "$phase rest holds")
            if (phase != EchoOnboardingPhase.Rest) assertTrue(steps.any { it.clip != null }, "$phase moves")
        }
        assertEquals(EchoOnboardingPhase.entries.map { echoOnboardingMoment(it).sequence }.toSet(), EchoOnboardingSequences.toSet())
    }

    @Test fun `everything Echo does in the conversation stays inside its figure, so the layout makes room for it`() {
        val g = EchoFigureGeometry
        fun figureY(frameY: Float) = (g.EchoTop + g.boxY(frameY) * g.HeightShare) / g.TotalShare
        EchoOnboardingPhase.entries.forEach { phase ->
            val reach = requireNotNull(spec.reach(echoOnboardingMoment(phase).sequence)) { "$phase has a still or clip without a reach" }
            val top = figureY(reach.top)
            val bottom = figureY(reach.bottom)
            val left = g.boxX(reach.left)
            val right = g.boxX(reach.right)
            assertTrue(top >= 0f && bottom <= 1f && left >= 0f && right <= 1f, "$phase reaches $left, $top to $right, $bottom of its figure")
            // Echo fills its figure, and its rest pose is the one the geometry assumes.
            val rest = spec.reaches.getValue(spec.sequence(echoOnboardingMoment(phase).sequence).lastStill)
            assertEquals(g.RestTop, rest.top, 0.01f, "$phase rest pose's head")
            assertTrue((rest.bottom - rest.top) * g.FrameShare / g.TotalShare > 0.6f, "$phase Echo is small in its figure")
        }
    }

    @Test fun `the stage behind Echo is a bit bigger than Echo and stays inside the figure at any angle`() {
        val g = EchoFigureGeometry
        val shape = g.PlatformShare * EchoPlatformFill
        assertTrue(shape >= g.restHeight * 1.1f, "stage $shape against Echo ${g.restHeight}")
        // The widest stage shape, the clover, reaches 1.1x its size when turned.
        assertTrue(shape * 1.1f <= g.PlatformShare + 1e-4f && g.PlatformShare <= g.TotalShare + 1e-4f && g.PlatformShare <= 1f)
        // Centred on Echo at rest.
        val echoMiddle = g.EchoTop + g.boxY((g.RestTop + EchoFloorLine) / 2f) * g.HeightShare
        assertEquals(g.TotalShare / 2f, echoMiddle, 0.001f)
    }

    @Test fun `while Echo waits on an answer it keeps moving`() {
        listOf(EchoOnboardingPhase.Thinking, EchoOnboardingPhase.Preparing).forEach { phase ->
            val sequence = spec.sequence(echoOnboardingMoment(phase).sequence)
            assertTrue(sequence.loop.any { it.clip != null }, "$phase loops a clip")
            val stillest = (0 until 60_000 step 100).map { sequence.at(it.toLong()) }.zipWithNext().fold(0L to 0L) { (run, longest), (a, b) ->
                val next = if (a.step.clip == null && b.step.clip == null) run + 100 else 0L
                next to maxOf(longest, next)
            }.second
            assertTrue(stillest < 3_000, "$phase rests $stillest ms between moves")
        }
    }

    @Test fun `in every moment Echo's eyes stay busy, blink naturally and never skip a cell`() {
        val grid = echoGrid(200f, 176f)
        idles.forEach { (phase, idle) ->
            val poses = (0 until 60_000 step 8).map { echoIdlePoseAt(it.toLong(), idle) }
            var still = 0
            var longest = 0
            poses.zipWithNext().forEach { (a, b) ->
                still = if (a == b) still + 8 else 0
                longest = maxOf(longest, still)
            }
            assertTrue(longest < 3_500, "$phase still for $longest ms")
            val blinking = poses.count { it.face == EchoFace.Blink } / poses.size.toFloat()
            assertTrue(blinking in 0.02f..0.08f, "$phase blinking share was $blinking")
            assertTrue(poses.all { it.gaze.x in -1f..1f && it.gaze.y in -1f..1f }, "$phase looks off the screen")
            val steps = poses.map { echoFaceStep(it.gaze, grid) }
            assertTrue(steps.zipWithNext().all { (a, b) -> abs(a.x - b.x) <= 1 && abs(a.y - b.y) <= 1 }, "$phase eyes skip a cell")
        }
    }

    @Test fun `Echo reads the question below, and looks up as it ponders while thinking`() {
        val question = (0 until 3_000 step 8).map { echoIdlePoseAt(it.toLong(), idles.getValue(EchoOnboardingPhase.Question)) }
        assertTrue(question.take(150).all { it.gaze.y > 0.3f }, "reads the question first")
        assertTrue(question.map { it.gaze.x }.let { it.max() - it.min() } > 0.8f, "reads across the line")
        val pondering = (0 until 10_000 step 8).map { echoIdlePoseAt(it.toLong(), idles.getValue(EchoOnboardingPhase.Thinking)) }
        assertTrue(pondering.count { it.gaze.y < -0.3f } > pondering.size * 0.5f, "mostly looks up, thinking")
    }
}
