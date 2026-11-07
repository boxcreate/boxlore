package cx.aswin.boxlore.feature.home

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The review prompt's Play Store page: the Play app, else the Play web page, else nothing; never a crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayStorePageLinkTest {
    /** A phone whose apps open only what [opens] accepts; it records what it was asked to open. */
    private class Phone(private val opens: (Intent) -> Boolean) : ContextWrapper(RuntimeEnvironment.getApplication()) {
        val asked = mutableListOf<Intent>()
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            asked += intent
            if (!opens(intent)) throw ActivityNotFoundException("No app for $intent")
            started += intent
        }
    }

    @Test
    fun thePlayAppOpensWhenThePhoneHasIt() {
        val phone = Phone { true }

        openPlayStorePage(phone)

        assertEquals("market://details?id=${phone.packageName}", phone.started.single().data.toString())
    }

    @Test
    fun withoutThePlayAppTheWebPageOpens() {
        val phone = Phone { it.data?.scheme == "https" }

        openPlayStorePage(phone)

        assertEquals("https://play.google.com/store/apps/details?id=${phone.packageName}", phone.started.single().data.toString())
        assertEquals(listOf("market", "https"), phone.asked.map { it.data?.scheme })
    }

    @Test
    fun withNeitherNothingOpensAndNothingCrashes() {
        val phone = Phone { false }

        openPlayStorePage(phone)

        assertTrue(phone.started.isEmpty())
        assertEquals(2, phone.asked.size)
    }
}
