package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * One listener state per episode across its stored IDs, through Room (docs/EPISODE_IDS.md §5).
 * Every show today stores each episode under one ID; those IDs must read and write exactly as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeListenerStoreTest {
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
    fun idsWithoutASharedAliasReadAndWriteExactlyAsBefore() = runTest {
        identity.insertAliasesIfAbsent(
            listOf(
                alias("2", key = null, state = EpisodeAliasState.UNRESOLVED),
                alias("3", state = EpisodeAliasState.AMBIGUOUS),
                alias("4", key = "guid-4", owner = true),
            ),
        )
        val rows = listOf("-1", "2", "3", "4").mapIndexed { index, id ->
            row(id, progress = 10L * index, liked = index % 2 == 0, likedAt = 5L, completed = index == 3, playedAt = 100L - index)
        }
        history.upsertAll(rows)

        for (row in rows) {
            assertEquals(row, store.projectedHistory(row.episodeId))
            assertSame(row, store.projectedHistory(row.episodeId, row))
            assertEquals(row.episodeId, store.historyId(row.episodeId))
            assertEquals(listOf(row.episodeId), store.memberIds(row.episodeId))
            assertNull(store.downloadHolder(row.episodeId))
        }
        assertSame(rows, store.projectRows(rows))
        assertEquals(rows.associate { it.episodeId to it.episodeId }, store.groupTokens(rows.map { it.episodeId }))
        assertEquals(setOf("4"), store.projectCompletedIds(listOf("4")))
        assertEquals(emptyMap<String, String>(), store.downloadHolders(rows.map { it.episodeId }))
        assertEquals(rows, store.projectedRows(history.getAllHistory()).first().sortedBy { it.episodeId })

        // The like write is the old single-row write: same flag, same time, marked for sync.
        assertEquals(1, store.setLiked("3", liked = true, nowMs = 777L))
        val written = history.getHistoryItem("3")!!
        assertEquals(rows[2].copy(isLiked = true, likedAt = 777L, isDirty = true), written)
        assertEquals(0, store.setLiked("missing", liked = true, nowMs = 777L))
        // Only an ID with a trusted alias records group settings, for an alias found later.
        assertNull(identity.getGroupState(SHOW, KEY))
        store.setLiked("4", liked = false, nowMs = 800L)
        assertEquals(800L, identity.getGroupState(SHOW, "guid-4")?.likeClearedAt)
        assertEquals(store.projectedHistory("4"), history.getHistoryItem("4"))
    }

    @Test
    fun clearingALikeAcrossTwoAliasesClearsEveryRowAndRecordsIt() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", liked = true, likedAt = 10L, playedAt = 50L), row("B", liked = true, likedAt = 20L, playedAt = 40L)))
        assertTrue(store.projectedHistory("B")!!.isLiked)

        assertEquals(2, store.setLiked("B", liked = false, nowMs = 100L))

        assertFalse(history.getHistoryItem("A")!!.isLiked)
        assertFalse(history.getHistoryItem("B")!!.isLiked)
        assertEquals(100L, history.getHistoryItem("B")!!.likedAt)
        assertTrue(history.getHistoryItem("A")!!.isDirty)
        assertEquals(100L, identity.getGroupState(SHOW, KEY)?.likeClearedAt)
        assertFalse(store.projectedHistory("A")!!.isLiked)
        assertTrue(store.projectRows(history.getLikedEpisodesList()) { it.isLiked }.isEmpty())
    }

    @Test
    fun clearingACompletionAcrossTwoAliasesClearsEveryRowAndRecordsIt() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", completed = true, playedAt = 50L), row("B", completed = true, playedAt = 40L, manual = true)))
        assertEquals(setOf("A", "B"), store.projectCompletedIds(listOf("A", "B")))

        // Playback reopens the owner's row (a replay); the store carries the clear to the other IDs.
        assertEquals("A", store.historyId("B"))
        history.reopenProgress("A", progressMs = 5L, durationMs = 1_000L, lastPlayedAt = 60L)
        store.recordCompletion("A", completed = false, nowMs = 60L)

        val other = history.getHistoryItem("B")!!
        assertFalse(other.isCompleted)
        assertFalse(other.isManualCompletion)
        assertTrue(identity.getGroupState(SHOW, KEY)!!.completeClearedAt > 0L)
        assertFalse(store.projectedHistory("B")!!.isCompleted)
        assertEquals(emptySet<String>(), store.projectCompletedIds(history.getCompletedEpisodeIds()))

        // Marking it played again reaches every ID.
        history.completeFromPlayback("A", durationMs = 1_000L, lastPlayedAt = 70L, isManualCompletion = true)
        store.recordCompletion("A", completed = true, nowMs = 70L)
        assertTrue(history.getHistoryItem("B")!!.isCompleted)
        assertEquals(setOf("A", "B"), store.projectCompletedIds(history.getCompletedEpisodeIds()))
    }

    @Test
    fun repeatedPullsOfAnOlderAliasRowCannotBringAClearedLikeOrCompletionBack() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", liked = true, likedAt = 10L, completed = true, playedAt = 50L), row("B", playedAt = 40L)))
        store.setLiked("A", liked = false, nowMs = 100L)
        history.reopenProgress("A", progressMs = 0L, durationMs = 1_000L, lastPlayedAt = 50L)
        store.recordCompletion("A", completed = false, nowMs = 110L)

        // Another device's older copy of B arrives again and again (a full pull writes it as is).
        repeat(3) {
            history.upsert(row("B", liked = true, likedAt = 90L, completed = true, playedAt = 105L, dirty = false))
            val projected = store.projectedHistory("A")!!
            assertFalse(projected.isLiked)
            assertFalse(projected.isCompleted)
            assertEquals(1, store.projectRows(history.getRecentHistoryList(10)).size)
        }
        // A newer decision from that device still wins.
        history.upsert(row("B", liked = true, likedAt = 200L, playedAt = 105L, dirty = false))
        assertTrue(store.projectedHistory("A")!!.isLiked)
    }

    @Test
    fun anAliasFoundLaterJoinsTheEpisodeWithoutUndoingItsSettings() = runTest {
        identity.insertAliasesIfAbsent(listOf(alias("A", owner = true, createdAt = 1L)))
        history.upsert(row("A", progress = 300L, liked = true, likedAt = 10L, playedAt = 200L))
        store.setLiked("A", liked = false, nowMs = 100L)
        assertEquals(listOf("A"), store.memberIds("A"))

        // A feed read later proves B is the same episode; B carries an old like and completion.
        history.upsert(row("B", progress = 900L, liked = true, likedAt = 50L, completed = true, playedAt = 150L))
        identity.insertAliasesIfAbsent(listOf(alias("B", createdAt = 2L)))

        assertEquals(listOf("A", "B"), store.memberIds("B"))
        val projected = store.projectedHistory("B")!!
        assertEquals("A", projected.episodeId)
        assertEquals(300L, projected.progressMs)
        assertFalse(projected.isLiked)
        assertFalse(projected.isCompleted)
        assertEquals("A", store.historyId("B"))
        assertTrue(identity.getAlias("A")!!.owner)
    }

    @Test
    fun ownershipMovesOnlyWhenTheOwnerHoldsNoState() = runTest {
        identity.insertAliasesIfAbsent(
            listOf(alias(SERVED, recipe = SERVED.toLong(), owner = true, createdAt = 1L), alias("B", createdAt = 2L)),
        )
        history.upsert(row("B", progress = 40L, playedAt = 10L))

        assertEquals("B", store.historyId(SERVED))
        assertTrue(identity.getAlias("B")!!.owner)
        assertFalse(identity.getAlias(SERVED)!!.owner)

        // The owner now holds state, so newer listening under another ID doesn't move it.
        history.upsert(row(SERVED, progress = 70L, playedAt = 99L))
        assertEquals("B", store.historyId(SERVED))
        assertEquals("B", store.group(SERVED).ownerId)
    }

    @Test
    fun anOwnerThatAlreadyHoldsAServedRecipeIdKeepsTheEpisode() = runTest {
        identity.insertAliasesIfAbsent(
            listOf(alias(SERVED, recipe = SERVED.toLong(), owner = true, createdAt = 5L), alias("777", createdAt = 1L)),
        )
        history.upsert(row(SERVED, progress = 120L, playedAt = 50L))
        history.upsert(row("777", progress = 999L, playedAt = 10L))

        assertEquals(SERVED, store.historyId("777"))
        assertEquals(120L, store.projectedHistory("777")!!.progressMs)
        // A newer copy of the other ID (a pull) is read until playback writes the owner again.
        history.upsert(row("777", progress = 999L, playedAt = 80L))
        assertEquals(SERVED, store.historyId("777"))
        assertEquals(999L, store.projectedHistory(SERVED)!!.progressMs)
        history.updateProgress(SERVED, progressMs = 905L, durationMs = 1_000L, lastPlayedAt = 90L)
        assertEquals(905L, store.projectedHistory("777")!!.progressMs)

        // With no listening anywhere the served ID wins over the older ID; a completed download wins over both.
        history.deleteAll()
        identity.setOwner(SHOW, KEY, "none")
        assertEquals(SERVED, identity.chooseOwner(SHOW, KEY))
        database.downloadedEpisodeDao().insert(download("777"))
        assertEquals("777", identity.chooseOwner(SHOW, KEY))
    }

    @Test
    fun playbackReadsTheDownloadOfWhicheverAliasHoldsIt() = runTest {
        twoAliases()
        history.upsert(row("A", progress = 300L, playedAt = 50L))
        database.downloadedEpisodeDao().insert(download("B"))

        assertEquals("A", store.historyId("B"))
        assertEquals("B", store.downloadHolder("A"))
        assertEquals("B", store.downloadHolder("B"))
        assertEquals(mapOf("A" to "B"), store.downloadHolders(listOf("A", "B", "other")))

        database.downloadedEpisodeDao().insert(download("A"))
        assertEquals("A", store.downloadHolder("A"))
        assertEquals(emptyMap<String, String>(), store.downloadHolders(listOf("A", "B")))
    }

    @Test
    fun listsAndTokensCountAnEpisodeOnce() = runTest {
        twoAliases()
        val rows = listOf(row("B", progress = 10L, playedAt = 90L), row("C", playedAt = 80L), row("A", progress = 300L, playedAt = 70L))
        history.upsertAll(rows)

        val projected = store.projectRows(rows)
        assertEquals(listOf("A", "C"), projected.map { it.episodeId })
        // B was played after the owner A, so the episode reads B's progress under A's row.
        assertEquals(10L, projected.first().progressMs)
        assertEquals(listOf("C"), store.projectRows(rows) { it.progressMs == 0L }.map { it.episodeId })
        assertEquals(listOf("A", "C"), store.projectedRows(history.getAllHistory()).first().map { it.episodeId })
        // Lookups find the episode under each of its IDs, once per ID.
        val byMember = store.projectRowsByMember(rows)
        assertEquals(listOf("A", "B", "C"), byMember.map { it.episodeId })
        assertEquals(byMember[0].copy(episodeId = "B"), byMember[1])
        assertEquals(listOf("A", "B", "C"), store.projectedRowsByMember(history.getAllHistory()).first().map { it.episodeId })

        val tokens = store.groupTokens(listOf("A", "B", "C"))
        assertEquals(tokens["A"], tokens["B"])
        assertNotEquals(tokens["A"], tokens["C"])
        assertEquals("C", tokens["C"])
        assertEquals(setOf("A", "B", "C"), store.withAllAliases(listOf("B", "C")))
    }

    @Test
    fun anOwnerThatHoldsOnlyALikeKeepsOwnershipButNeverHidesTheProgressOfAnotherId() = runTest {
        twoAliases()
        identity.setOwner(SHOW, KEY, "none")
        history.upsertAll(listOf(row("A", liked = true, likedAt = 900L), row("B", progress = 400L, playedAt = 300L)))

        // The upgrade's order puts the recent like first, so A owns the episode...
        assertEquals("A", store.historyId("B"))
        // ...and the episode still resumes from B's progress and stays in Continue listening.
        val projected = store.projectedHistory("A")!!
        assertEquals(400L, projected.progressMs)
        assertTrue(projected.isLiked)
        assertEquals(listOf("A"), store.projectRows(history.getResumeItemsList()) { !it.isCompleted && it.progressMs > 0 }.map { it.episodeId })
        assertEquals(400L, store.lastPlayedSession(unfinishedOnly = true)!!.progressMs)
    }

    @Test
    fun aCompletionOnlyTheGroupSettingsHoldCountsInCompletedIds() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", playedAt = 10L), row("B", playedAt = 20L)))
        identity.upsertGroupState(EpisodeGroupStateEntity(SHOW, KEY, completedAt = 30L))

        assertTrue(store.projectedHistory("A")!!.isCompleted)
        assertEquals(setOf("A", "B"), store.projectCompletedIds(history.getCompletedEpisodeIds()))
        assertEquals(setOf("A", "B"), store.projectedCompletedIds(history.getCompletedEpisodeIdsFlow()).first())
        // A clear recorded later takes it out again.
        identity.upsertGroupState(EpisodeGroupStateEntity(SHOW, KEY, completedAt = 30L, completeClearedAt = 40L))
        assertEquals(emptySet<String>(), store.projectCompletedIds(history.getCompletedEpisodeIds()))
    }

    @Test
    fun aLikeOfAnEpisodeStoredUnderOneIdKeepsTheClockTimeOnItsRow() = runTest {
        identity.insertAliasesIfAbsent(listOf(alias("solo", owner = true)))
        // A like time from a device whose clock ran ahead is stored on the row.
        history.upsert(row("solo", liked = true, likedAt = 5_000L))

        assertEquals(1, store.setLiked("solo", liked = false, nowMs = 1_000L))

        val written = history.getHistoryItem("solo")!!
        assertFalse(written.isLiked)
        assertEquals(1_000L, written.likedAt)
        // The group settings still record the clear after every recorded decision, for an alias found later.
        assertEquals(5_001L, identity.getGroupState(SHOW, KEY)!!.likeClearedAt)
        assertEquals(written, store.projectedHistory("solo"))
    }

    @Test
    fun likedIdsListEveryIdOfALikedEpisode() = runTest {
        twoAliases()
        history.upsertAll(listOf(row("A", liked = true, likedAt = 10L, playedAt = 5L), row("B", playedAt = 4L), row("C", liked = true, likedAt = 3L)))

        assertEquals(setOf("A", "B", "C"), store.projectedLikedIds(history.getLikedEpisodes()).first())
        store.setLiked("B", liked = false, nowMs = 100L)
        assertEquals(setOf("C"), store.projectedLikedIds(history.getLikedEpisodes()).first())
    }

    @Test
    fun batchedOwnersAndCompletionsMatchOneByOneAndSkipEpisodesWithoutRows() = runTest {
        twoAliases()
        identity.insertAliasesIfAbsent(listOf(alias("single", key = "guid-single", owner = true), alias("no-row", key = "guid-no-row", owner = true)))
        history.upsertAll(listOf(row("A", progress = 5L, playedAt = 50L), row("B", playedAt = 40L), row("single", completed = true, playedAt = 60L)))

        assertEquals(mapOf("B" to "A", "single" to "single", "no-row" to "no-row", "-3" to "-3"), store.historyIds(listOf("B", "single", "no-row", "-3")))

        store.recordCompletions(listOf("A", "single", "no-row", "-3"), completed = true, nowMs = 60L)

        assertTrue(history.getHistoryItem("B")!!.isCompleted)
        assertEquals(60L, identity.getGroupState(SHOW, KEY)!!.completedAt)
        // The single ID's row was played at the same time, so its decision is stamped just after it.
        assertEquals(61L, identity.getGroupState(SHOW, "guid-single")!!.completedAt)
        assertNull(identity.getGroupState(SHOW, "guid-no-row"))
    }

    @Test
    fun groupSettingsAreRemovedWithAnEpisodesHistoryAndPutBackByUndo() = runTest {
        twoAliases()
        store.setLiked("A", liked = true, nowMs = 10L)
        identity.upsertGroupState(EpisodeGroupStateEntity(SHOW, "other", completedAt = 5L))
        val before = identity.getGroupState(SHOW, KEY)!!

        val removed = store.removeGroupState("B")!!
        assertEquals(before, removed)
        assertNull(identity.getGroupState(SHOW, KEY))
        assertNull(store.removeGroupState("-1"))

        store.restoreGroupState(removed)
        assertEquals(before, identity.getGroupState(SHOW, KEY))

        store.clearGroupStates()
        assertNull(identity.getGroupState(SHOW, KEY))
        assertNull(identity.getGroupState(SHOW, "other"))
        assertEquals(listOf("A", "B"), store.memberIds("B"))
    }

    @Test
    fun theLastPlayedSessionIsProjectedAndSkipsAnEpisodeFinishedUnderAnotherId() = runTest {
        twoAliases()
        history.upsertAll(
            listOf(
                row("B", progress = 70L, playedAt = 90L),
                row("A", completed = true, playedAt = 95L),
                row("solo", progress = 5L, playedAt = 80L),
            ),
        )
        // B's own row is the latest unfinished one, but its episode was finished later under A.
        assertEquals("B", history.getLastPlayedSession()!!.episodeId)
        assertEquals("solo", store.lastPlayedSession(unfinishedOnly = true)!!.episodeId)
        val any = store.lastPlayedSession(unfinishedOnly = false)!!
        assertEquals("A", any.episodeId)
        assertTrue(any.isCompleted)
    }

    @Test
    fun queueRemovalsCarryTheEpisodeIdentityOfTrustedIds() = runTest {
        twoAliases()
        identity.insertAliasesIfAbsent(listOf(alias("U", key = null, state = EpisodeAliasState.UNRESOLVED)))

        assertEquals(
            listOf(
                QueueRemovalIdentity("A", RECIPE, SHOW, KEY),
                QueueRemovalIdentity("U"),
                QueueRemovalIdentity("-5"),
            ),
            store.queueIdentities(listOf("A", "U", "-5", "", "A")),
        )
    }

    private suspend fun twoAliases() {
        identity.insertAliasesIfAbsent(listOf(alias("A", owner = true, createdAt = 1L), alias("B", createdAt = 2L)))
    }

    private fun alias(
        id: String,
        key: String? = KEY,
        state: String = EpisodeAliasState.TRUSTED,
        recipe: Long? = RECIPE,
        owner: Boolean = false,
        createdAt: Long = 1L,
    ) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = SHOW,
        catalogKey = key,
        recipeId = if (key == null) null else recipe,
        state = state,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = createdAt,
    )

    @Suppress("LongParameterList")
    private fun row(
        id: String,
        progress: Long = 0L,
        liked: Boolean = false,
        likedAt: Long = 0L,
        completed: Boolean = false,
        playedAt: Long = 0L,
        manual: Boolean = false,
        dirty: Boolean = true,
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 1_000L,
        isCompleted = completed,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
        isDirty = dirty,
        isManualCompletion = manual,
    )

    private fun download(id: String) = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeDescription = null,
        episodeImageUrl = null,
        podcastName = "Show",
        podcastImageUrl = null,
        durationMs = 1_000L,
        publishedDate = 1L,
        localFilePath = "",
        downloadId = 0L,
        downloadedAt = 1L,
        sizeBytes = 1L,
        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
    )

    private companion object {
        const val SHOW = "100"
        const val KEY = "guid-a"
        val RECIPE = EpisodeRecipeId.forNamespace(SHOW.toLong(), KEY)
        val SERVED = RECIPE.toString()
    }
}
