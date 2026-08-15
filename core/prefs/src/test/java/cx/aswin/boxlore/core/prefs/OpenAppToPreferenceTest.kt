package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAppToPreferenceTest {
    @Test
    fun sanitizeOpenAppTo_keepsSubscriptionsDownloadsAndDefaultsToHome() {
        assertEquals(OpenAppTo.HOME, sanitizeOpenAppTo(null))
        assertEquals(OpenAppTo.HOME, sanitizeOpenAppTo("unsupported"))
        assertEquals(OpenAppTo.HOME, sanitizeOpenAppTo("Home"))
        assertEquals(OpenAppTo.SUBSCRIPTIONS, sanitizeOpenAppTo(" subscriptions "))
        assertEquals(OpenAppTo.SUBSCRIPTIONS, sanitizeOpenAppTo("SUBSCRIPTIONS"))
        assertEquals(OpenAppTo.DOWNLOADS, sanitizeOpenAppTo(" downloads "))
        assertEquals(OpenAppTo.DOWNLOADS, sanitizeOpenAppTo("DOWNLOADS"))
    }
}
