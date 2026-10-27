package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A show Podcast Index renumbered, with the shape of the data found on a listener's phone: the old
 * show's history (most of it completed) and its unresolved v39 aliases, no catalog rows under the
 * old show, and the new show's catalog read under recipe IDs with trusted aliases. Linking makes the
 * new show's episodes read the old rows' completion, through real Room and the real listener store,
 * and no stored ID or row changes (docs/EPISODE_IDS.md §5, renumbered shows).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenumberedShowRoomTest : RoomCatalogTestBase() {
    private val shows = MemoryRenumberedShows()

    private fun renumberedRepo() = LocalEpisodeCatalogRepository(database, feed, DownloadCacheRelinker { _, _ -> false }, {}, RecipeIdHash.RECIPE, shows)

    /** The phone's data before the fix: the old show's listening, the new show's catalog. */
    private suspend fun seedPhoneShape() {
        follow(OLD, epNs = null)
        follow(NEW)
        for ((index, id) in OLD_IDS.withIndex()) {
            listened(id, OLD, audio = audioOf(index), completed = index < COMPLETED)
            seedUnresolved(id, OLD, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        }
        feed.items = OLD_IDS.indices.map { Item("guid-$it", title = "Episode $it", audio = audioOf(it)) } + Item("guid-new", title = "New episode")
        // The new show's catalog was read before the renumbering was known.
        assertTrue(refresh(NEW) is RefreshOutcome.Success)
    }

    @Test
    fun theNewShowsEpisodesReadTheOldShowsCompletionWithoutAnyIdChange() = runBlocking {
        seedPhoneShape()
        val catalogBefore = rows(NEW)
        val historyBefore = historyRows()
        assertTrue(shows.record(OLD, NEW))

        assertEquals(OLD_IDS.size, renumberedRepo().linkRenumberedShow(OLD, NEW))

        assertEquals("catalog rows keep their recipe IDs", catalogBefore, rows(NEW))
        assertEquals("history rows keep their IDs, show and state", historyBefore, historyRows())
        for ((index, id) in OLD_IDS.withIndex()) {
            val alias = requireNotNull(alias(id))
            assertEquals(NEW, alias.podcastId)
            assertEquals("guid-$index", alias.catalogKey)
            assertEquals(EpisodeAliasState.TRUSTED, alias.state)
            assertEquals(EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH, alias.provenance)
            assertFalse("joins as a non-owner", alias.owner)
            assertEquals(recipe(NEW, "guid-$index").toLong(), alias.recipeId)
        }
        val completed = database.episodeListenerStore().projectCompletedIds(OLD_IDS.take(COMPLETED))
        for (index in 0 until COMPLETED) {
            assertTrue("episode $index reads completed on the new show", recipe(NEW, "guid-$index") in completed)
        }
        assertFalse("an unfinished episode stays unfinished", recipe(NEW, "guid-${OLD_IDS.size - 1}") in completed)
        assertFalse(recipe(NEW, "guid-new") in completed)
        assertEquals("linking again changes nothing", 0, renumberedRepo().linkRenumberedShow(OLD, NEW))
    }

    @Test
    fun eachCatalogReadOfTheNewShowLinksRowsThatArrivedSince() = runBlocking {
        seedPhoneShape()
        shows.record(OLD, NEW)
        renumberedRepo().linkRenumberedShow(OLD, NEW)
        // A cloud pull brings another old-show row after the first link.
        val late = "41000000099"
        feed.items = feed.items + Item("guid-late", title = "Late", audio = "https://sphinx.example/late.mp3")
        listened(late, OLD, audio = "https://sphinx.example/late.mp3", completed = true)

        assertTrue(refresh(NEW, repo = renumberedRepo()) is RefreshOutcome.Success)

        assertEquals(NEW, alias(late)?.podcastId)
        assertEquals("guid-late", alias(late)?.catalogKey)
        assertTrue(recipe(NEW, "guid-late") in database.episodeListenerStore().projectCompletedIds(listOf(late)))
        assertEquals(OLD, database.listeningHistoryDao().getHistoryItem(late)?.podcastId)
    }

    @Test
    fun onlyExactUniqueEvidenceLinksAndSettledAliasesAreNeverRewritten() = runBlocking {
        follow(OLD, epNs = null)
        follow(NEW)
        follow(LOOKALIKE)
        // No audio match; a match two old IDs claim; a trusted alias of the old show; a lookalike show's row.
        listened("41000000010", OLD, audio = "https://elsewhere.example/a.mp3", completed = true)
        seedUnresolved("41000000010", OLD, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        listened("41000000011", OLD, audio = SHARED, completed = true)
        listened("41000000012", OLD, audio = SHARED, completed = true)
        listened("41000000013", OLD, audio = audioOf(1), completed = true)
        seedAlias("41000000013", OLD, "guid-old-key")
        listened("41000000014", LOOKALIKE, audio = audioOf(2), completed = true)
        feed.items = listOf(Item("guid-shared", audio = SHARED), Item("guid-1", audio = audioOf(1)), Item("guid-2", audio = audioOf(2)))
        assertTrue(refresh(NEW) is RefreshOutcome.Success)
        shows.record(OLD, NEW)

        assertEquals(0, renumberedRepo().linkRenumberedShow(OLD, NEW))

        assertEquals(EpisodeAliasState.UNRESOLVED, alias("41000000010")?.state)
        assertEquals(null, alias("41000000011"))
        assertEquals(null, alias("41000000012"))
        assertEquals("a trusted alias keeps its show and key", OLD, alias("41000000013")?.podcastId)
        assertEquals("guid-old-key", alias("41000000013")?.catalogKey)
        assertEquals("another show is never linked, whatever its title", null, alias("41000000014"))
    }

    /**
     * The shape of any show followed since episodes are keyed: the old show's catalog was read under
     * its recipe IDs and the listening is under them, trusted under the old show. Once its follow
     * ended and the new show's catalog was read, linking joins each old episode to the new episode
     * with its key: completion and likes read on the new show and no stored ID changes.
     */
    @Test
    fun episodesOfTheOldShowsCatalogJoinTheNewShowsEpisodesWithoutAnyIdChange() = runBlocking {
        val keys = OLD_IDS.indices.map { "guid-$it" }
        feed.items = keys.mapIndexed { index, key -> Item(key, title = "Episode $index", audio = audioOf(index)) }
        follow(OLD)
        assertTrue(refresh(OLD) is RefreshOutcome.Success)
        val oldIds = keys.map { recipe(OLD, it) }
        assertEquals(oldIds, keys.map { rows(OLD)[it] })
        for ((index, id) in oldIds.withIndex()) listened(id, OLD, audio = audioOf(index), completed = index < COMPLETED)
        val liked = EpisodeGroupStateEntity(podcastId = OLD, catalogKey = keys[0], likedAt = 7_000L, version = 1L)
        database.episodeIdentityDao().upsertGroupState(liked)
        // The follow moved to the new show, whose catalog was then read under its own recipe IDs.
        database.podcastDao().upsert(database.podcastDao().getPodcast(OLD)!!.copy(isSubscribed = false))
        follow(NEW)
        assertTrue(refresh(NEW) is RefreshOutcome.Success)
        val catalogBefore = rows(NEW)
        val historyBefore = oldIds.mapNotNull { database.listeningHistoryDao().getHistoryItem(it) }
        shows.record(OLD, NEW)

        assertEquals(oldIds.size, renumberedRepo().linkRenumberedShow(OLD, NEW))

        assertEquals("the new show's rows keep their recipe IDs", catalogBefore, rows(NEW))
        assertEquals("history keeps its IDs, show and state", historyBefore, oldIds.mapNotNull { database.listeningHistoryDao().getHistoryItem(it) })
        assertTrue("the ended show's copy is dropped, as its countdown would", rows(OLD).isEmpty())
        for ((index, id) in oldIds.withIndex()) {
            val alias = requireNotNull(alias(id))
            assertEquals(NEW, alias.podcastId)
            assertEquals(keys[index], alias.catalogKey)
            assertEquals(recipe(NEW, keys[index]).toLong(), alias.recipeId)
            assertEquals(EpisodeAliasState.TRUSTED, alias.state)
            assertEquals(EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH, alias.provenance)
            assertFalse(alias.owner)
            assertTrue(id in database.episodeListenerStore().memberIds(recipe(NEW, keys[index])))
        }
        val completed = database.episodeListenerStore().projectCompletedIds(oldIds.take(COMPLETED))
        for (index in 0 until COMPLETED) assertTrue("episode $index reads completed on the new show", recipe(NEW, keys[index]) in completed)
        assertFalse(recipe(NEW, keys.last()) in completed)
        assertEquals("the like decision moves with the episode", 7_000L, database.episodeIdentityDao().getGroupState(NEW, keys[0])?.likedAt)
        assertNull(database.episodeIdentityDao().getGroupState(OLD, keys[0]))
        assertEquals("linking again changes nothing", 0, renumberedRepo().linkRenumberedShow(OLD, NEW))
    }

    @Test
    fun aStillFollowedOldShowKeepsItsCatalogAndItsEpisodes() = runBlocking {
        feed.items = listOf(Item("guid-0", audio = audioOf(0)))
        follow(OLD)
        assertTrue(refresh(OLD) is RefreshOutcome.Success)
        val oldId = recipe(OLD, "guid-0")
        listened(oldId, OLD, audio = audioOf(0), completed = true)
        follow(NEW)
        assertTrue(refresh(NEW) is RefreshOutcome.Success)
        shows.record(OLD, NEW)

        assertEquals(0, renumberedRepo().linkRenumberedShow(OLD, NEW))

        assertEquals(OLD, alias(oldId)?.podcastId)
        assertEquals(mapOf("guid-0" to oldId), rows(OLD))
    }

    @Test
    fun withoutARecordedRenumberingACatalogReadLinksNothing() = runBlocking {
        seedPhoneShape()

        assertTrue(refresh(NEW, repo = renumberedRepo()) is RefreshOutcome.Success)

        for (id in OLD_IDS) assertEquals(EpisodeAliasState.UNRESOLVED, alias(id)?.state)
    }

    private suspend fun listened(id: String, show: String, audio: String, completed: Boolean) {
        database.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = id,
                podcastId = show,
                episodeTitle = "Old title $id",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = audio,
                podcastName = "The Desi Crime Podcast",
                progressMs = if (completed) 60_000L else 5_000L,
                durationMs = 60_000L,
                isCompleted = completed,
                lastPlayedAt = 1_000L + id.takeLast(3).toLong(),
            ),
        )
    }

    private suspend fun historyRows(): List<ListeningHistoryEntity> =
        OLD_IDS.mapNotNull { database.listeningHistoryDao().getHistoryItem(it) }

    private companion object {
        const val OLD = "1258562"
        const val NEW = "7976624"
        const val LOOKALIKE = "5200907"
        const val COMPLETED = 3
        const val SHARED = "https://sphinx.example/shared.mp3"
        val OLD_IDS = listOf("41000000001", "41000000002", "41000000003", "41000000004")

        fun audioOf(index: Int) = "https://sphinx.example/e/$index/media.mp3"
    }
}
