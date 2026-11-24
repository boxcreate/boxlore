package cx.aswin.boxlore.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The show card's glitter keeps off its words, and a card always gets the same glitter. */
class EchoPrismFoilTest {
    private val stacked = PrismPlan(compact = false, width = 360, art = 200, titleSp = 22f, titleLines = 2, showPublisher = true, height = 420)
    private val compact = PrismPlan(compact = true, width = 400, art = 96, titleSp = 16f, titleLines = 2, showPublisher = false, height = 220)

    @Test
    fun `no glint lands on the name, byline or pitch`() {
        listOf(stacked, compact).forEach { plan ->
            val width = plan.width - 6f
            val height = plan.height - 6f
            val area = prismSparkleArea(plan, width, height, edge = 16f, tilt = 4f)
            // The words: under the art on a stacked card; beside it and under it on a compact one.
            val inside = { x: Float, y: Float -> x > 16f && x < width - 16f && y > 16f && y < height - 16f }
            val wordsTop = if (plan.compact) 16f + plan.art else 16f + 4f + plan.art
            (1L..50L).forEach { seed ->
                echoSparkles(seed, 60, area).forEach { sparkle ->
                    val x = sparkle.x * width
                    val y = sparkle.y * height
                    val onWords = inside(x, y) && (y > wordsTop || (plan.compact && x > 16f + plan.art))
                    assertTrue("seed $seed puts a glint on the words at ($x, $y)", !onWords)
                }
            }
        }
    }

    @Test
    fun `the same card gets the same glitter`() {
        val area = prismSparkleArea(stacked, 354f, 414f, 16f, 4f)
        assertEquals(echoSparkles(42L, 60, area), echoSparkles(42L, 60, area))
        assertTrue(echoSparkles(42L, 60, area).isNotEmpty())
    }
}
