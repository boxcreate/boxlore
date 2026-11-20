package cx.aswin.boxlore.fcm

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PushTextFallbacksTest {
    @Test
    fun anAnnouncementWithoutItsOwnTextFallsBackToTheAppsResources() {
        val fallbacks = PushTextFallbacks.from(ApplicationProvider.getApplicationContext())
        assertEquals(PushTextFallbacks("boxlore Update", "Check out what's new in boxlore!", "View"), fallbacks)

        val parsed = FcmPayloadParser.parse(emptyMap(), fallbacks)
        assertEquals("boxlore Update", parsed.title)
        assertEquals("Check out what's new in boxlore!", parsed.body)
        assertEquals("View", parsed.actionLabel)
    }
}
