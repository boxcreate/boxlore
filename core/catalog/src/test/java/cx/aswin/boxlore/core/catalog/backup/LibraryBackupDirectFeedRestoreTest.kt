package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryBackupDirectFeedRestoreTest {
    @Test
    fun `restoreAndRefresh stubs, refreshes, saves tip, and patches tracked url`() = runTest {
        val order = mutableListOf<String>()
        val tip = TestFixtures.episode(id = "ep-1", title = "Tip", podcastId = "100")
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets =
            listOf(
                DirectFeedOptInBackup("100", "https://feeds.example/a.xml"),
            ),
            actions =
            DirectFeedRestoreActions(
                restoreStub = { id, _ -> order.add("stub:$id") },
                ensureFeedUrl = { id, _ -> order.add("ensure:$id") },
                invalidateCache = { order.add("invalidate:$it") },
                refreshFeed = { id, _ ->
                    order.add("refresh:$id")
                    EpisodeSupplementOutcome.Success(
                        addedCount = 1,
                        totalSupplementCount = 1,
                        newestFeedEpisode = tip,
                    )
                },
                saveTip = { id, episode -> order.add("tip:$id:${episode.id}") },
                syncTrackedUrl = { order.add("tracked:$it") },
            ),
        )
        assertEquals(
            listOf(
                "stub:100",
                "ensure:100",
                "tracked:100",
                "invalidate:100",
                "refresh:100",
                "tip:100:ep-1",
            ),
            order,
        )
    }

    @Test
    fun `restoreAndRefresh keeps stub when refresh fails`() = runTest {
        val stubs = mutableListOf<String>()
        val tracked = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val errors = mutableListOf<String>()
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets =
            listOf(
                DirectFeedOptInBackup("100", "https://feeds.example/a.xml"),
                DirectFeedOptInBackup("101", "https://feeds.example/b.xml"),
            ),
            actions =
            DirectFeedRestoreActions(
                restoreStub = { id, _ -> stubs.add(id) },
                ensureFeedUrl = { _, _ -> },
                invalidateCache = {},
                refreshFeed = { id, _ ->
                    if (id == "100") {
                        EpisodeSupplementOutcome.Failure("nope")
                    } else {
                        throw Exception("boom")
                    }
                },
                saveTip = { id, _ -> saved.add(id) },
                syncTrackedUrl = { tracked.add(it) },
                onError = { id, _ -> errors.add(id) },
            ),
        )
        assertEquals(listOf("100", "101"), stubs.sorted())
        assertEquals(listOf("100", "101"), tracked.sorted())
        assertTrue(saved.isEmpty())
        assertEquals(listOf("101"), errors)
    }

    @Test
    fun `restoreAndRefresh no-ops on empty targets`() = runTest {
        var called = false
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets = emptyList(),
            actions =
            DirectFeedRestoreActions(
                restoreStub = { _, _ -> called = true },
                ensureFeedUrl = { _, _ -> called = true },
                invalidateCache = { called = true },
                refreshFeed = { _, _ ->
                    called = true
                    EpisodeSupplementOutcome.NoDisconnect
                },
                saveTip = { _, _ -> called = true },
                syncTrackedUrl = { called = true },
            ),
        )
        assertFalse(called)
    }

    @Test
    fun `restoreAndRefresh invokes progress callbacks per target`() = runTest {
        val started = mutableListOf<String>()
        val completed = mutableListOf<String>()
        LibraryBackupDirectFeedRestore.restoreAndRefresh(
            targets = listOf(
                DirectFeedOptInBackup("201", "https://feeds.example/201.xml"),
                DirectFeedOptInBackup("202", "https://feeds.example/202.xml"),
            ),
            actions = DirectFeedRestoreActions(
                restoreStub = { _, _ -> },
                ensureFeedUrl = { _, _ -> },
                invalidateCache = {},
                refreshFeed = { _, _ -> EpisodeSupplementOutcome.NoDisconnect },
                saveTip = { _, _ -> },
                syncTrackedUrl = {},
            ),
            onTargetStarted = { started.add(it) },
            onTargetCompleted = { completed.add(it) },
        )
        assertEquals(listOf("201", "202"), started.sorted())
        assertEquals(listOf("201", "202"), completed.sorted())
    }
}
