package cx.aswin.boxlore.updates

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PlayStoreListingTest {
    @Test fun `links name the app's own package in the Play Store app and on the web`() {
        assertEquals("market://details?id=cx.aswin.boxlore", playStoreAppUri("cx.aswin.boxlore").toString())
        assertEquals("https://play.google.com/store/apps/details?id=cx.aswin.boxlore", playStoreWebUri("cx.aswin.boxlore").toString())
    }
}
