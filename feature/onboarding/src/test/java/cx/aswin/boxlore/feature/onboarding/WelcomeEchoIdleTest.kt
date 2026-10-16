package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.designsystem.echo.EchoCell
import cx.aswin.boxlore.core.designsystem.echo.EchoFace
import cx.aswin.boxlore.core.designsystem.echo.EchoFaceStep
import cx.aswin.boxlore.core.designsystem.echo.EchoGaze
import cx.aswin.boxlore.core.designsystem.echo.echoFaceEyes
import cx.aswin.boxlore.core.designsystem.echo.echoFaceStep
import cx.aswin.boxlore.core.designsystem.echo.echoGrid
import cx.aswin.boxlore.core.designsystem.echo.echoIdlePoseAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Welcome's idle script for Echo (WelcomeEchoIdle.kt): where Echo looks, how it arrives, how its eyes move. */
class WelcomeEchoIdleTest {
    private val grids = listOf(echoGrid(25f, 22f), echoGrid(90f, 71f), echoGrid(152f, 120f), echoGrid(200f, 158f), echoGrid(420f, 332f))

    @Test fun `on Welcome Echo watches the listener, reads the headline, checks the button and smiles, only ever looking left`() {
        val poses = (0 until 60_000 step 16).map { echoIdlePoseAt(it.toLong(), EchoWelcomeIdle) }
        assertTrue(poses.any { it.gaze.x <= -0.85f && it.gaze.y < -0.5f }, "reads the headline up and left")
        assertTrue(poses.any { it.gaze.y > 0.5f }, "checks the button below")
        assertTrue(poses.all { it.gaze.x in -1f..0f && it.gaze.y in -1f..1f }, "never looks right")
        assertTrue(poses.any { it.face == EchoFace.Happy } && poses.any { it.face == EchoFace.Curious })
    }

    @Test fun `Echo comes alive as it arrives and is never still for long`() {
        // It rises looking down at the button edge, then looks up at the listener with a blink and an eye smile.
        assertTrue(echoIdlePoseAt(0, EchoWelcomeIdle).gaze.y > 0.5f)
        val arrival = (0 until 1_500 step 8).map { echoIdlePoseAt(it.toLong(), EchoWelcomeIdle) }
        assertTrue(arrival.take(90).any { it.face == EchoFace.Blink }, "blinks as it arrives")
        assertTrue(arrival.any { it.face == EchoFace.Happy && it.gaze == EchoGaze(0f, 0f) }, "smiles at the listener")
        // Something changes (the eyes move, blink or change shape) at least every 3.5 seconds, from the start.
        val poses = (0 until 60_000 step 8).map { echoIdlePoseAt(it.toLong(), EchoWelcomeIdle) }
        var still = 0
        var longest = 0
        poses.zipWithNext().forEach { (a, b) ->
            still = if (a == b) still + 8 else 0
            longest = maxOf(longest, still)
        }
        assertTrue(longest < 3_500, "still for $longest ms")
    }

    @Test fun `Echo's eyes dart and hold like a person's, and blink as they move`() {
        val poses = (0 until 60_000 step 8).map { echoIdlePoseAt(it.toLong(), EchoWelcomeIdle) }
        // Quick moves, long holds: the eyes are still most of the time.
        val moving = poses.zipWithNext().count { (a, b) -> a.gaze != b.gaze } / poses.size.toFloat()
        assertTrue(moving < 0.12f, "eyes moving $moving of the time")
        val blinking = poses.count { it.face == EchoFace.Blink } / poses.size.toFloat()
        assertTrue(blinking in 0.02f..0.06f, "blinking share was $blinking")
        // At least one double blink: two blinks under half a second apart.
        val blinkStarts = poses.indices.filter { poses[it].face == EchoFace.Blink && (it == 0 || poses[it - 1].face != EchoFace.Blink) }.map { it * 8 }
        assertTrue(blinkStarts.zipWithNext().any { (a, b) -> b - a < 500 }, "double blink")
        // Most big eye moves come with a blink.
        val bigMoves = (EchoWelcomeIdle.loop + EchoWelcomeIdle.loop.first()).zipWithNext().filter { (a, b) -> kotlin.math.abs(a.gaze.x - b.gaze.x) + kotlin.math.abs(a.gaze.y - b.gaze.y) > 0.9f }
        assertTrue(bigMoves.count { (_, b) -> b.blinks > 0 } >= bigMoves.size * 3 / 4, "big moves without a blink")
    }

    @Test fun `the face moves one whole cell at a time and keeps its exact shape`() {
        grids.forEach { grid ->
            val idle = echoFaceEyes(EchoFace.Idle, EchoFaceStep(0, 0), grid).flatMap { it.cells }
            val moved = echoFaceEyes(EchoFace.Idle, EchoFaceStep(-3, -1), grid).flatMap { it.cells }
            assertEquals(idle.map { EchoCell(it.col - 3, it.row - 1) }, moved)
        }
        val grid = echoGrid(200f, 158f)
        val steps = (0 until 60_000 step 8).map { echoFaceStep(echoIdlePoseAt(it.toLong(), EchoWelcomeIdle).gaze, grid) }
        assertTrue(steps.zipWithNext().all { (a, b) -> kotlin.math.abs(a.x - b.x) <= 1 && kotlin.math.abs(a.y - b.y) <= 1 }, "the eyes skip a cell")
        assertTrue(steps.any { it.x <= -3 }, "glances reach the headline")
        assertTrue(steps.all { it.x <= 0 }, "never looks right")
    }
}
