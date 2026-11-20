package cx.aswin.boxlore.feature.player

import cx.aswin.boxlore.core.model.text.resolve
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The same-show banner's words as listeners read them in English, including plurals and lengths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SameShowContinuationCopyTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun bannerCopyReadsAsWholeSentences() {
        assertEquals("Add next 1 from this show", SameShowContinuationBannerDefaults.buttonText(1).resolve(context))
        assertEquals("Add next 4 from this show", SameShowContinuationBannerDefaults.buttonText(4).resolve(context))
        assertEquals("Continue Tech Talk?", SameShowContinuationBannerDefaults.titleText("Tech Talk").resolve(context))
        assertEquals("Continue this show?", SameShowContinuationBannerDefaults.titleText("").resolve(context))
        assertEquals(
            "We skipped newer episodes from this show as you played it from recommendations.",
            SameShowContinuationBannerDefaults.explanationText.resolve(context),
        )
        assertEquals("Preview 1 upcoming episode", SameShowContinuationBannerDefaults.previewToggleText(1, isExpanded = false).resolve(context))
        assertEquals("Preview 5 upcoming episodes", SameShowContinuationBannerDefaults.previewToggleText(5, isExpanded = false).resolve(context))
        assertEquals("Hide preview", SameShowContinuationBannerDefaults.previewToggleText(5, isExpanded = true).resolve(context))
    }

    @Test
    fun episodeLengthFollowsTheLanguageAndIsBlankWhenUnknown() {
        assertEquals("", SameShowContinuationBannerDefaults.formatDuration(0, Locale.ENGLISH))
        assertEquals("", SameShowContinuationBannerDefaults.formatDuration(-10, Locale.ENGLISH))
        assertEquals("45 min", SameShowContinuationBannerDefaults.formatDuration(2700, Locale.ENGLISH))
        assertEquals("1 hr, 15 min", SameShowContinuationBannerDefaults.formatDuration(4500, Locale.ENGLISH))
        assertEquals("1 Std., 15 Min.", SameShowContinuationBannerDefaults.formatDuration(4500, Locale.GERMAN))
    }
}
