package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Archiving an episode ([EpisodeListenerStore.setDismissed]) across its stored IDs, through Room: the
 * decision reaches every trusted ID and the group settings like a like does, never touches progress or
 * completion, and never makes a row count as listening.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeDismissalStoreTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var store: EpisodeListenerStore
    private val history get() = database.listeningHistoryDao()
    private val identity get() = database.episodeIdentityDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        store = database.episodeListenerStore()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun anEpisodeStoredUnderOneIdArchivesItsRowOnlyAndKeepsItsListening() = runTest {
        history.upsert(row("solo", progress = 300L, playedAt = 50L, dirty = false))

        assertEquals(1, store.setDismissed("solo", dismissed = true, nowMs = 1_000L))

        val archived = history.getHistoryItem("solo")!!
        assertEquals(1_000L, archived.dismissedAt)
        assertTrue(archived.isDismissed && archived.isDirty)
        assertEquals(300L to 50L, archived.progressMs to archived.lastPlayedAt)
        assertFalse(archived.isCompleted)
        assertEquals(setOf("solo"), store.dismissedIds())
        assertEquals(0, store.setDismissed("missing", dismissed = true, nowMs = 1_000L))

        store.setDismissed("solo", dismissed = false, nowMs = 2_000L)
        assertEquals(-2_000L, history.getHistoryItem("solo")!!.dismissedAt)
        assertTrue(store.dismissedIds().isEmpty())
    }

    @Test
    fun archivingAcrossTwoAliasesWritesEveryRowAndTheGroupSettings() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", progress = 100L, playedAt = 50L), row("B", playedAt = 40L)))

        assertEquals(2, store.setDismissed("B", dismissed = true, nowMs = 100L))

        assertTrue(history.getHistoryItem("A")!!.isDismissed)
        assertTrue(history.getHistoryItem("B")!!.isDismissed)
        assertEquals(100L, identity.getGroupState(SHOW, KEY)?.dismissedAt)
        assertTrue(store.projectedHistory("A")!!.isDismissed)
        assertEquals(setOf("A", "B"), store.dismissedIds())
        assertEquals(setOf("A", "B"), store.dismissedIdsFlow().first())
        // Progress and completion stay where they were.
        assertEquals(100L, store.projectedHistory("B")!!.progressMs)
        assertFalse(store.projectedHistory("B")!!.isCompleted)
    }

    @Test
    fun aClearedArchiveOnAnOldIdDoesNotComeBack() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", playedAt = 50L), row("B", playedAt = 40L)))
        store.setDismissed("A", dismissed = true, nowMs = 100L)
        store.setDismissed("A", dismissed = false, nowMs = 200L)
        assertEquals(200L, identity.getGroupState(SHOW, KEY)?.dismissClearedAt)

        // Another device's older copy of B, still archived, arrives again and again.
        repeat(3) {
            history.upsert(row("B", playedAt = 40L, dismissedAt = 150L, dirty = false))
            assertFalse(store.projectedHistory("A")!!.isDismissed)
            assertTrue(store.dismissedIds().isEmpty())
        }
        // A newer archive from that device still wins.
        history.upsert(row("B", playedAt = 40L, dismissedAt = 300L, dirty = false))
        assertTrue(store.projectedHistory("A")!!.isDismissed)
        assertEquals(setOf("A", "B"), store.dismissedIds())
    }

    @Test
    fun anArchiveOnlyTheGroupSettingsHoldStillCounts() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", playedAt = 50L), row("B", playedAt = 40L)))
        identity.upsertGroupState(EpisodeGroupStateEntity(podcastId = SHOW, catalogKey = KEY, dismissedAt = 500L))

        assertEquals(setOf("A", "B"), store.dismissedIds())
    }

    @Test
    fun aNewArchiveDecisionAlwaysWinsEvenWithAnEarlierClock() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", playedAt = 50L), row("B", playedAt = 40L)))
        store.setDismissed("A", dismissed = true, nowMs = 1_000L)

        // The clock went back: the clear is stamped just after the archive, so it still wins.
        store.setDismissed("A", dismissed = false, nowMs = 10L)

        assertFalse(store.projectedHistory("B")!!.isDismissed)
        assertEquals(1_001L, identity.getGroupState(SHOW, KEY)?.dismissClearedAt)
    }

    @Test
    fun manyEpisodesAtOnceReportThoseWithoutARow() = runTest {
        history.upsertAll(listOf(row("x", playedAt = 1L), row("y", playedAt = 2L)))

        val missing = store.setDismissed(listOf("x", "y", "z", "x"), dismissed = true, nowMs = 10L)

        assertEquals(listOf("z"), missing)
        assertEquals(setOf("x", "y"), store.dismissedIds())
    }

    @Test
    fun aBatchMatchesOneByOneAcrossEveryKindOfEpisode() = runTest {
        twoAliases()
        identity.insertAliasesIfAbsent(listOf(alias("S", owner = true).copy(catalogKey = "guid-s"), alias("K", owner = true).copy(catalogKey = "guid-k")))
        history.upsertAll(listOf(row("A", playedAt = 50L), row("B", playedAt = 40L), row("x", playedAt = 1L), row("S", playedAt = 1L, dismissedAt = -2_000L)))

        val missing = store.setDismissed(listOf("x", "z", "B", "S", "K"), dismissed = true, nowMs = 10L)

        // No row anywhere: z (no alias) and K (one trusted ID). The caller creates their rows.
        assertEquals(listOf("z", "K"), missing)
        assertEquals(setOf("A", "B", "x", "S"), store.dismissedIds())
        assertEquals(10L, history.getHistoryItem("x")!!.dismissedAt)
        // S was brought back at 2 000 by a faster clock: the new archive is stamped just after it.
        assertEquals(2_001L, history.getHistoryItem("S")!!.dismissedAt)
        assertEquals(2_001L, identity.getGroupState(SHOW, "guid-s")?.dismissedAt)
        assertEquals(10L, identity.getGroupState(SHOW, "guid-k")?.dismissedAt)
        assertTrue(identity.getGroupState(SHOW, KEY)!!.dismissedAt > 0L)
    }

    @Test
    fun aShowsArchivedCountCountsEachEpisodeOnceAndOnlyItsOwn() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", playedAt = 50L), row("B", playedAt = 40L), row("x"), row("other").copy(podcastId = "200")))
        store.setDismissed(listOf("A", "x", "other"), dismissed = true, nowMs = 10L)

        // A and B are one episode; "other" belongs to another show.
        assertEquals(2, store.dismissedEpisodeCount(store.dismissedIds(), setOf(SHOW)))
        assertEquals(0, store.dismissedEpisodeCount(emptySet(), setOf(SHOW)))
    }

    @Test
    fun clearingHistoryKeepsEveryArchiveDecisionAndNothingElse() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", progress = 60_000L, playedAt = 50L), row("B", playedAt = 40L), row("back", playedAt = 5L, dismissedAt = -7L)))
        store.setDismissed("A", dismissed = true, nowMs = 100L)

        val (rows, states) = store.archiveOnlyState()

        assertEquals(setOf("A", "B", "back"), rows.map { it.episodeId }.toSet())
        assertTrue(rows.all { it.progressMs == 0L && it.lastPlayedAt == 0L && !it.isDirty && !it.countsAsListening })
        assertEquals(listOf(100L), states.map { it.dismissedAt })
    }

    @Test
    fun anArchivedOnlyRowNeverCountsAsListening() {
        val archivedOnly = row("ep", dismissedAt = 10L)
        assertFalse(archivedOnly.countsAsListening)
        assertFalse(archivedOnly.copy(dismissedAt = -20L).countsAsListening)
        // Listening done before archiving still counts: archiving is not a statement about it.
        assertTrue(row("ep", progress = 60_000L, playedAt = 5L, dismissedAt = 10L).countsAsListening)
    }

    @Test
    fun continueListeningLeavesArchivedEpisodesOut() = runTest {
        history.upsertAll(listOf(row("started", progress = 10L, playedAt = 10L), row("archived", progress = 10L, playedAt = 20L)))
        store.setDismissed("archived", dismissed = true, nowMs = 30L)

        assertEquals(listOf("started"), history.getResumeItems().first().map { it.episodeId })
        assertEquals(listOf("started"), history.getResumeItemsList().map { it.episodeId })
    }

    private suspend fun twoAliases() {
        identity.insertAliasesIfAbsent(listOf(alias("A", owner = true, createdAt = 1L), alias("B", createdAt = 2L)))
    }

    private fun alias(id: String, owner: Boolean = false, createdAt: Long = 1L) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = KEY,
        recipeId = RECIPE,
        state = EpisodeAliasState.TRUSTED,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = createdAt,
    )

    private fun row(id: String, progress: Long = 0L, playedAt: Long = 0L, dismissedAt: Long = 0L, dirty: Boolean = true) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_000L,
        isCompleted = false,
        lastPlayedAt = playedAt,
        isDirty = dirty,
        dismissedAt = dismissedAt,
    )

    private companion object {
        const val SHOW = "100"
        const val KEY = "guid-a"
        val RECIPE = EpisodeRecipeId.forNamespace(SHOW.toLong(), KEY)
    }
}
