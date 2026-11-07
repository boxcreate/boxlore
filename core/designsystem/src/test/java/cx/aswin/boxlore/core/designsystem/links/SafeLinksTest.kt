package cx.aswin.boxlore.core.designsystem.links

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * Regression: tapping a link on a phone where no app could open it (an Instagram link with no browser)
 * crashed the episode page with ActivityNotFoundException, and the same could happen from every other
 * link, store and settings button. These start the first intent the phone handles, or none, never throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SafeLinksTest {
    /** A phone whose apps open only what [opens] accepts. */
    private class Phone(private val opens: (Intent) -> Boolean) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            if (!opens(intent)) throw ActivityNotFoundException("No app for $intent")
            started += intent
        }
    }

    private val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=cx.aswin.boxlore"))
    private val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=cx.aswin.boxlore"))

    @Test
    fun theFirstIntentAnAppHandlesStarts() {
        val phone = Phone { it.data?.scheme == "https" }

        assertTrue(phone.startFirstAvailable(market, web))
        assertEquals(listOf("https"), phone.started.map { it.data?.scheme })
    }

    @Test
    fun noAppForAnyIntentReturnsFalseWithoutThrowing() {
        val phone = Phone { false }

        assertFalse(phone.startFirstAvailable(market, web))
        assertTrue(phone.started.isEmpty())
    }

    @Test
    fun outsideAnActivityTheLaunchStartsANewTask() {
        val phone = Phone { true }

        phone.startFirstAvailable(web)

        assertTrue(phone.started.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun aLinkNoAppCanOpenSaysSoInsteadOfCrashing() {
        val phone = Phone { false }

        SafeUriHandler(phone).openUri("https://www.instagram.com/serialpodcast")

        assertEquals("No app could open this link", ShadowToast.getTextOfLatestToast())
        assertFalse((phone as Context).openLink(Uri.parse("https://example.com")))
    }

    @Test
    fun aLinkAnAppCanOpenOpensIt() {
        val phone = Phone { true }

        assertTrue(phone.openLink(Uri.parse("https://example.com/notes")))
        assertEquals(Intent.ACTION_VIEW, phone.started.single().action)
    }
}
