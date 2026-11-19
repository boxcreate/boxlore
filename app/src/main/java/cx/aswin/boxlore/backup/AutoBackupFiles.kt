package cx.aswin.boxlore.backup

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Names of the automatic backup's files. Only names that match [isBackup] exactly are ever pruned, so
 * manual exports and anything else in the folder are never touched.
 */
internal object AutoBackupFiles {
    const val PREFIX = "boxlore_auto_backup_"
    private const val EXTENSION = ".json"
    private const val TEMP_SUFFIX = ".tmp"
    private val BACKUP_NAME = Regex("^boxlore_auto_backup_\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.json$")
    private val TEMP_NAME = Regex("^boxlore_auto_backup_\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.json\\.tmp$")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.US)

    /** `boxlore_auto_backup_YYYY-MM-DD_HHmm.json` in local time, so names sort oldest to newest. */
    fun fileName(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        PREFIX + STAMP.withZone(zone).format(Instant.ofEpochMilli(nowMs)) + EXTENSION

    /** Written first and renamed when done, so a sync app never copies a half-written backup. */
    fun tempName(fileName: String): String = fileName + TEMP_SUFFIX

    fun isBackup(name: String): Boolean = BACKUP_NAME.matches(name)

    /** A temporary file a stopped run left behind. */
    fun isLeftoverTemp(name: String): Boolean = TEMP_NAME.matches(name)

    /**
     * The automatic backups beyond the newest [keep], oldest last; other names are never included. [saved] (the
     * backup just written) always counts as one of the kept files, even when a wrong clock gave it an older name.
     */
    fun toDelete(names: Collection<String>, keep: Int, saved: String? = null): List<String> {
        val kept = keep.coerceAtLeast(1)
        val others = names.filter { isBackup(it) && it != saved }.distinct().sortedDescending()
        return others.drop(if (saved != null && isBackup(saved)) kept - 1 else kept)
    }
}
