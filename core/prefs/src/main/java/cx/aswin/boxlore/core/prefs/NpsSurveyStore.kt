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

/**
 * What the NPS survey's schedule reads: when it last appeared here, how many times boxlore has started
 * here, and when the player here last played an episode to the end.
 */
data class NpsSurveyRecord(val lastShownAtMs: Long?, val launches: Int, val lastFinishedAtMs: Long? = null,)

class NpsSurveyStore internal constructor(private val dataStore: DataStore<Preferences>,) {
    constructor(context: Context) : this(context.applicationContext.npsSurveyDataStore)

    suspend fun record(): NpsSurveyRecord {
        val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        return NpsSurveyRecord(lastShownAtMs = prefs[LAST_SHOWN_AT], launches = prefs[LAUNCHES] ?: 0, lastFinishedAtMs = prefs[LAST_FINISHED_AT])
    }

    /** The player here played an episode to the end at [atMs]; keeps the latest, so the finish outlives a restart. */
    suspend fun recordFinish(atMs: Long) {
        dataStore.edit { prefs -> if (atMs > (prefs[LAST_FINISHED_AT] ?: 0L)) prefs[LAST_FINISHED_AT] = atMs }
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
     * Runs once per install. An install that ran an older version ([updatedInstall]: Android updated it
     * after installing it) and whose listener answered the old survey ([legacyAnswered]: a score was
     * kept) waits the full gap from the update. Everyone else is asked like a new install: a listener
     * who only saw the old survey, dismissed it, or whose phone marked it shown before it ever appeared
     * (the backed-up `nps_survey_fired`, [legacyShown]). A phone installed fresh, restored from a backup
     * or not, is asked like any new install.
     *
     * v0.0.30 carried the wait over for every [legacyShown] phone, which kept most regular listeners
     * from being asked for 90 days. Once, that wait is lifted again where the listener never answered;
     * a real showing (it set the shared prompt time, [lastPromptAtMs], at the same moment) is kept.
     */
    suspend fun carryOverLegacy(legacyShown: Boolean, legacyAnswered: Boolean, updatedInstall: Boolean, lastPromptAtMs: Long?, nowMs: Long,) {
        dataStore.edit { prefs ->
            if (prefs[LEGACY_CHECKED] != true) {
                prefs[LEGACY_CHECKED] = true
                prefs[LEGACY_REVISED] = true
                if (legacyAnswered && updatedInstall && prefs[LAST_SHOWN_AT] == null) prefs[LAST_SHOWN_AT] = nowMs
                return@edit
            }
            if (prefs[LEGACY_REVISED] == true) return@edit
            prefs[LEGACY_REVISED] = true
            val shownAt = prefs[LAST_SHOWN_AT] ?: return@edit
            val realShowing = lastPromptAtMs != null && kotlin.math.abs(lastPromptAtMs - shownAt) <= SAME_MOMENT_MS
            val carriedOver = legacyShown && updatedInstall && !realShowing
            if (carriedOver && !legacyAnswered) prefs.remove(LAST_SHOWN_AT)
        }
    }

    companion object {
        const val FILE_NAME = "boxlore_nps_survey"
        private val LAST_SHOWN_AT = longPreferencesKey("last_shown_at")
        private val LAUNCHES = intPreferencesKey("launches")
        private val LEGACY_CHECKED = booleanPreferencesKey("legacy_checked")
        private val LEGACY_REVISED = booleanPreferencesKey("legacy_revised")
        private val LAST_FINISHED_AT = longPreferencesKey("last_finished_at")

        /** A showing writes its own time and the shared prompt time together; a carry-over writes only its own. */
        private const val SAME_MOMENT_MS = 60_000L
    }
}
