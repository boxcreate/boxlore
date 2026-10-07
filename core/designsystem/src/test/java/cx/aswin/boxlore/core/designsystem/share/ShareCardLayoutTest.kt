package cx.aswin.boxlore.core.designsystem.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareCardLayoutTest {
    @Test
    fun brandingLabelRemainsReadableWhenSharedImageFitsAPhone() {
        val story = shareBrandingLayout(isStory = true)
        val message = shareBrandingLayout(isStory = false)

        assertTrue(story.labelSize * 360f / 1_080f >= 12f)
        assertTrue(message.labelSize * 360f / 1_200f >= 12f)
    }

    @Test
    fun storySignatureStaysClearOfContentAndBottomStoryControls() {
        val layout = shareBrandingLayout(isStory = true)
        val signatureHeight = 96f

        assertFalse(layout.inline)
        for (contentBottom in listOf(1_200f, 1_300f, 1_380f)) {
            val top = shareBrandingTop(true, contentBottom, signatureHeight)
            assertTrue(top >= contentBottom + layout.contentGap)
            assertTrue(top + signatureHeight <= 1_600f)
        }
    }

    @Test
    fun messageFooterKeepsItsBottomInsetForShortAndTwoLineTitles() {
        val layout = shareBrandingLayout(isStory = false)
        val footerHeight = 48f

        assertTrue(layout.inline)
        for (contentBottom in listOf(900f, 980f, 1_008f)) {
            val top = shareBrandingTop(false, contentBottom, footerHeight)
            assertTrue(top >= contentBottom + layout.contentGap)
            assertEquals(96f, 1_200f - top - footerHeight)
        }
    }
}
