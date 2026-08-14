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
    fun `restoreAndRefresh stubs, refreshes, saves tip, and patches tracked url`() =
        runTest {
            val stubs = mutableListOf<String>()
            val ensured = mutableListOf<String>()
            val invalidated = mutableListOf<String>()
            val refreshed = mutableListOf<String>()
            val saved = mutableListOf<String>()
            val tracked = mutableListOf<String>()
            val tip = TestFixtures.episode(id = "ep-1", title = "Tip", podcastId = "100")
            LibraryBackupDirectFeedRestore.restoreAndRefresh(
                targets =
                    listOf(
                        DirectFeedOptInBackup("100", "https://feeds.example/a.xml"),
                    ),
                actions =
                    DirectFeedRestoreActions(
                        restoreStub = { id, _ -> stubs.add(id) },
                        ensureFeedUrl = { id, _ -> ensured.add(id) },
                        invalidateCache = { invalidated.add(it) },
                        refreshFeed = { id, _ ->
                            refreshed.add(id)
                            EpisodeSupplementOutcome.Success(
                                addedCount = 1,
                                totalSupplementCount = 1,
                                newestFeedEpisode = tip,
                            )
                        },
                        saveTip = { id, episode -> saved.add("$id:${episode.id}") },
                        syncTrackedUrl = { tracked.add(it) },
                    ),
            )
            assertEquals(listOf("100"), stubs)
            assertEquals(listOf("100"), ensured)
            assertEquals(listOf("100"), invalidated)
            assertEquals(listOf("100"), refreshed)
            assertEquals(listOf("100:ep-1"), saved)
            assertEquals(listOf("100"), tracked)
        }

    @Test
    fun `restoreAndRefresh keeps stub when refresh fails`() =
        runTest {
            val stubs = mutableListOf<String>()
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
                        syncTrackedUrl = {},
                        onError = { id, _ -> errors.add(id) },
                    ),
            )
            assertEquals(listOf("100", "101"), stubs.sorted())
            assertTrue(saved.isEmpty())
            assertEquals(listOf("101"), errors)
        }

    @Test
    fun `restoreAndRefresh no-ops on empty targets`() =
        runTest {
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
}
