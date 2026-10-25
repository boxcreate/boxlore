package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [RoomDiscoveryEpisodeIds] through Room: stored IDs go out as their episode's recipe ID, and
 * recipe IDs in `/v2` answers come back as the stored ID that owns the episode on this phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDiscoveryEpisodeIdsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var ids: RoomDiscoveryEpisodeIds
    private val identity get() = database.episodeIdentityDao()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        ids = RoomDiscoveryEpisodeIds { database }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storedIdsGoOutAsTheRecipeIdOfTheirTrustedAlias() = runTest {
        identity.insertAliasesIfAbsent(
            listOf(
                // A Podcast Index ID an older build stored, and a phone-minted ID of a feed-only episode.
                alias("7777", key = "guid-a", recipe = RECIPE_A),
                alias("-55", key = "guid-b", recipe = RECIPE_B, provenance = EpisodeAliasProvenance.FEED_OWN_MINT),
                alias("-66", key = null, recipe = null, state = EpisodeAliasState.UNRESOLVED),
                alias("8888", key = "guid-c", recipe = RECIPE_C, state = EpisodeAliasState.AMBIGUOUS),
            ),
        )

        val out = ids.recipeIds(listOf("7777", "-55", "-66", "8888", RECIPE_D.toString(), "9999", ""))

        assertEquals(
            mapOf("7777" to RECIPE_A, "-55" to RECIPE_B, RECIPE_D.toString() to RECIPE_D),
            out,
        )
    }

    @Test
    fun anAliasRecipeIdWinsOverAStoredRecipeRangeIdThatDiffers() = runTest {
        identity.insertAliasesIfAbsent(listOf(alias(RECIPE_D.toString(), key = "guid-d", recipe = RECIPE_A)))

        assertEquals(mapOf(RECIPE_D.toString() to RECIPE_A), ids.recipeIds(listOf(RECIPE_D.toString())))
    }

    @Test
    fun aRecipeIdComesBackAsTheIdThatHoldsTheListening() = runTest {
        identity.insertAliasesIfAbsent(
            listOf(
                alias("7777", key = "guid-a", recipe = RECIPE_A, createdAt = 2L),
                alias(RECIPE_A.toString(), key = "guid-a", recipe = RECIPE_A, provenance = EpisodeAliasProvenance.FEED_RECIPE, createdAt = 1L),
            ),
        )
        database.listeningHistoryDao().upsertAll(listOf(history("7777", playedAt = 500L)))
        val ref = DiscoveryEpisodeRef(SHOW, RECIPE_A)

        assertEquals(mapOf(ref to "7777"), ids.localOwners(listOf(ref)))
    }

    @Test
    fun aRecipeIdHeldOnlyAsAStoredIdMapsToItsOwnEpisode() = runTest {
        identity.insertAliasesIfAbsent(listOf(alias(RECIPE_B.toString(), key = "guid-b", recipe = RECIPE_C)))
        val ref = DiscoveryEpisodeRef(SHOW, RECIPE_B)

        assertEquals(mapOf(ref to RECIPE_B.toString()), ids.localOwners(listOf(ref)))
    }

    @Test
    fun unknownCollidingAmbiguousAndOtherShowRecipeIdsAreLeftAsTheyAre() = runTest {
        identity.insertAliasesIfAbsent(
            listOf(
                // Two episodes of the show share one recipe ID: never guess between them.
                alias("1", key = "guid-x", recipe = RECIPE_A),
                alias("2", key = "guid-y", recipe = RECIPE_A),
                alias("3", key = "guid-z", recipe = RECIPE_B, state = EpisodeAliasState.AMBIGUOUS),
                alias("4", key = "guid-w", recipe = RECIPE_C, show = "555"),
            ),
        )
        val refs =
            listOf(
                DiscoveryEpisodeRef(SHOW, RECIPE_A),
                DiscoveryEpisodeRef(SHOW, RECIPE_B),
                DiscoveryEpisodeRef(SHOW, RECIPE_C),
                DiscoveryEpisodeRef(SHOW, RECIPE_D),
            )

        assertEquals(emptyMap<DiscoveryEpisodeRef, String>(), ids.localOwners(refs))
        assertEquals(emptyMap<DiscoveryEpisodeRef, String>(), ids.localOwners(emptyList()))
    }

    @Test
    fun translationIsAvailableThroughTheListHelpersInBothDirections() = runTest {
        identity.insertAliasesIfAbsent(listOf(alias("7777", key = "guid-a", recipe = RECIPE_A)))
        val episodes =
            listOf(
                episode(RECIPE_A.toString()),
                episode(RECIPE_B.toString()),
                // The same episode named twice (the second answer maps onto the first's owner).
                episode("7777"),
            )

        val mapped = ids.toLocalOwners(episodes)

        assertEquals(listOf("7777", RECIPE_B.toString()), mapped.map { it.id })
        assertEquals(mapOf("7777" to RECIPE_A), ids.recipeIdsOrOwn(listOf("7777")))
    }

    private fun alias(
        id: String,
        key: String?,
        recipe: Long?,
        state: String = EpisodeAliasState.TRUSTED,
        provenance: String = EpisodeAliasProvenance.UPGRADE_CATALOG,
        show: String = SHOW,
        createdAt: Long = 1L,
    ) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = show,
        catalogKey = key,
        recipeId = recipe,
        state = state,
        provenance = provenance,
        createdAt = createdAt,
    )

    private fun history(id: String, playedAt: Long) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = SHOW,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://cdn.example/$id.mp3",
        podcastName = "Show",
        progressMs = 100L,
        durationMs = 1_000L,
        isCompleted = false,
        lastPlayedAt = playedAt,
    )

    private fun episode(id: String) = cx.aswin.boxlore.core.model.Episode(id = id, title = "Episode", description = "", audioUrl = "", podcastId = SHOW)

    private companion object {
        const val SHOW = "920666"
        const val RECIPE_A = 4_503_599_627_370_497L
        const val RECIPE_B = 4_503_599_627_370_498L
        const val RECIPE_C = 4_503_599_627_370_499L
        const val RECIPE_D = 4_503_599_627_370_500L
    }
}
