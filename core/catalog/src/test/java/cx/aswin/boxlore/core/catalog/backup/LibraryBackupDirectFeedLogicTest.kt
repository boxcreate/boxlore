package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryBackupDirectFeedLogicTest {
    @Test
    fun `mergeExport keeps https port urls and drops rss ids`() {
        val merged =
            LibraryBackupDirectFeedLogic.mergeExport(
                portOptIns =
                    listOf(
                        EpisodeSupplementPort.DirectFeedOptIn("100", "https://feeds.example/a.xml"),
                        EpisodeSupplementPort.DirectFeedOptIn("rss:show", "https://feeds.example/rss.xml"),
                        EpisodeSupplementPort.DirectFeedOptIn("101", "http://insecure.example/b.xml"),
                    ),
                subscriptionFeedUrls =
                    mapOf(
                        "101" to "https://feeds.example/b.xml",
                    ),
            )
        assertEquals(
            listOf(
                DirectFeedOptInBackup("100", "https://feeds.example/a.xml"),
                DirectFeedOptInBackup("101", "https://feeds.example/b.xml"),
            ),
            merged,
        )
    }

    @Test
    fun `restoreTargets only includes imported PI shows with https urls`() {
        val targets =
            LibraryBackupDirectFeedLogic.restoreTargets(
                backupOptIns =
                    listOf(
                        DirectFeedOptInBackup("100", "https://feeds.example/a.xml"),
                        DirectFeedOptInBackup("101", "https://feeds.example/skipped.xml"),
                        DirectFeedOptInBackup("rss:show", "https://feeds.example/rss.xml"),
                        DirectFeedOptInBackup("102", "http://insecure.example/c.xml"),
                    ),
                importedIds = listOf("100", "rss:show", "102"),
            )
        assertEquals(
            listOf(DirectFeedOptInBackup("100", "https://feeds.example/a.xml")),
            targets,
        )
    }

    @Test
    fun `piSyncIds omits rss and restored opt-ins`() {
        assertEquals(
            listOf("200"),
            LibraryBackupDirectFeedLogic.piSyncIds(
                importedIds = listOf("100", "200", "rss:show"),
                restoredOptInIds = setOf("100"),
            ),
        )
    }

    @Test
    fun `backup version is 6`() {
        assertEquals(6, LibraryBackupDirectFeedLogic.VERSION)
    }
}
