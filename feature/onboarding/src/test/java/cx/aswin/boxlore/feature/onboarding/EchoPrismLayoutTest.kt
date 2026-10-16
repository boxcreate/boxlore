package cx.aswin.boxlore.feature.onboarding

import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPrismLayoutTest {
    /** A stand-in for the text measurer: fixed-width letters, so wrapping is predictable. */
    private object Measure : PrismTextMeasure {
        override fun title(text: String, sizeSp: Float, widthPx: Int): PrismTextBox = box(text, sizeSp * 0.8f, widthPx, sizeSp * 1.14f)

        override fun hook(text: String, sizeSp: Float, widthPx: Int): PrismTextBox = box(text, sizeSp * 0.5f, widthPx, sizeSp * 20f / 14f)

        private fun box(text: String, letter: Float, width: Int, line: Float): PrismTextBox {
            var lines = 1
            var used = 0f
            text.split(' ').forEach { word ->
                val w = word.length * letter
                if (used > 0f && used + letter + w > width) {
                    lines += 1
                    used = w
                } else {
                    used += (if (used > 0f) letter else 0f) + w
                }
            }
            return PrismTextBox(lines, ceil(lines * line).toInt())
        }
    }

    private val hook = "Bill Nye and visiting scientists answer listener questions, from black holes to why your cat ignores you."

    private fun plan(title: String, width: Int, height: Int, pitch: String = hook, publisher: Boolean = true) =
        EchoPrismLayout.plan(title.uppercase(), pitch, publisher, PrismRoom(width, height, density = 1f, fontScale = 1f), Measure)

    @Test fun `a short name on a phone is stacked, keeps its size and shows the publisher`() {
        val plan = plan("Serial", 321, 440)
        assertFalse(plan.compact)
        assertEquals(24f, plan.titleSp)
        assertTrue(plan.showPublisher)
        assertTrue("art ${plan.art}", plan.art >= EchoPrismLayout.MinStackedArt)
        assertTrue("height ${plan.height}", plan.height <= 440)
    }

    @Test fun `a long name steps down in size and drops the publisher before taking a third line`() {
        val plan = plan("Bloomberg Intelligence: Tech Disruptors", 321, 440)
        assertTrue(plan.titleSp < 24f)
        assertTrue(plan.titleLines <= 2 || plan.titleSp <= 18f)
        assertFalse(plan.showPublisher)
        assertTrue(plan.height <= 440)
    }

    @Test fun `an extreme name takes at most three lines at the smallest size`() {
        val plan = plan("The Dry Bar Comedy Podcast: Stand-Up Stories, Backstage Chats and Much More From the Road", 321, 480)
        assertEquals(3, plan.titleLines)
        assertFalse(plan.showPublisher)
    }

    @Test fun `the pitch is always measured whole and the art gives way instead`() {
        val long = List(5) { hook }.joinToString(" ")
        val short = plan("Serial", 321, 520)
        val tall = plan("Serial", 321, 520, pitch = long)
        assertTrue(tall.art < short.art)
        val inner = tall.width - ((EchoPrismLayout.Border + EchoPrismLayout.Padding) * 2).toInt()
        val pitchWidth = inner - ((EchoPrismLayout.HookPaddingH + EchoPrismLayout.HookBorder) * 2).toInt()
        val pitch = Measure.hook(long, EchoPrismLayout.HookSp, pitchWidth).height + ((EchoPrismLayout.HookPaddingV + EchoPrismLayout.HookBorder) * 2).toInt()
        assertTrue("the plan leaves room for the whole pitch", tall.height - tall.art >= pitch)
    }

    @Test fun `a short slot switches to the compact card, which fits`() {
        val plan = plan("Bloomberg Intelligence: Tech Disruptors", 312, 300)
        assertTrue(plan.compact)
        assertFalse(plan.showPublisher)
        assertTrue("height ${plan.height}", plan.height <= 300)
        assertTrue(plan.art in EchoPrismLayout.CompactMinArt.toInt()..EchoPrismLayout.CompactMaxArt.toInt())
    }

    @Test fun `on the tightest slot the pitch steps down, then the art goes, before anything is cut`() {
        val long = List(3) { hook }.joinToString(" ")
        val tight = plan("Bloomberg Intelligence: Tech Disruptors", 272, 260, pitch = long)
        assertTrue(tight.compact)
        assertTrue(tight.hookSp < EchoPrismLayout.HookSp)
        assertTrue(tight.hookSp >= 11f)
        val roomier = plan("Bloomberg Intelligence: Tech Disruptors", 272, 300, pitch = hook)
        assertEquals(EchoPrismLayout.HookSp, roomier.hookSp)
    }

    @Test fun `the card hugs its art and never grows past its widest`() {
        val roomy = plan("Good Hang with Amy Poehler", 625, 430)
        assertTrue(roomy.width <= EchoPrismLayout.MaxWidth)
        val side = (roomy.width - roomy.art) / 2f
        assertTrue("foil either side $side", side <= EchoPrismLayout.Border + EchoPrismLayout.Padding + EchoPrismLayout.SideClearance + EchoPrismLayout.SnugExtra / 2 + 1)
        assertTrue(roomy.width >= minOf(625f, EchoPrismLayout.MinWidth).toInt())
    }

    @Test fun `the clip button names a 45 second clip`() {
        assertEquals("0:45", EchoClipTiming.lengthLabel())
    }

    @Test fun `the sweep eases back and forth and loops`() {
        assertEquals(0f, prismSweep(0f), 0.001f)
        assertEquals(1f, prismSweep(5f), 0.001f)
        assertEquals(0f, prismSweep(10f), 0.001f)
        assertEquals(0.5f, prismSweep(2.5f), 0.001f)
    }
}
