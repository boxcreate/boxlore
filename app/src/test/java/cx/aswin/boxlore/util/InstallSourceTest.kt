package cx.aswin.boxlore.util

import android.app.Application
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The install source as Android records it, including when Android can't answer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class InstallSourceTest {
    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun readsTheInstallerAndTheAppThatAskedForTheInstallSeparately() {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, "com.android.chrome", "com.google.android.packageinstaller")

        assertEquals(
            InstallOrigin(installer = "com.google.android.packageinstaller", initiator = "com.android.chrome"),
            context.installOrigin(),
        )
        assertFalse(context.isInstalledFromPlayStore())
    }

    @Test
    fun aPlayStoreInstallIsRecognised() {
        shadowOf(context.packageManager).setInstallSourceInfo(context.packageName, "com.android.vending", "com.android.vending")

        assertTrue(context.installOrigin()!!.fromPlayStore)
        assertTrue(context.isInstalledFromPlayStore())
    }

    @Test
    fun aFailedLookupIsUnknownRatherThanNotPlay() {
        // Android throws for a package it doesn't know.
        val unknownPackage = object : ContextWrapper(context) {
            override fun getPackageName() = "cx.aswin.boxlore.not.installed"
        }

        assertNull(unknownPackage.installOrigin())
        assertFalse(unknownPackage.isInstalledFromPlayStore())
    }
}
