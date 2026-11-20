package cx.aswin.boxlore.backup

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupFilesTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun `names use local time and sort oldest to newest`() {
        val name = AutoBackupFiles.fileName(at(2026, 12, 15, 9, 5), zone)
        assertEquals("boxlore_auto_backup_2026-12-15_0905.json", name)
        // 03:35 UTC is 09:05 in India: the listener's own clock names the file.
        assertEquals(name, AutoBackupFiles.fileName(LocalDateTime.of(2026, 12, 15, 3, 35).toInstant(ZoneOffset.UTC).toEpochMilli(), zone))
        val names = listOf(at(2027, 1, 2, 0, 0), at(2026, 12, 31, 23, 59), at(2026, 12, 15, 9, 5)).map { AutoBackupFiles.fileName(it, zone) }
        assertEquals(names.reversed(), names.sorted())
        assertEquals("boxlore_auto_backup_2026-12-15_0905.json.tmp", AutoBackupFiles.tempName(name))
        assertTrue(AutoBackupFiles.isBackup(name))
        assertTrue(AutoBackupFiles.isLeftoverTemp(AutoBackupFiles.tempName(name)))
    }

    @Test fun `pruning keeps the newest and never touches other files`() {
        val names = listOf(
            "boxlore_auto_backup_2026-12-01_0900.json",
            "boxlore_auto_backup_2026-12-08_0900.json",
            "boxlore_auto_backup_2026-12-15_0900.json",
            "boxlore_auto_backup_2026-11-24_0900.json",
            "boxlore_backup_1765000000000.json",
            "boxlore_auto_backup_notes.json",
            "boxlore_auto_backup_2026-12-01_0900.json.tmp",
            "BOXLORE_AUTO_BACKUP_2026-01-01_0900.json",
            "boxlore_auto_backup_2026-12-01_0900 (1).json",
            "holiday.jpg",
        )
        assertEquals(
            listOf("boxlore_auto_backup_2026-12-01_0900.json", "boxlore_auto_backup_2026-11-24_0900.json"),
            AutoBackupFiles.toDelete(names, keep = 2),
        )
        assertEquals(listOf("boxlore_auto_backup_2026-11-24_0900.json"), AutoBackupFiles.toDelete(names, keep = 3))
    }

    @Test fun `fewer backups than the limit deletes nothing`() {
        val names = listOf("boxlore_auto_backup_2026-12-01_0900.json", "boxlore_auto_backup_2026-12-08_0900.json")
        assertTrue(AutoBackupFiles.toDelete(names, keep = 5).isEmpty())
        assertTrue(AutoBackupFiles.toDelete(emptyList(), keep = 3).isEmpty())
        // A zero limit still keeps the backup that was just saved.
        assertEquals(listOf("boxlore_auto_backup_2026-12-01_0900.json"), AutoBackupFiles.toDelete(names, keep = 0))
    }

    @Test fun `the backup just saved is never pruned, even when a wrong clock gave it the oldest name`() {
        val saved = "boxlore_auto_backup_2026-01-01_0000.json"
        val names = listOf(
            saved,
            "boxlore_auto_backup_2026-12-01_0900.json",
            "boxlore_auto_backup_2026-12-08_0900.json",
            "boxlore_auto_backup_2026-12-15_0900.json",
        )
        assertEquals(
            listOf("boxlore_auto_backup_2026-12-08_0900.json", "boxlore_auto_backup_2026-12-01_0900.json"),
            AutoBackupFiles.toDelete(names, keep = 2, saved = saved),
        )
        assertEquals(
            listOf("boxlore_auto_backup_2026-12-15_0900.json", "boxlore_auto_backup_2026-12-08_0900.json", "boxlore_auto_backup_2026-12-01_0900.json"),
            AutoBackupFiles.toDelete(names, keep = 1, saved = saved),
        )
    }

    @Test fun `a saved backup a sync app already took away holds no place among the kept ones`() {
        val names = listOf(
            "boxlore_auto_backup_2026-12-01_0900.json",
            "boxlore_auto_backup_2026-12-08_0900.json",
            "boxlore_auto_backup_2026-12-15_0900.json",
        )
        assertTrue(AutoBackupFiles.toDelete(names, keep = 3, saved = "boxlore_auto_backup_2026-12-22_0900.json").isEmpty())
        assertEquals(
            listOf("boxlore_auto_backup_2026-12-01_0900.json"),
            AutoBackupFiles.toDelete(names, keep = 2, saved = "boxlore_auto_backup_2026-12-22_0900.json"),
        )
    }

    @Test fun `only exact names count as backups or leftovers`() {
        assertTrue(AutoBackupFiles.isLeftoverTemp("boxlore_auto_backup_2026-12-01_0900.json.tmp"))
        assertTrue(AutoBackupFiles.isLeftoverTemp(AutoBackupFiles.asideName("boxlore_auto_backup_2026-12-01_0900.json")))
        assertFalse(AutoBackupFiles.isBackup(AutoBackupFiles.asideName("boxlore_auto_backup_2026-12-01_0900.json")))
        assertFalse(AutoBackupFiles.isBackup("boxlore_auto_backup_2026-12-01_0900.json.tmp"))
        assertFalse(AutoBackupFiles.isBackup("my_boxlore_auto_backup_2026-12-01_0900.json"))
        assertFalse(AutoBackupFiles.isLeftoverTemp("boxlore_auto_backup_2026-12-01_0900.json"))
        assertFalse(AutoBackupFiles.isLeftoverTemp("notes.json.tmp"))
    }
}
