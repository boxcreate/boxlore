package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The v39 alias, group-state and repair DAOs on a fresh install's schema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeIdentityDaoTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: EpisodeIdentityDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.episodeIdentityDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun alias(id: String, key: String? = "guid-a", recipe: Long? = RECIPE, owner: Boolean = false, createdAt: Long = 1L,) =
        EpisodeAliasEntity(
            aliasId = id,
            podcastId = SHOW,
            catalogKey = key,
            recipeId = recipe,
            state = if (key == null) EpisodeAliasState.UNRESOLVED else EpisodeAliasState.TRUSTED,
            owner = owner,
            provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
            createdAt = createdAt,
        )

    @Test
    fun insertingAnExistingAliasLeavesTheStoredRowAsItIs() = runTest {
        dao.insertAliasesIfAbsent(listOf(alias("-11", owner = true)))

        val results = dao.insertAliasesIfAbsent(listOf(alias("-11", key = "other"), alias("-12")))

        assertEquals(-1L, results[0])
        assertTrue(results[1] > 0)
        assertEquals("guid-a", dao.getAlias("-11")?.catalogKey)
        assertTrue(dao.getAlias("-11")!!.owner)
        assertEquals(listOf("-11", "-12"), dao.getAliases(listOf("-12", "-11", "-99")).map { it.aliasId }.sorted())
    }

    @Test
    fun aShowsAliasesListEveryStoredIdOfThatShowOnly() = runTest {
        dao.insertAliasesIfAbsent(listOf(alias("-12"), alias("-11", key = null), alias("77").copy(podcastId = "other-show")))

        assertEquals(listOf("-11", "-12"), dao.aliasesForShow(SHOW).map { it.aliasId })
        assertTrue(dao.aliasesForShow("no-show").isEmpty())
    }

    @Test
    fun anEpisodesIdsListTheOwnerFirstThenTheOldest() = runTest {
        dao.insertAliasesIfAbsent(
            listOf(alias("-13", createdAt = 5L), alias("-11", createdAt = 9L, owner = true), alias("-12", createdAt = 5L), alias("-20", key = "guid-b")),
        )

        assertEquals(listOf("-11", "-12", "-13"), dao.aliasesForKey(SHOW, "guid-a").map { it.aliasId })
        // Two keys share the recipe ID (a collision): both are listed so the caller can refuse to pick.
        assertEquals(listOf("-11", "-20", "-12", "-13"), dao.aliasesForRecipeId(SHOW, RECIPE).map { it.aliasId })
        assertTrue(dao.aliasesForKey("other-show", "guid-a").isEmpty())
    }

    @Test
    fun upsertingAnAliasReplacesItInPlace() = runTest {
        dao.insertAliasesIfAbsent(listOf(alias("-11", key = null, recipe = null)))
        assertEquals(listOf("-11"), dao.unresolvedAliases(SHOW).map { it.aliasId })

        dao.upsertAlias(alias("-11"))

        assertTrue(dao.unresolvedAliases(SHOW).isEmpty())
        assertEquals(EpisodeAliasState.TRUSTED, dao.getAlias("-11")?.state)
    }

    @Test
    fun groupStateRoundTrips() = runTest {
        assertNull(dao.getGroupState(SHOW, "guid-a"))
        val state = EpisodeGroupStateEntity(SHOW, "guid-a", likedAt = 10L, likeClearedAt = 20L, version = 2L)

        dao.upsertGroupState(state)
        dao.upsertGroupState(state.copy(completedAt = 30L, version = 3L))

        assertEquals(state.copy(completedAt = 30L, version = 3L), dao.getGroupState(SHOW, "guid-a"))
    }

    @Test
    fun repairProgressListsOnlyPendingShows() = runTest {
        val repair = database.upgradeRepairDao()
        repair.upsert(UpgradeRepairShowEntity("300"))
        repair.upsert(UpgradeRepairShowEntity("100"))
        repair.upsert(UpgradeRepairShowEntity("200", completedStep = 3, state = UpgradeRepairState.DONE))
        assertEquals(listOf("100", "300"), repair.pendingShows().map { it.podcastId })

        repair.upsert(UpgradeRepairShowEntity("100", completedStep = 1, attempts = 1, checkedFetchUrl = "https://new.example/feed"))

        assertEquals(2, repair.pendingCount())
        assertEquals("https://new.example/feed", repair.getShow("100")?.checkedFetchUrl)
        assertNull(repair.getShow("400"))
    }

    @Test
    fun freshRowsStartWithoutTheUpgradeMarks() = runTest {
        val catalog = database.localEpisodeCatalogDao()
        catalog.upsertEpisodes(
            listOf(
                LocalEpisodeEntity(
                    "-1", SHOW, "guid-a", "t", "d", "https://cdn.example/a.mp3", null, 1, 2L,
                    null, null, null, null, null, null, null, null,
                ),
            ),
        )
        assertFalse(catalog.getEpisode("-1")!!.legacy)
        assertTrue(database.upgradeRepairDao().pendingShows().isEmpty())
    }

    private companion object {
        const val SHOW = "100"
        const val RECIPE = 4_503_599_627_370_500L
    }
}
