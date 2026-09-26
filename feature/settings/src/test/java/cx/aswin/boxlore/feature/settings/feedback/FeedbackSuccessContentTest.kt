package cx.aswin.boxlore.feature.settings.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackSuccessContentTest {

    @Test
    fun getFeedbackSuccessContent_bugWithEmail_includes24HrTatAndEmail() {
        val content = getFeedbackSuccessContent(FeedbackCategory.BUG, "listener@example.com")

        assertEquals("Bug report submitted", content.title)
        assertTrue(content.subtitle.contains("boxlore"))
        assertTrue(content.isEmailAttached)
        assertEquals("Reach out within 24 hours", content.statusTitle)
        assertTrue(content.statusDescription.contains("24 hours"))
        assertTrue(content.statusDescription.contains("listener@example.com"))
        assertTrue(content.statusDescription.contains("truly solved"))
    }

    @Test
    fun getFeedbackSuccessContent_bugWithoutEmail_showsAnonymousLogging() {
        val content = getFeedbackSuccessContent(FeedbackCategory.BUG, "   ")

        assertEquals("Bug report submitted", content.title)
        assertFalse(content.isEmailAttached)
        assertEquals("Submitted anonymously", content.statusTitle)
        assertTrue(content.statusDescription.contains("debugging queue"))
    }

    @Test
    fun getFeedbackSuccessContent_audioWithEmail_includesAudioSpecificCopyAndTat() {
        val content = getFeedbackSuccessContent(FeedbackCategory.AUDIO, "audiofan@example.com")

        assertEquals("Audio report submitted", content.title)
        assertTrue(content.subtitle.contains("Playback reliability"))
        assertTrue(content.isEmailAttached)
        assertEquals("Reach out within 24 hours", content.statusTitle)
        assertTrue(content.statusDescription.contains("24 hours"))
        assertTrue(content.statusDescription.contains("audiofan@example.com"))
        assertTrue(content.statusDescription.contains("playback diagnostics"))
    }

    @Test
    fun getFeedbackSuccessContent_audioWithoutEmail_showsAnonymousQueue() {
        val content = getFeedbackSuccessContent(FeedbackCategory.AUDIO, "")

        assertEquals("Audio report submitted", content.title)
        assertFalse(content.isEmailAttached)
        assertEquals("Submitted anonymously", content.statusTitle)
        assertTrue(content.statusDescription.contains("audio playback report"))
    }

    @Test
    fun getFeedbackSuccessContent_featureWithEmail_includesFeatureCopyAndTat() {
        val content = getFeedbackSuccessContent(FeedbackCategory.FEATURE, "builder@example.com")

        assertEquals("Feature idea submitted", content.title)
        assertTrue(content.subtitle.contains("boxlore"))
        assertTrue(content.isEmailAttached)
        assertEquals("Reach out within 24 hours", content.statusTitle)
        assertTrue(content.statusDescription.contains("24 hours"))
        assertTrue(content.statusDescription.contains("builder@example.com"))
    }

    @Test
    fun getFeedbackSuccessContent_featureWithoutEmail_showsAnonymousRoadmap() {
        val content = getFeedbackSuccessContent(FeedbackCategory.FEATURE, "")

        assertEquals("Feature idea submitted", content.title)
        assertFalse(content.isEmailAttached)
        assertEquals("Submitted anonymously", content.statusTitle)
        assertTrue(content.statusDescription.contains("product design roadmap"))
    }

    @Test
    fun getFeedbackSuccessContent_otherWithEmail_includesGeneralCopyAndTat() {
        val content = getFeedbackSuccessContent(FeedbackCategory.OTHER, "friend@example.com")

        assertEquals("Feedback received", content.title)
        assertTrue(content.subtitle.contains("boxlore"))
        assertTrue(content.isEmailAttached)
        assertEquals("Reach out within 24 hours", content.statusTitle)
        assertTrue(content.statusDescription.contains("24 hours"))
        assertTrue(content.statusDescription.contains("friend@example.com"))
    }

    @Test
    fun getFeedbackSuccessContent_otherWithoutEmail_showsAnonymousDelivery() {
        val content = getFeedbackSuccessContent(FeedbackCategory.OTHER, "")

        assertEquals("Feedback received", content.title)
        assertFalse(content.isEmailAttached)
        assertEquals("Submitted anonymously", content.statusTitle)
        assertTrue(content.statusDescription.contains("safely shared"))
    }
}
