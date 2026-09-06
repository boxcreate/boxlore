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
    fun heroVisualFor_importingJsonWithZeroTotal_isIndeterminate() {
        val state = OpmlImportState.ImportingJson(totalCount = 0)
        assertEquals(ImportHeroVisual.Indeterminate, heroVisualFor(state))
    }

    @Test
    fun heroVisualFor_importingJsonWithPositiveTotal_isProgress() {
        val state = OpmlImportState.ImportingJson(
            totalCount = 20,
            progress = 0.45f,
            phase = JsonBackupPhase.SUBSCRIBING,
        )
        val hero = heroVisualFor(state)
        assertTrue(hero is ImportHeroVisual.Progress)
        assertEquals(0.45f, (hero as ImportHeroVisual.Progress).value, 0.001f)
    }

    @Test
    fun heroVisualFor_zeroOrNegativeProgress_isIndeterminate() {
        val zeroState = OpmlImportState.ImportingJson(
            totalCount = 10,
            progress = 0f,
        )
        assertEquals(ImportHeroVisual.Indeterminate, heroVisualFor(zeroState))

        val negativeState = OpmlImportState.ImportingJson(
            totalCount = 10,
            progress = -0.2f,
        )
        assertEquals(ImportHeroVisual.Indeterminate, heroVisualFor(negativeState))
    }

    @Test
    fun heroVisualFor_clampsProgressAtOne() {
        val overflowState = OpmlImportState.ImportingJson(
            totalCount = 10,
            progress = 1.8f,
        )
        val overflowHero = heroVisualFor(overflowState) as ImportHeroVisual.Progress
        assertEquals(1f, overflowHero.value, 0.001f)
    }

    @Test
    fun heroVisualFor_success_isComplete() {
        val state = OpmlImportState.Success(
            importedCount = 5,
            completedCount = 0,
            isJson = true,
        )
        assertEquals(ImportHeroVisual.Complete, heroVisualFor(state))
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
