package cx.aswin.boxlore.core.playback.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServicePlayerFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val factory = PlaybackServicePlayerFactory(context, scope)

    @Test
    fun `createPlayerSessionActivityIntent returns valid PendingIntent`() {
        val pendingIntent = factory.createPlayerSessionActivityIntent()
        assertNotNull(pendingIntent)
    }

    @Test
    fun `createPlayerSessionActivityIntent catches SecurityException and returns null`() {
        val pendingIntent = factory.createPlayerSessionActivityIntent { _, _, _, _ ->
            throw SecurityException("Too many PendingIntent created for uid 10234")
        }
        assertNull(pendingIntent)
    }

    @Test
    fun `createPlayerSessionActivityIntent configures intent targeting MainActivity with EXTRA_OPEN_PLAYER`() {
        var capturedIntent: Intent? = null
        var capturedFlags: Int? = null

        val pendingIntent = factory.createPlayerSessionActivityIntent { ctx, code, intent, flags ->
            capturedIntent = intent
            capturedFlags = flags
            PendingIntent.getActivity(ctx, code, intent, flags)
        }

        assertNotNull(pendingIntent)
        assertNotNull(capturedIntent)
        assertEquals("cx.aswin.boxlore.MainActivity", capturedIntent?.component?.className)
        assertEquals(context.packageName, capturedIntent?.component?.packageName)
        assertTrue(capturedIntent?.getBooleanExtra("EXTRA_OPEN_PLAYER", false) == true)
        assertEquals(
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            capturedFlags,
        )
    }
}
