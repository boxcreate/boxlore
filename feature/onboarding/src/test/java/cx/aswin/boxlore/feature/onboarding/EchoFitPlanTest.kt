package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Echo's fit order, without Compose: today's tier layout whenever it fits, then shorter answers, a
 * smaller Echo and bubble, answers without icons, the answers stacked (beside Echo and the question on
 * wide, short windows), and last Echo folded away. The question always gets its height first, and no
 * window boxlore supports overflows. Text heights are fixed stand-ins in the shape of real text.
 */
class EchoFitPlanTest {
    /**
     * Stand-in text: the question wraps to three lines under 300dp and two above, the bubble needs
     * two lines under 400dp, and labels need [labelWidth] at 12sp (scaled by the text size).
     */
    private class FixedMeasure(
        private val fontScale: Float = 1f,
        private val labelWidth: Float = 60f,
        private val bubbleLines: (Float) -> Int = { width -> if (width < 400f) 2 else 1 },
    ) : EchoFitMeasure {
        override fun questionHeight(widthDp: Float, maxSp: Float, minSp: Float): Float = (if (widthDp < 300f) 3 else 2) * maxSp * 1.15f * fontScale

        override fun bubbleHeight(widthDp: Float, lines: Int): Float = 36f + minOf(lines, bubbleLines(widthDp)) * 24f * fontScale

        override fun labelsFit(widthDp: Float, maxLines: Int): Boolean = widthDp * maxLines >= labelWidth * fontScale

        override fun bubbleFits(widthDp: Float, lines: Int): Boolean = lines >= bubbleLines(widthDp)
    }

    private val topics = EchoAnswerShape(EchoAnswerKind.Topics, EchoInitialTopic.entries.size)
    private val rows = EchoAnswerShape(EchoAnswerKind.Rows, 4)

    /**
     * Echo's body in a [width]x[height] window: the screen's 24dp sides (within the 720dp utility
     * column), under the 56dp top bar and the status bar, above a one-button footer and the nav bar.
     */
    private fun plan(
        width: Int,
        height: Int,
        status: Int = 0,
        nav: Int = 0,
        fontScale: Float = 1f,
        answers: EchoAnswerShape = topics,
        measure: EchoFitMeasure = FixedMeasure(fontScale),
    ): EchoFit = echoFitPlan(body(width, height, status, nav), tier(height, status), fontScale, keyboardOpen = false, answers, measure)

    private fun body(width: Int, height: Int, status: Int, nav: Int) = DpSize((minOf(width, UtilityWidth) - Sides).dp, (height - status - TopBar - Footer - nav).dp)

    private fun tier(height: Int, status: Int) = (height - status - TopBar - FooterAllowance).dp

    @Test
    fun `today's layout is kept wherever it fits`() {
        listOf(plan(412, 915), plan(412, 915, answers = rows), plan(1280, 800), plan(800, 1280)).forEach { fit ->
            assertEquals(EchoFitLevel.Today, fit.level)
            assertTrue(fit.showIcons)
            assertFalse(fit.sideBySide || fit.somethingElseInFooter)
        }
        assertEquals(EchoLayout.metrics((915 - TopBar - FooterAllowance).dp, 1f, keyboardOpen = false), plan(412, 915).metrics)
    }

    @Test
    fun `wide windows set the topics four across, phones and the cover keep two`() {
        listOf(plan(800, 1280), plan(1280, 800), plan(1600, 1000), plan(750, 832), plan(841, 701)).forEach { fit ->
            assertEquals(4, fit.columns)
            assertTrue(fit.showIcons)
            assertFalse(fit.sideBySide)
        }
        listOf(plan(412, 915), plan(360, 800), plan(475, 695, 24, 24), plan(600, 960)).forEach { assertEquals(2, it.columns) }
    }

    @Test
    fun `a phone with its system bars shortens the topic tiles first and keeps Echo's size`() {
        val today = plan(412, 915)
        val fit = plan(412, 915, status = 24, nav = 24)

        assertEquals(EchoFitLevel.Shrunk, fit.level)
        assertEquals(80.dp, fit.metrics.tileHeight)
        assertEquals(today.metrics.avatarWidth, fit.metrics.avatarWidth)
        assertTrue(fit.showIcons)
    }

