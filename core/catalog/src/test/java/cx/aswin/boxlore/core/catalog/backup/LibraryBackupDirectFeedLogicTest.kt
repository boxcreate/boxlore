package cx.aswin.boxlore.core.catalog.backup

import com.google.gson.Gson
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import kotlinx.coroutines.test.runTest
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
    fun `restoreTargets skips gson null podcastId and feedUrl`() {
        val parsed =
            Gson()
                .fromJson(
                    """
                    [
                      {"podcastId": null, "feedUrl": "https://feeds.example/a.xml"},
                      {"podcastId": "100", "feedUrl": null},
                      {"podcastId": "101", "feedUrl": "https://feeds.example/ok.xml"}
                    ]
                    """.trimIndent(),
                    Array<DirectFeedOptInBackup>::class.java,
                ).toList()
        val targets =
            LibraryBackupDirectFeedLogic.restoreTargets(
                backupOptIns = parsed,
                importedIds = listOf("100", "101"),
            )
        assertEquals(
            listOf(DirectFeedOptInBackup("101", "https://feeds.example/ok.xml")),
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
    fun `refreshPlan ingests subscription feed urls when backup has no opt-in list`() {
        val plan =
            LibraryBackupDirectFeedLogic.refreshPlan(
                importedIds = listOf("100", "200", "rss:show"),
                backupOptIns = null,
                subscriptionFeedUrls =
                    mapOf(
                        "100" to "https://feeds.example/a.xml",
                        "200" to null,
                        "rss:show" to "https://feeds.example/rss.xml",
                    ),
            )

        assertEquals(
            listOf(DirectFeedOptInBackup("100", "https://feeds.example/a.xml")),
            plan.directFeedTargets,
        )
        assertEquals(listOf("200"), plan.piSyncIds)
        assertEquals(listOf("rss:show"), plan.rssIds)
    }

    @Test
    fun `refreshPlan restores opt-ins then pi-syncs others and refreshes rss`() =
        runTest {
            val plan =
                LibraryBackupDirectFeedLogic.refreshPlan(
                    importedIds = listOf("100", "200", "rss:show"),
                    backupOptIns =
                        listOf(
                            DirectFeedOptInBackup("100", "https://feeds.example/a.xml"),
                        ),
                )
            assertEquals(
                listOf(DirectFeedOptInBackup("100", "https://feeds.example/a.xml")),
                plan.directFeedTargets,
            )
            assertEquals(listOf("200"), plan.piSyncIds)
            assertEquals(listOf("rss:show"), plan.rssIds)

            val order = mutableListOf<String>()
            LibraryBackupDirectFeedLogic.runPostSubscribeRefresh(
                plan = plan,
                restoreDirectFeeds = { targets ->
                    order.add("restore:${targets.map { it.podcastId }}")
                },
                syncPi = { ids -> order.add("sync:$ids") },
                refreshRss = { ids -> order.add("rss:$ids") },
            )
            assertEquals(
                listOf("restore:[100]", "sync:[200]", "rss:[rss:show]"),
                order,
            )
        }

    @Test
    fun `backup version is 6`() {
        assertEquals(6, LibraryBackupDirectFeedLogic.VERSION)
    }
}
