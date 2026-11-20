package cx.aswin.boxlore.feature.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Marquee scrolls with Home as one piece: its words fade under the top bar, and the cover stays in view. */
class MarqueeScrollTest {
    @Test fun `words draw as they are until they reach the top bar`() {
        // A 100px bar; the words start 120px down the hero.
        assertNull("at rest", marqueeFadeUnderBar(barBottom = 100f, scroll = 0, top = 120f, fade = 50f))
        assertNull("still below the bar", marqueeFadeUnderBar(barBottom = 100f, scroll = 15, top = 120f, fade = 50f))
        assertNull("the hero has scrolled away", marqueeFadeUnderBar(barBottom = 100f, scroll = Int.MAX_VALUE, top = 120f, fade = 50f))
    }

    @Test fun `words fade over the stretch just above the bar's foot as they slide under it`() {
        // Scrolled 80px: the bar's foot is 60px into the words, so they are clear above 10px and whole below 60px.
        assertEquals(10f to 60f, marqueeFadeUnderBar(barBottom = 100f, scroll = 80, top = 120f, fade = 50f))
        // The band moves down the words at the scroll's pace, so the fade stays fixed under the bar on screen.
        assertEquals(30f to 80f, marqueeFadeUnderBar(barBottom = 100f, scroll = 100, top = 120f, fade = 50f))
    }

    @Test fun `the cover is sharp at the top and each blurrier copy eases in further down`() {
        val sharp = marqueeCoverMask(0)
        assertEquals("the sharp cover is whole from the top", 0f to 1f, sharp.first())
        val first = MarqueeCoverSoftening.first()
        assertEquals("it stays whole until the first blurred copy is", first.to to 1f, sharp[1])

        MarqueeCoverSoftening.forEachIndexed { index, softening ->
            val mask = marqueeCoverMask(index + 1)
            assertEquals("hidden at the top", 0f to 0f, mask.first())
            assertEquals(softening.from to 0f, mask[1])
            assertTrue("whole once eased in", mask.contains(softening.to to 1f))
        }
        // Positions only ever go down each mask, so the gradient stops are in order.
        (0..MarqueeCoverSoftening.size).forEach { layer ->
            val positions = marqueeCoverMask(layer).map { it.first }
            assertEquals(positions.sorted(), positions)
        }
    }

    @Test fun `a sharper copy is gone before the next one fades out, so no lettering ghosts at the foot`() {
        val layers = (0..MarqueeCoverSoftening.size).map { marqueeCoverMask(it) }
        layers.dropLast(1).forEachIndexed { index, mask ->
            val whole = mask.indexOfFirst { it.second == 1f }
            val gone = mask.drop(whole).first { it.second == 0f }.first
            assertTrue("layer $index hides before the cover's foot", gone < MarqueeCoverFoot)
        }
        val last = layers.last()
        assertTrue("only the blurriest copy eases into the wash", last.contains(MarqueeCoverFoot to 1f))
        assertEquals(1f to 0f, last.last())
    }

    @Test fun `the blur steps overlap, so the blur grows smoothly instead of in visible steps`() {
        MarqueeCoverSoftening.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue("the next step starts before this one is whole", heavier.from < lighter.to)
        }
    }

    @Test fun `the softening is light and grows down the cover, never starting at the top`() {
        val blurs = MarqueeCoverSoftening.map { it.blur.value }
        assertEquals(blurs.sorted(), blurs)
        assertTrue("never a heavy blur that hides the cover", blurs.all { it <= 12f })
        assertTrue("the top fifth of the cover stays sharp", MarqueeCoverSoftening.all { it.from >= 0.2f })
        assertTrue("every step is whole before the cover's foot", MarqueeCoverSoftening.all { it.to <= MarqueeCoverFoot })
    }

    @Test fun `every fade eases in and out, so neither end shows as a line`() {
        val fade = marqueeEasedFade(0f, 1f, 0f, 1f)
        assertEquals(0f to 0f, fade.first())
        assertEquals(1f to 1f, fade.last())
        val alphas = fade.map { it.second }
        assertEquals("never goes back", alphas.sorted(), alphas)
        val step = 1f / (fade.size - 1)
        assertTrue("starts gentler than a straight ramp", alphas[1] < step)
        assertTrue("settles gentler than a straight ramp", 1f - alphas[alphas.size - 2] < step)
    }
}
