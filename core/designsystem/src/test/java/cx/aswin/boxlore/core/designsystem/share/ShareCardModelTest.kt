package cx.aswin.boxlore.core.designsystem.share

import cx.aswin.boxlore.core.model.ShareTarget
import kotlin.math.pow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareCardModelTest {
    private fun channel(c: Int): Double = (c / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    private fun luminance(argb: Int) =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)
    private fun contrast(a: Int, b: Int): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    // Purple, red, crimson, yellow, green, teal, near-grey, plus no seed (black-and-white covers).
    private val seeds = listOf(0xFF7939F9.toInt(), 0xFFFF3003.toInt(), 0xFF941F4A.toInt(), 0xFFE8C21A.toInt(), 0xFF2E9E4F.toInt(), 0xFF00A1A5.toInt(), 0xFF8A8C90.toInt(), null)

    @Test
    fun everyColourPairReadsForAnyArtwork() {
        for (seed in seeds) {
            val c = shareCardColors(seed)
            assertTrue(contrast(c.title, c.card) >= 7.0, "title on card, seed $seed")
            assertTrue(contrast(c.subtitle, c.card) >= 4.5, "name on card, seed $seed")
            assertTrue(contrast(c.accent, c.card) >= 4.5, "start time on card, seed $seed")
            assertTrue(contrast(c.onAccent, c.accent) >= 4.5, "boxlore bar and play icon, seed $seed")
            assertTrue(contrast(c.accent, c.ground) >= 3.0, "play button and bar stand off the ground, seed $seed")
            assertTrue(luminance(c.card) > luminance(c.ground), "card sits above the ground, seed $seed")
        }
    }

    @Test
    fun coversWithoutColourUseBoxloreIndigo() {
        assertEquals(shareCardColors(ShareCardFallbackSeed), shareCardColors(null))
    }

    @Test
    fun cardsKeepEverythingInsideTheImage() {
        for (target in ShareTarget.entries) {
            val l = shareCardLayout(target)
            val boxes = listOf(l.card, l.artwork, l.text, l.slider, l.play, l.bar)
            boxes.forEach {
                assertTrue(it.left >= 0f && it.top >= 0f && it.right <= l.width && it.bottom <= l.height, "$target $it")
            }
            assertTrue(l.artwork.right <= l.card.right && l.artwork.bottom <= l.card.bottom)
            assertTrue(l.slider.right < l.play.left, "slider stops before the play button, $target")
            assertTrue(l.play.bottom <= l.card.bottom && l.card.bottom <= l.bar.top, "bar sits under the card, $target")
            if (l.textBeside) {
                assertTrue(l.artwork.right < l.text.left)
            } else {
                assertTrue(l.artwork.bottom < l.text.top && l.text.bottom <= l.play.top)
            }
        }
    }

    @Test
    fun storyStaysClearOfInstagramsBottomControls() {
        val story = shareCardLayout(ShareTarget.INSTAGRAM_STORY)
        assertTrue(story.bar.bottom <= StorySafeBottom)
        assertTrue(story.card.top >= 120f, "clear of the story's top bar")
    }

    @Test
    fun textStaysReadableWhenTheCardFitsAPhoneScreen() {
        for (target in ShareTarget.entries) {
            val l = shareCardLayout(target)
            val shrink = 360f / l.width
            assertTrue(l.titleSizes.last() * shrink >= 12f, "$target title")
            assertTrue(l.subtitleSize * shrink >= 9f && l.labelSize * shrink >= 12f, "$target name and label")
        }
    }

    @Test
    fun titlesStepDownUntilTheyFitThenStopAtTheSmallestSize() {
        val sizes = listOf(56f, 50f, 46f, 40f)
        assertEquals(56f, fitTitleSize(sizes) { true })
        assertEquals(46f, fitTitleSize(sizes) { it <= 46f })
        assertEquals(40f, fitTitleSize(sizes) { false })
    }

    @Test
    fun sharedMomentFollowsTheLink() {
        assertEquals(ShareMoment.Whole, shareMoment(opensShow = true, timestampMs = 60_000, startMs = null, endMs = null))
        assertEquals(ShareMoment.StartsAt(60_000), shareMoment(false, 60_000, null, null))
        assertEquals(ShareMoment.Clip(1_000, 9_000), shareMoment(false, 60_000, 1_000, 9_000))
        assertEquals(ShareMoment.Whole, shareMoment(false, 0, null, null))
        assertEquals(ShareMoment.Whole, shareMoment(false, null, 9_000, 1_000))
    }

    @Test
    fun sliderShowsTheSharedPositionWhenTheLengthIsKnown() {
        assertEquals(0.5f, shareProgress(ShareMoment.StartsAt(30 * 60_000L), durationSeconds = 3_600), 0.001f)
        assertEquals(0.25f, shareProgress(ShareMoment.Clip(15 * 60_000L, 16 * 60_000L), 3_600), 0.001f)
        assertEquals(DefaultShareProgress, shareProgress(ShareMoment.StartsAt(1_000), durationSeconds = 0))
        assertEquals(DefaultShareProgress, shareProgress(ShareMoment.Whole, 3_600))
        assertEquals(0.94f, shareProgress(ShareMoment.StartsAt(3_600_000), 3_600), 0.001f)
    }

    @Test
    fun timesReadLikeThePlayer() {
        assertEquals("0:07", formatShareTime(7_000))
        assertEquals("12:34", formatShareTime(754_000))
        assertEquals("1:05:09", formatShareTime(3_909_000))
    }

    @Test
    fun messagesCarryTheNameTheMomentAndTheLink() {
        assertEquals("The Vergecast · The Verge\nhttps://boxlore.in/share?x", shareMessage("The Vergecast", "The Verge", null, "https://boxlore.in/share?x"))
        assertEquals("Octomom · Radiolab\nStarts at 12:34\nhttps://l", shareMessage("Octomom", "Radiolab", "Starts at 12:34", "https://l"))
        assertEquals("Octomom\nhttps://l", shareMessage("Octomom", " ", null, "https://l"))
    }

    @Test
    fun previewShrinksThenLeavesShortWindows() {
        assertEquals(240, sharePreviewSizeDp(760f))
        assertEquals(176, sharePreviewSizeDp(759.9f))
        assertEquals(176, sharePreviewSizeDp(600f))
        assertEquals(0, sharePreviewSizeDp(599.9f))
        assertEquals(0, sharePreviewSizeDp(0f))
    }
}
