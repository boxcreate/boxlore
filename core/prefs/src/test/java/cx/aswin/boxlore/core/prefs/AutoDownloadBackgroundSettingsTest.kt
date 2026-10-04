package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadBackgroundSettingsTest {
    @Test fun absentConsentDefaultsOffAndOnlyExplicitSetterEnablesIt() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = UserPreferencesRepository(context)
        assertEquals(AutoDownloadBackgroundSettings(), prefs.autoDownloadBackgroundSettingsStream.first())
        prefs.setAutoDownloadWifiOnly(false)
        prefs.setAutoDownloadMaxEpisodes(5)
        assertFalse(prefs.autoDownloadBackgroundSettingsStream.first().enabled)
        prefs.setAutoDownloadBackgroundChecksEnabled(true)
        prefs.setAutoDownloadBackgroundWifiOnly(false)
        prefs.setAutoDownloadBackgroundChargingOnly(true)
        assertEquals(AutoDownloadBackgroundSettings(true, false, true), UserPreferencesRepository(context).autoDownloadBackgroundSettingsStream.first())
        prefs.setAutoDownloadBackgroundChecksEnabled(false)
        assertFalse(prefs.autoDownloadBackgroundSettingsStream.first().enabled)
    }

    @Test fun restoringGeneralSettingsCannotGrantBackgroundConsent() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.userPreferencesDataStore.edit {
            it[booleanPreferencesKey("explicitly_enabled")] = true
            it[booleanPreferencesKey("auto_download_background_checks_enabled")] = true
        }
        val settings = UserPreferencesRepository(context).autoDownloadBackgroundSettingsStream.first()
        assertFalse(settings.enabled)
        assertTrue(settings.wifiOnly)
    }
}
