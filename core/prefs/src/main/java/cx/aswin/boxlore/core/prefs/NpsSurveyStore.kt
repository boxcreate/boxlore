package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/** The NPS survey's history on this install. The backup rules leave the file out, so a restored phone is asked afresh. */
internal val Context.npsSurveyDataStore: DataStore<Preferences> by preferencesDataStore(name = NpsSurveyStore.FILE_NAME)

/** What the NPS survey's schedule reads: when it last appeared here, and how many times boxlore has started here. */
data class NpsSurveyRecord(val lastShownAtMs: Long?, val launches: Int,)

class NpsSurveyStore internal constructor(private val dataStore: DataStore<Preferences>,) {
    constructor(context: Context) : this(context.applicationContext.npsSurveyDataStore)

    suspend fun record(): NpsSurveyRecord {
        val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        return NpsSurveyRecord(lastShownAtMs = prefs[LAST_SHOWN_AT], launches = prefs[LAUNCHES] ?: 0)
    }

    /** Counts one start of boxlore on this install, so the survey never appears in the first one. */
    suspend fun recordLaunch() {
        dataStore.edit { it[LAUNCHES] = (it[LAUNCHES] ?: 0) + 1 }
    }

    /** The survey was on screen at [nowMs]; it waits [NpsSurveySchedule.ASK_AGAIN_DAYS] before asking again. */
    suspend fun markShown(nowMs: Long) {
        dataStore.edit { it[LAST_SHOWN_AT] = nowMs }
    }

    /** Debug: forgets when the survey last appeared and counts at least two starts, so the next finish can ask. */
    suspend fun resetSchedule() {
        dataStore.edit { prefs ->
            prefs.remove(LAST_SHOWN_AT)
            prefs[LAUNCHES] = maxOf(prefs[LAUNCHES] ?: 0, 2)
        }
    }

    /**
     * Runs once per install. An install that already showed the survey from an older version
     * ([legacyShown], the backed-up `nps_survey_fired`) waits the full gap from the update. A phone
     * restored from a backup is new here (installed within a day of [nowMs]), so the copied flag is
     * ignored and it is asked like any new install.
     */
    suspend fun carryOverLegacy(legacyShown: Boolean, installedAtMs: Long, nowMs: Long,) {
        dataStore.edit { prefs ->
            if (prefs[LEGACY_CHECKED] == true) return@edit
            prefs[LEGACY_CHECKED] = true
            val updatedInstall = installedAtMs > 0L && nowMs - installedAtMs >= RESTORED_INSTALL_WINDOW_MS
            if (legacyShown && updatedInstall && prefs[LAST_SHOWN_AT] == null) prefs[LAST_SHOWN_AT] = nowMs
        }
    }

    companion object {
        const val FILE_NAME = "boxlore_nps_survey"
        private const val RESTORED_INSTALL_WINDOW_MS = 24L * 60 * 60 * 1000
        private val LAST_SHOWN_AT = longPreferencesKey("last_shown_at")
        private val LAUNCHES = intPreferencesKey("launches")
        private val LEGACY_CHECKED = booleanPreferencesKey("legacy_checked")
    }
}