    @Test
    fun `each step is taken only when every earlier one overflows`() {
        // One tier, less and less room: the steps come in order and never go back.
        val tier = 731.dp
        val levels = (1000 downTo 200 step 10).map { height ->
            echoFitPlan(DpSize(364.dp, height.dp), tier, 1f, keyboardOpen = false, topics, FixedMeasure()).level
        }
        levels.zipWithNext().forEach { (roomier, tighter) -> assertTrue(tighter >= roomier, "$levels") }
        assertEquals(EchoFitLevel.Today, levels.first())
        assertEquals(listOf(EchoFitLevel.Today, EchoFitLevel.Shrunk, EchoFitLevel.Stacked, EchoFitLevel.LastResort), levels.distinct())
        // Within Shrunk, the tiles go before Echo.
        val shrunk = (1000 downTo 200 step 10).map { echoFitPlan(DpSize(364.dp, it.dp), tier, 1f, false, topics, FixedMeasure()) }
            .filter { it.level == EchoFitLevel.Shrunk }
        assertTrue(shrunk.first { it.metrics.avatarWidth < 168.dp }.metrics.tileHeight <= 50.dp)
    }

    @Test
    fun `a sideways phone puts Echo and the question beside the answers`() {
        val fit = plan(915 - 48 - 32, 412, status = 24)

        assertTrue(fit.sideBySide)
        assertTrue(fit.somethingElseInFooter)
        assertFalse(fit.showIcons)
        assertFalse(fit.overflow)
        assertTrue(plan(740, 360).sideBySide)
    }

    @Test
    fun `beside the answers Echo stays for a turn whose line runs long`() {
        // A body short enough for the answers to stand beside Echo, under a tier that shows Echo.
        fun beside(measure: EchoFitMeasure) = echoFitPlan(DpSize(800.dp, 330.dp), 700.dp, 1f, keyboardOpen = false, rows, measure)
        val short = beside(FixedMeasure())
        // A long line that needs more bubble than any size offers: Echo stays and its bubble shortens.
        val long = beside(FixedMeasure(bubbleLines = { 9 }))
        assertTrue(short.sideBySide && long.sideBySide)
        assertTrue(short.metrics.showEcho)
        assertTrue(long.metrics.showEcho, "Echo folded away for a long line")
        assertFalse(long.overflow)
    }

    @Test
    fun `split screen and tabletop halves fit`() {
        assertFalse(plan(412, 450).overflow)
        assertFalse(plan(412, 450).sideBySide)
        assertFalse(plan(475, 330).overflow)
        listOf(932 to 314, 841 to 340, 673 to 410).forEach { (w, h) -> assertFalse(plan(w, h).overflow, "${w}x$h") }
    }

    @Test
    fun `nothing overflows on any supported window`() {
        val bars = listOf(412 to 915, 360 to 800, 430 to 932, 384 to 854, 443 to 649, 475 to 695)
        val bare = listOf(915 to 412, 740 to 360, 695 to 475, 412 to 450, 475 to 330, 932 to 314, 841 to 340, 673 to 410) +
            listOf(750 to 832, 932 to 650, 841 to 701, 800 to 1280, 1280 to 800, 1600 to 1000)
        listOf(1f, 1.3f).forEach { scale ->
            listOf(topics, rows).forEach { answers ->
                bars.forEach { (w, h) -> assertFalse(plan(w, h, 24, 24, scale, answers).overflow, "${w}x$h bars at $scale") }
                bare.forEach { (w, h) -> assertFalse(plan(w, h, 0, 0, scale, answers).overflow, "${w}x$h at $scale") }
            }
        }
        listOf(412 to 915, 360 to 800).forEach { (w, h) -> assertFalse(plan(w, h, 24, 24, 2f).overflow, "${w}x$h at 2x") }
    }

    @Test
    fun `a label too long for its tile with an icon drops the icons before anything shrinks`() {
        // Too long beside an icon in a two-column tile, short enough without one.
        val germanTile = 80f
        val fit = plan(360, 800, fontScale = 1.3f, measure = FixedMeasure(1.3f, labelWidth = germanTile))

        assertEquals(EchoFitLevel.NoIcons, fit.level)
        assertFalse(fit.showIcons)
        assertEquals(plan(360, 800, fontScale = 1.3f).metrics, fit.metrics)
    }

