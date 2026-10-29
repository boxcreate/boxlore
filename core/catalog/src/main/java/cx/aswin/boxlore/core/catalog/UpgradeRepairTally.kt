package cx.aswin.boxlore.core.catalog

import android.content.SharedPreferences

/**
 * What the one-time upgrade repair ([UpgradeAddressRepair]) did across its runs, for the one
 * `own_ids_upgrade_completed` report, and whether that report was sent.
 *
 * The production store is a SharedPreferences file, which an Android backup carries with Room: a
 * phone restored after the report was sent never sends it again, and one restored mid-repair
 * keeps counting from where the backup left off. (The step 4 marker lives in `noBackupFilesDir`
 * on purpose, so a restore registers notifications again; the report must not follow it.)
 *
 * Added in P4 PR 18 of the own-episode-IDs plan (plan section 11, upgrade repair; analytics).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
interface UpgradeRepairTally {
    /** Counts recorded so far. */
    fun read(): UpgradeRepairCounts

    /** Adds one run that started at [startedAt] and ended at [endedAt]; false when it couldn't be saved. */
    fun recordRun(startedAt: Long, endedAt: Long, addressesSwapped: Int, notificationsReregistered: Int): Boolean

    /** True once the report was sent on this install (or the one it was restored from). */
    val reported: Boolean

    /** Records that the report was sent; false when the record couldn't be saved (then nothing is sent). */
    fun markReported(): Boolean
}

/**
 * Counts in an [UpgradeRepairTally]: runs, the start of the first one, the end of the last one,
 * addresses swapped and notifications registered again.
 */
data class UpgradeRepairCounts(
    val runs: Int = 0,
    val firstRunAt: Long = 0L,
    val lastRunAt: Long = 0L,
    val addressesSwapped: Int = 0,
    val notificationsReregistered: Int = 0,
)

/** In-memory [UpgradeRepairTally] for tests and previews. */
class MemoryUpgradeRepairTally : UpgradeRepairTally {
    private var counts = UpgradeRepairCounts()

    @Volatile
    override var reported: Boolean = false
        private set

    @Synchronized override fun read(): UpgradeRepairCounts = counts

    @Synchronized override fun recordRun(startedAt: Long, endedAt: Long, addressesSwapped: Int, notificationsReregistered: Int): Boolean {
        counts = counts.plusRun(startedAt, endedAt, addressesSwapped, notificationsReregistered)
        return true
    }

    @Synchronized override fun markReported(): Boolean {
        reported = true
        return true
    }
}

/**
 * [UpgradeRepairTally] in SharedPreferences file [FILE_NAME], written with `commit()` so a report
 * is never sent before its record is on disk. Stable storage name and keys: never rename.
 */
class SharedPreferencesUpgradeRepairTally(private val prefs: SharedPreferences) : UpgradeRepairTally {
    override val reported: Boolean
        @Synchronized get() = prefs.getBoolean(KEY_REPORTED, false)

    @Synchronized override fun read(): UpgradeRepairCounts = UpgradeRepairCounts(
        runs = prefs.getInt(KEY_RUNS, 0),
        firstRunAt = prefs.getLong(KEY_FIRST_RUN_AT, 0L),
        lastRunAt = prefs.getLong(KEY_LAST_RUN_AT, 0L),
        addressesSwapped = prefs.getInt(KEY_SWAPPED, 0),
        notificationsReregistered = prefs.getInt(KEY_REREGISTERED, 0),
    )

    @Synchronized override fun recordRun(startedAt: Long, endedAt: Long, addressesSwapped: Int, notificationsReregistered: Int): Boolean {
        val next = read().plusRun(startedAt, endedAt, addressesSwapped, notificationsReregistered)
        return prefs
            .edit()
            .putInt(KEY_RUNS, next.runs)
            .putLong(KEY_FIRST_RUN_AT, next.firstRunAt)
            .putLong(KEY_LAST_RUN_AT, next.lastRunAt)
            .putInt(KEY_SWAPPED, next.addressesSwapped)
            .putInt(KEY_REREGISTERED, next.notificationsReregistered)
            .commit()
    }

    @Synchronized override fun markReported(): Boolean = prefs.edit().putBoolean(KEY_REPORTED, true).commit()

    companion object {
        /** SharedPreferences file name. Stable storage name: never rename. */
        const val FILE_NAME = "boxlore_upgrade_repair_report"

        private const val KEY_RUNS = "runs"
        private const val KEY_FIRST_RUN_AT = "first_run_at"
        private const val KEY_LAST_RUN_AT = "last_run_at"
        private const val KEY_SWAPPED = "addresses_swapped"
        private const val KEY_REREGISTERED = "notifications_reregistered"
        private const val KEY_REPORTED = "reported"
    }
}

private fun UpgradeRepairCounts.plusRun(startedAt: Long, endedAt: Long, addressesSwapped: Int, notificationsReregistered: Int) = UpgradeRepairCounts(
    runs = runs + 1,
    firstRunAt = if (runs == 0) startedAt else firstRunAt,
    lastRunAt = endedAt,
    addressesSwapped = this.addressesSwapped + addressesSwapped.coerceAtLeast(0),
    notificationsReregistered = this.notificationsReregistered + notificationsReregistered.coerceAtLeast(0),
)
