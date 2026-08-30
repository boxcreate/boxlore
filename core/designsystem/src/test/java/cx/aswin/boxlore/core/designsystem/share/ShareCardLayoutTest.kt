package cx.aswin.boxlore.core.designsystem.share

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareCardLayoutTest {
    @Test
    fun storyBrandingIsSeparatedAndKeepsListenPrompt() {
        val layout = shareBrandingLayout(isStory = true)

        assertTrue(layout.contentGap >= 110f)
        assertTrue(layout.labelSize < 34f)
        assertTrue(layout.logoWidth < 480)
        assertTrue(layout.showListenNow)
    }

    @Test
    fun messageBrandingIsCompactAndOmitsListenPrompt() {
        val layout = shareBrandingLayout(isStory = false)

        assertTrue(layout.contentGap >= 60f)
        assertTrue(layout.labelSize < 28f)
        assertTrue(layout.logoWidth < 400)
        assertFalse(layout.showListenNow)
    }
}
