package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * The automatic backup's folder and schedule. A folder link only works on the phone that granted it, so the
 * backup rules leave this file out and it is never part of a library backup's settings.
 */
internal val Context.autoBackupDataStore: DataStore<Preferences> by preferencesDataStore(name = AutoBackupStore.FILE_NAME)

/** How often the automatic backup runs. */
enum class AutoBackupFrequency(val storedValue: String, val intervalDays: Long) {
    DAILY("daily", 1),
    WEEKLY("weekly", 7),
    ;

    companion object {
        val DEFAULT = WEEKLY

        fun fromStored(value: String?): AutoBackupFrequency = entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

/** Why the last automatic backup didn't save; the stored codes are also the analytics error codes. */
object AutoBackupProblem {
    /** Android no longer lets boxlore write to the folder (the grant was removed). */
    const val PERMISSION_LOST = "permission_lost"

    /** The folder was moved, renamed or deleted, or its storage is gone. */
    const val FOLDER_MISSING = "folder_missing"

    /** Writing the file failed after every retry. */
    const val IO_ERROR = "io_error"

    /** The backup itself couldn't be built after every retry. */
    const val EXPORT_FAILED = "export_failed"

    /** Problems only a new folder fixes. */
    fun needsNewFolder(code: String?): Boolean = code == PERMISSION_LOST || code == FOLDER_MISSING
}

/**
 * [folderUri] is the persisted document tree link and [folderName] its name when picked; both are null
 * while no folder is chosen. [keepCount] is how many automatic backups stay in the folder.
 */
data class AutoBackupSettings(
    val enabled: Boolean = false,
    val folderUri: String? = null,
    val folderName: String? = null,
    val frequency: AutoBackupFrequency = AutoBackupFrequency.DEFAULT,
    val keepCount: Int = AutoBackupStore.DEFAULT_KEEP,
    val lastSuccessAt: Long? = null,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null,
    val lastFileName: String? = null,
) {
    /** Backups run only while on and with a folder. */
    val isActive: Boolean get() = enabled && folderUri != null
}

/** Reads and writes the `auto_backup` DataStore; every instance on one process shares the same file. */
class AutoBackupStore internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.autoBackupDataStore)

    val settings: Flow<AutoBackupSettings> =
        dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .map(::read)
            .distinctUntilChanged()

    suspend fun current(): AutoBackupSettings = settings.first()

    /** One read of the settings, or null when the file can't be read now (never the "off" defaults). */
    suspend fun readable(): AutoBackupSettings? =
        try {
            dataStore.data.map(::read).first()
        } catch (error: IOException) {
            null
        }

    /**
     * Like [settings], but an unreadable file emits nothing instead of reading as "off", so the schedule that
     * follows it is left as it was rather than cancelled. The file is read again after a pause (longer each
     * time, up to [MAX_READ_RETRY_MS]), so later changes still reach the schedule once it reads again.
     */
    val settingsWhenReadable: Flow<AutoBackupSettings> =
        dataStore.data
            .map(::read)
            .retryWhen { error, attempt ->
                (error is IOException).also { unreadable -> if (unreadable) delay(readRetryDelayMs(attempt)) }
            }
            .distinctUntilChanged()

    /**
     * A folder turns backups on and forgets the old folder's problem. A different folder (or one picked again
     * after turning off) also forgets the last backup, which was saved somewhere else.
     */
    suspend fun setFolder(uri: String, name: String?) {
        dataStore.edit { prefs ->
            if (prefs[FOLDER_URI] != uri) {
                prefs.remove(LAST_SUCCESS_AT)
                prefs.remove(LAST_FILE_NAME)
            }
            prefs[FOLDER_URI] = uri
            if (name.isNullOrBlank()) prefs.remove(FOLDER_NAME) else prefs[FOLDER_NAME] = name
            prefs[ENABLED] = true
            prefs.remove(LAST_ERROR)
        }
    }

    /** Off forgets the folder too: its grant is released, so it could never be written again. */
    suspend fun turnOff() {
        dataStore.edit { prefs ->
            prefs[ENABLED] = false
            prefs.remove(FOLDER_URI)
            prefs.remove(FOLDER_NAME)
            prefs.remove(LAST_ERROR)
        }
    }

    suspend fun setFrequency(frequency: AutoBackupFrequency) {
        dataStore.edit { it[FREQUENCY] = frequency.storedValue }
    }

    suspend fun setKeepCount(count: Int) {
        dataStore.edit { it[KEEP_COUNT] = sanitizeKeep(count) }
    }

    suspend fun recordAttempt(atMs: Long) {
        dataStore.edit { it[LAST_ATTEMPT_AT] = atMs }
    }

    /** Kept only while [folderUri] is still the chosen folder, so a run for an old folder can't stamp the new one. */
    suspend fun recordSuccess(atMs: Long, fileName: String, folderUri: String) {
        dataStore.edit { prefs ->
            if (prefs[FOLDER_URI] != folderUri) return@edit
            prefs[LAST_SUCCESS_AT] = atMs
            prefs[LAST_FILE_NAME] = fileName
            prefs.remove(LAST_ERROR)
        }
    }

    /** Kept only while [folderUri] is still the chosen folder, so a run for an old folder can't flag the new one. */
    suspend fun recordProblem(code: String, folderUri: String) {
        dataStore.edit { prefs ->
            if (prefs[FOLDER_URI] == folderUri) prefs[LAST_ERROR] = code
        }
    }

    private fun read(prefs: Preferences) = AutoBackupSettings(
        enabled = prefs[ENABLED] ?: false,
        folderUri = prefs[FOLDER_URI],
        folderName = prefs[FOLDER_NAME],
        frequency = AutoBackupFrequency.fromStored(prefs[FREQUENCY]),
        keepCount = sanitizeKeep(prefs[KEEP_COUNT] ?: DEFAULT_KEEP),
        lastSuccessAt = prefs[LAST_SUCCESS_AT],
        lastAttemptAt = prefs[LAST_ATTEMPT_AT],
        lastError = prefs[LAST_ERROR],
        lastFileName = prefs[LAST_FILE_NAME],
    )

    companion object {
        const val FILE_NAME = "auto_backup"
        const val DEFAULT_KEEP = 5
        val KEEP_CHOICES = listOf(3, 5, 10)

        fun sanitizeKeep(count: Int): Int = if (count in KEEP_CHOICES) count else DEFAULT_KEEP

        /** The longest pause before an unreadable settings file is read again. */
        const val MAX_READ_RETRY_MS = 10 * 60 * 1000L
        private const val FIRST_READ_RETRY_MS = 1_000L

        /** One second, doubling after each failed read, up to [MAX_READ_RETRY_MS]. */
        internal fun readRetryDelayMs(attempt: Long): Long =
            (FIRST_READ_RETRY_MS shl attempt.coerceIn(0L, 20L).toInt()).coerceAtMost(MAX_READ_RETRY_MS)

        private val ENABLED = booleanPreferencesKey("enabled")
        private val FOLDER_URI = stringPreferencesKey("folder_uri")
        private val FOLDER_NAME = stringPreferencesKey("folder_name")
        private val FREQUENCY = stringPreferencesKey("frequency")
        private val KEEP_COUNT = intPreferencesKey("keep_count")
        private val LAST_SUCCESS_AT = longPreferencesKey("last_success_at")
        private val LAST_ATTEMPT_AT = longPreferencesKey("last_attempt_at")
        private val LAST_ERROR = stringPreferencesKey("last_error")
        private val LAST_FILE_NAME = stringPreferencesKey("last_file_name")
    }
}
