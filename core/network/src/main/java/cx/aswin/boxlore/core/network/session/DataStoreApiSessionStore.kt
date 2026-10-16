package cx.aswin.boxlore.core.network.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** DataStore file `boxlore_api_session`; the app's backup rules exclude it (it is bound to this phone). */
private val Context.apiSessionDataStore by preferencesDataStore(name = DataStoreApiSessionStore.FILE_NAME)

class DataStoreApiSessionStore(context: Context) : ApiSessionStore {
    private val appContext = context.applicationContext

    override suspend fun load(): StoredApiSession {
        val prefs = appContext.apiSessionDataStore.data.first()
        return StoredApiSession(
            token = prefs[TOKEN],
            installId = prefs[INSTALL_ID],
            clockOffsetSeconds = prefs[CLOCK_OFFSET] ?: 0L,
            appCheckFailures = prefs[APP_CHECK_FAILURES] ?: 0,
            nextUpgradeAtMs = prefs[NEXT_UPGRADE_AT] ?: 0L,
        )
    }

    override suspend fun save(state: StoredApiSession) {
        appContext.apiSessionDataStore.edit { prefs ->
            if (state.token == null) prefs.remove(TOKEN) else prefs[TOKEN] = state.token
            state.installId?.let { prefs[INSTALL_ID] = it }
            prefs[CLOCK_OFFSET] = state.clockOffsetSeconds
            prefs[APP_CHECK_FAILURES] = state.appCheckFailures
            prefs[NEXT_UPGRADE_AT] = state.nextUpgradeAtMs
        }
    }

    companion object {
        /** DataStore file name; keep it stable (renaming signs every install out of its session). */
        const val FILE_NAME = "boxlore_api_session"
        private val TOKEN = stringPreferencesKey("session_token")
        private val INSTALL_ID = stringPreferencesKey("install_id")
        private val CLOCK_OFFSET = longPreferencesKey("clock_offset_seconds")
        private val APP_CHECK_FAILURES = intPreferencesKey("app_check_failures")
        private val NEXT_UPGRADE_AT = longPreferencesKey("next_app_check_at_ms")
    }
}
