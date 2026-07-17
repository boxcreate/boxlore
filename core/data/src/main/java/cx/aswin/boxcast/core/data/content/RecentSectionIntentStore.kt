package cx.aswin.boxcast.core.data.content

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal data class RecentSectionIntentRecord(
    val id: String,
    val exposedAt: Long,
)

internal fun pruneRecentSectionIntents(
    records: List<RecentSectionIntentRecord>,
    now: Long,
    maximum: Int = RecentSectionIntentStore.MAX_RECENT_SECTION_IDS,
    ttlMillis: Long = RecentSectionIntentStore.RECENT_SECTION_TTL_MILLIS,
): List<RecentSectionIntentRecord> {
    require(maximum >= 0)
    require(ttlMillis >= 0L)
    val cutoff = now - ttlMillis
    return records.asSequence()
        .filter { record ->
            record.id.isNotBlank() &&
                record.id.length <= RecentSectionIntentStore.MAX_SECTION_ID_LENGTH &&
                record.exposedAt in cutoff..now
        }
        .sortedByDescending(RecentSectionIntentRecord::exposedAt)
        .distinctBy(RecentSectionIntentRecord::id)
        .take(maximum)
        .toList()
}

class RecentSectionIntentStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()
    private val recordListType = object : TypeToken<List<RecentSectionIntentRecord>>() {}.type

    @Synchronized
    fun recordVisible(
        sectionId: String,
        now: Long = System.currentTimeMillis(),
    ) {
        val normalizedId = sectionId.trim()
        if (normalizedId.isEmpty() || normalizedId.length > MAX_SECTION_ID_LENGTH) return
        val updated = pruneRecentSectionIntents(
            records = listOf(RecentSectionIntentRecord(normalizedId, now)) + readRecords(),
            now = now,
        )
        preferences.edit().putString(RECENT_SECTION_RECORDS_KEY, gson.toJson(updated)).apply()
    }

    @Synchronized
    fun recentIds(now: Long = System.currentTimeMillis()): List<String> {
        val stored = readRecords()
        val pruned = pruneRecentSectionIntents(stored, now)
        if (pruned != stored) {
            preferences.edit().putString(RECENT_SECTION_RECORDS_KEY, gson.toJson(pruned)).apply()
        }
        return pruned.map(RecentSectionIntentRecord::id)
    }

    private fun readRecords(): List<RecentSectionIntentRecord> {
        val json = preferences.getString(RECENT_SECTION_RECORDS_KEY, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<RecentSectionIntentRecord>>(json, recordListType).orEmpty()
        }.getOrDefault(emptyList())
    }

    companion object {
        internal const val MAX_RECENT_SECTION_IDS = 24
        internal const val MAX_SECTION_ID_LENGTH = 128
        internal const val RECENT_SECTION_TTL_MILLIS = 7L * 24L * 60L * 60L * 1_000L
        private const val PREFERENCES_NAME = "content_section_exposure_history"
        private const val RECENT_SECTION_RECORDS_KEY = "recent_intents_v1_json"
    }
}
