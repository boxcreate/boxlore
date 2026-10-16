package cx.aswin.boxlore.ui.libraryimport

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import cx.aswin.boxlore.core.catalog.backup.JsonBackupPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OpmlImportProgressContentTest {
    private lateinit var activity: ComponentActivity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    }

    @Test
    fun contentKeyFor_returnsImportingJsonForJsonImportState() {
        val state = OpmlImportState.ImportingJson(
            currentTitle = "Show A",
            progress = 0.5f,
            currentCount = 5,
            totalCount = 10,
            phase = JsonBackupPhase.SUBSCRIBING,
        )
        assertEquals("importing_json", contentKeyFor(state))
    }

    @Test
    fun areAppNotificationsEnabled_and_hasPostNotificationPermission_executeSuccessfully() {
        val enabled = areAppNotificationsEnabled(activity)
        val permissionResult = hasPostNotificationPermission(activity)
        assertEquals(enabled, permissionResult)
    }

    @Test
    fun openAppNotificationSettings_startsExpectedSettingsIntent() {
        openAppNotificationSettings(activity)
        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivity
        assertNotNull(nextIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, nextIntent.action)
            assertEquals(activity.packageName, nextIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        }
        assertTrue(nextIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