    @Test
    fun `labels too long for two columns even without icons stand one to a row where that fits`() {
        listOf(1.3f to 360, 2f to 360, 2f to 412).forEach { (scale, width) ->
            val fit = plan(width, if (width == 360) 800 else 915, 24, 24, scale, measure = FixedMeasure(scale, labelWidth = 120f))
            assertEquals(1, fit.columns, "${width}dp at $scale")
            assertEquals(EchoFitLevel.Stacked, fit.level)
            assertFalse(fit.sideBySide || fit.showIcons || fit.overflow)
            assertTrue(fit.somethingElseInFooter)
        }
    }

    @Test
    fun `beside the answers every label fits, the start column narrowing first`() {
        listOf(475 to 330, 673 to 410, 740 to 360).forEach { (w, h) ->
            listOf(1f, 1.3f).forEach { scale ->
                val roomy = plan(w, h, fontScale = scale)
                // Labels a little too long for two columns in the end column's usual share.
                val end = (w - Sides - SideBySideGap) * (1 - SideBySideStartShare)
                val long = plan(w, h, fontScale = scale, measure = FixedMeasure(scale, labelWidth = (end / 2 - 32f + 10f) / scale))
                assertTrue(long.sideBySide, "${w}x$h at $scale")
                assertFalse(long.overflow)
                assertTrue(long.startShare < roomy.startShare || long.labelLines == 2 || long.columns != roomy.columns, "${w}x$h at $scale: $long")
            }
        }
    }

    @Test
    fun `Echo comes and goes with the window, never with what it says`() {
        // Windows whose tier hides Echo keep it hidden beside the answers.
        listOf(475 to 330, 932 to 314, 740 to 360, 673 to 410).forEach { (w, h) ->
            listOf(FixedMeasure(), FixedMeasure(bubbleLines = { 1 }), FixedMeasure(bubbleLines = { 6 })).forEach { measure ->
                assertFalse(plan(w, h, measure = measure).metrics.showEcho, "${w}x$h")
            }
        }
        // Beside the answers, Echo shows only with everything it says.
        val wordy = plan(841, 560, measure = FixedMeasure(bubbleLines = { 9 }))
        if (wordy.sideBySide) assertFalse(wordy.metrics.showEcho)
    }

    @Test
    fun `larger text keeps a third line for Echo where it still fits`() {
        val fit = plan(412, 915, 24, 24, 1.3f, measure = FixedMeasure(1.3f, bubbleLines = { 3 }))
        assertEquals(3, fit.metrics.bubbleLines)
        assertTrue(fit.level <= EchoFitLevel.Shrunk)
        // Default text keeps the tier's lines.
        assertEquals(EchoLayout.metrics((915 - 24 - TopBar - FooterAllowance).dp, 1f, false).bubbleLines, plan(412, 915, 24, 24).metrics.bubbleLines)
    }

    @Test
    fun `the question gets its whole height first`() {
        listOf(plan(412, 915, 24, 24), plan(915, 412, 24), plan(412, 450), plan(932, 314)).forEach { fit ->
            assertTrue(fit.questionHeight > 0.dp)
        }
        assertEquals(EchoFitLevel.LastResort, plan(320, 360).level)
        assertFalse(plan(320, 360).metrics.showEcho)
    }

    @Test
    fun `the keyboard folds Echo away but never changes the step`() {
        listOf(412 to 915, 915 to 412, 412 to 450).forEach { (w, h) ->
            val closed = plan(w, h, 24, 24)
            val open = echoFitPlan(body(w, h, 24, 24), tier(h, 24), 1f, keyboardOpen = true, topics, FixedMeasure())
            assertEquals(closed.copy(metrics = closed.metrics.copy(showEcho = false)), open)
        }
    }

    private companion object {
        const val TopBar = 56
        const val Footer = 72
        const val FooterAllowance = 128
        const val Sides = 48
        const val UtilityWidth = 720
    }
}
