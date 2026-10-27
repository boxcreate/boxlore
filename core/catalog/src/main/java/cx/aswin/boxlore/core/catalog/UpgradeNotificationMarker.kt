package cx.aswin.boxlore.core.catalog

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.Properties

/**
 * Step 4 of the one-time feed address repair ([UpgradeAddressRepair]): which followed shows had
 * their new-episode notifications registered again on this install, and whether the step finished.
 * It also records that the sync's identity catch-up was asked for once steps 1–3 were over.
 *
 * Steps 1–3 are kept in Room (`upgrade_repair_shows`), which an Android restore brings back; this
 * marker lives in `noBackupFilesDir`, which a restore never brings, so a restored phone (a new
 * messaging token, no topic subscriptions) registers every followed show again even when Room says
 * the repair is done. The catch-up is then asked for again too, which only makes the next sync
 * pull from the start once more.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair; "Android backup").
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
interface UpgradeNotificationMarker {
    /** True once every followed show was registered again after the repair. */
    val isComplete: Boolean

    /** Shows whose registration was already requested on this install. */
    fun registered(): Set<String>

    /** Records [podcastIds] as requested; false when the record couldn't be saved. */
    fun addRegistered(podcastIds: Collection<String>): Boolean

    /** Records that step 4 finished; false when the record couldn't be saved. */
    fun markComplete(): Boolean

    /** True once the sync's identity catch-up was asked for after steps 1–3. */
    val syncCatchUpRequested: Boolean

    /** Records that the sync's identity catch-up was asked for; false when the record couldn't be saved. */
    fun markSyncCatchUpRequested(): Boolean
}

/** In-memory [UpgradeNotificationMarker] for tests and previews. */
class MemoryUpgradeNotificationMarker : UpgradeNotificationMarker {
    private val values = linkedSetOf<String>()

    @Volatile
    override var isComplete: Boolean = false
        private set

    @Synchronized override fun registered(): Set<String> = values.toSet()

    @Synchronized override fun addRegistered(podcastIds: Collection<String>): Boolean {
        values += podcastIds
        return true
    }

    @Synchronized override fun markComplete(): Boolean {
        isComplete = true
        return true
    }

    @Volatile
    override var syncCatchUpRequested: Boolean = false
        private set

    @Synchronized override fun markSyncCatchUpRequested(): Boolean {
        syncCatchUpRequested = true
        return true
    }
}

/**
 * [UpgradeNotificationMarker] in one file under `noBackupFilesDir` (the app opens
 * [FILE_NAME]); written atomically, so process death mid-write keeps the previous record.
 * A file that can't be read counts as no record: the shows are registered again, which is harmless.
 */
class FileUpgradeNotificationMarker internal constructor(private val storage: AtomicFile) : UpgradeNotificationMarker {
    constructor(file: File) : this(AtomicFile(file))

    override val isComplete: Boolean
        @Synchronized get() = read().getProperty(COMPLETE_KEY) == "true"

    override val syncCatchUpRequested: Boolean
        @Synchronized get() = read().getProperty(SYNC_CATCH_UP_KEY) == "true"

    @Synchronized override fun registered(): Set<String> = read().stringPropertyNames().filterNot { it.startsWith(RECORD_KEY_PREFIX) }.toSet()

    @Synchronized override fun addRegistered(podcastIds: Collection<String>): Boolean =
        podcastIds.isEmpty() || change { values -> podcastIds.forEach { values.setProperty(it, "1") } }

    @Synchronized override fun markComplete(): Boolean = change { it.setProperty(COMPLETE_KEY, "true") }

    @Synchronized override fun markSyncCatchUpRequested(): Boolean = change { it.setProperty(SYNC_CATCH_UP_KEY, "true") }

    private fun read(): Properties = Properties().apply {
        try {
            storage.openRead().use { load(it) }
        } catch (_: FileNotFoundException) {
            // No record yet.
        } catch (_: java.io.IOException) {
            clear()
        } catch (_: IllegalArgumentException) {
            clear()
        }
    }

    private fun change(edit: (Properties) -> Unit): Boolean = try {
        val values = read()
        edit(values)
        val output = storage.startWrite()
        try {
            values.store(output, "Feed address repair notifications v1")
            storage.finishWrite(output)
        } catch (error: java.io.IOException) {
            storage.failWrite(output)
            throw error
        }
        true
    } catch (_: java.io.IOException) {
        false
    }

    companion object {
        /** File name under `noBackupFilesDir`. Stable storage name: never rename. */
        const val FILE_NAME = "upgrade_repair_notifications"

        /** Keys of the file's own records start with this; show IDs never do. */
        private const val RECORD_KEY_PREFIX = "__"
        private const val COMPLETE_KEY = "__complete"
        private const val SYNC_CATCH_UP_KEY = "__sync_catch_up"
    }
}
