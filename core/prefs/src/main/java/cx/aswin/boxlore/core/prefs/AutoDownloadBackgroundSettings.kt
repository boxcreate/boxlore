package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Excluded from Android cloud backup and device transfer; never part of library/cloud settings.
private val Context.autoDownloadBackgroundDataStore by preferencesDataStore(name = "auto_download_background")

data class AutoDownloadBackgroundSettings(
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
)

internal class AutoDownloadBackgroundPreferences(context: Context) {
    private val store = context.autoDownloadBackgroundDataStore
    val settings = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        AutoDownloadBackgroundSettings(values[ENABLED] ?: false, values[WIFI_ONLY] ?: true, values[CHARGING_ONLY] ?: false)
    }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[ENABLED] = enabled }
    }
    suspend fun setWifiOnly(wifiOnly: Boolean) {
        store.edit { it[WIFI_ONLY] = wifiOnly }
    }
    suspend fun setChargingOnly(chargingOnly: Boolean) {
        store.edit { it[CHARGING_ONLY] = chargingOnly }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("explicitly_enabled")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CHARGING_ONLY = booleanPreferencesKey("charging_only")
    }
}
