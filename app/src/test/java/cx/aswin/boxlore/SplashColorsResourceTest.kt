package cx.aswin.boxlore

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launch screen uses boxlore's dark background in dark theme, and keeps the purple in light theme. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SplashColorsResourceTest {
    private fun splash(): Int = ContextCompat.getColor(ApplicationProvider.getApplicationContext(), R.color.splash_background)

    @Test @Config(sdk = [31], qualifiers = "night")
    fun `dark theme launches on the dark background on Android 12`() = assertEquals(0xFF121318.toInt(), splash())

    @Test @Config(sdk = [34], qualifiers = "night")
    fun `dark theme launches on the dark background on Android 14`() = assertEquals(0xFF121318.toInt(), splash())

    @Test @Config(sdk = [31], qualifiers = "notnight")
    fun `light theme keeps the purple launch on Android 12`() = assertEquals(0xFF573DF5.toInt(), splash())

    @Test @Config(sdk = [34], qualifiers = "notnight")
    fun `light theme keeps the purple launch on Android 14`() = assertEquals(0xFF573DF5.toInt(), splash())
}
