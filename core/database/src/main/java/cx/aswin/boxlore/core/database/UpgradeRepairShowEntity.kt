package cx.aswin.boxlore.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Progress of the one-time feed address repair for one followed show.
 *
 * The repair runs per show: (1) ask the server for the show's current address, (2) swap a dead or
 * moved saved address, (3) re-read the feed. [completedStep] is the last step that finished, so
 * the work resumes after process death. Notification re-registration (step 4) is recorded outside
 * Room, so a restored device registers again. The v39 upgrade adds one [UpgradeRepairState.PENDING]
 * row per followed Podcast Index show; a fresh install starts with none.
 *
 * @property completedStep 0 before step 1 finishes, then 1..3
 * @property attempts tries so far, for the per-show cap
 * @property state [UpgradeRepairState]
 * @property checkedFetchUrl the publisher address step 1 got; null keeps the saved address
 * @property checkedTransportUrl the transport address step 1 got, when the feed must be proxied
 * @property checkedFeedSource where the server's address came from, as the server names it
 * @property checkedEpNs the episode namespace the server reported; recorded, never applied to issued IDs
 * @property lastAttemptAt when the last try ran (epoch ms)
 * @property lastError a short reason for the last failure, for diagnostics
 *
 * Added in P4 of the own-episode-IDs plan (plan section 11, upgrade repair).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
@Entity(tableName = "upgrade_repair_shows")
data class UpgradeRepairShowEntity(
    @PrimaryKey
    val podcastId: String,
    @ColumnInfo(defaultValue = "0")
    val completedStep: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val attempts: Int = 0,
    @ColumnInfo(defaultValue = "'pending'")
    val state: String = UpgradeRepairState.PENDING,
    val checkedFetchUrl: String? = null,
    val checkedTransportUrl: String? = null,
    val checkedFeedSource: String? = null,
    val checkedEpNs: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val lastAttemptAt: Long = 0L,
    val lastError: String? = null,
)

/**
 * Row counts of `upgrade_repair_shows` ([UpgradeRepairDao.showCounts]): every show the upgrade
 * seeded, and those that reached [UpgradeRepairState.GAVE_UP]. A query result, not a table.
 *
 * Added in P4 PR 18 of the own-episode-IDs plan (plan section 11, upgrade repair; analytics).
 */
data class UpgradeRepairShowCounts(val shows: Int, val gaveUp: Int)

/**
 * States of an [UpgradeRepairShowEntity].
 *
 * Added in P4 of the own-episode-IDs plan (plan section 11, upgrade repair).
 * COMPAT(own-ids): stored state values. Permanent: never remove or rename (MIGRATION.md §5).
 */
object UpgradeRepairState {
    /** Steps left to run. */
    const val PENDING = "pending"

    /** Steps 1–3 finished, or the show no longer needs them (unfollowed). */
    const val DONE = "done"

    /** The per-show attempt cap was reached; the show is not tried again. */
    const val GAVE_UP = "gave_up"
}
