package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
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

        assertEquals(mapOf(ref to DiscoveryEpisodeOwner("7777", SHOW)), ids.localOwners(listOf(ref)))
    }

    @Test
    fun aRecipeIdHeldOnlyAsAStoredIdMapsToItsOwnEpisode() = runTest {
        identity.insertAliasesIfAbsent(listOf(alias(RECIPE_B.toString(), key = "guid-b", recipe = RECIPE_C)))
        val ref = DiscoveryEpisodeRef(SHOW, RECIPE_B)

        assertEquals(mapOf(ref to DiscoveryEpisodeOwner(RECIPE_B.toString(), SHOW)), ids.localOwners(listOf(ref)))
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

        assertEquals(emptyMap<DiscoveryEpisodeRef, DiscoveryEpisodeOwner>(), ids.localOwners(refs))
        assertEquals(emptyMap<DiscoveryEpisodeRef, DiscoveryEpisodeOwner>(), ids.localOwners(emptyList()))
    }

    @Test
    fun anOwnerStoredUnderTheShowsOwnFeedComesBackWithThatShow() = runTest {
        // The listener switched the show to its feed: the episode's only trusted member is the feed's
        // negative ID, linked under the Podcast Index show, and its listening is stored under the feed's show.
        identity.insertAliasesIfAbsent(listOf(alias("-77", key = "guid-a", recipe = RECIPE_A, provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK)))
        database.listeningHistoryDao().upsertAll(listOf(history("-77", playedAt = 500L, show = "rss:abc")))
        val ref = DiscoveryEpisodeRef(SHOW, RECIPE_A)

        assertEquals(mapOf(ref to DiscoveryEpisodeOwner("-77", "rss:abc")), ids.localOwners(listOf(ref)))

        val mapped = ids.toLocalOwners(listOf(episode(RECIPE_A.toString()).copy(podcastTitle = "The Show")))
        assertEquals("-77", mapped.single().id)
        assertEquals("rss:abc", mapped.single().podcastId)
        assertEquals(RECIPE_A, mapped.single().recipeId)
    }

    @Test
    fun aPodcastIndexRowWithNoAliasIsMatchedByItsShowAndAudioAddress() = runTest {
        // History from discovery on an older build: no alias until the show's feed is read.
        database.listeningHistoryDao().upsertAll(listOf(history("5555", playedAt = 500L, audio = AUDIO_A)))
        database.queueDao().insertQueueItem(queued("4444", AUDIO_B))
        val played = DiscoveryEpisodeRef(SHOW, RECIPE_A, AUDIO_A)
        val queued = DiscoveryEpisodeRef(SHOW, RECIPE_B, AUDIO_B)
        val otherShow = DiscoveryEpisodeRef("555", RECIPE_C, AUDIO_A)
        val noAudio = DiscoveryEpisodeRef(SHOW, RECIPE_D)

        assertEquals(
            mapOf(played to DiscoveryEpisodeOwner("5555", SHOW), queued to DiscoveryEpisodeOwner("4444", SHOW)),
            ids.localOwners(listOf(played, queued, otherShow, noAudio)),
        )
        // Out: the row still has no recipe ID to send.
        assertEquals(emptyMap<String, Long>(), ids.recipeIds(listOf("5555")))
    }

    @Test
    fun anAudioAddressSeveralRowsShareOrARowAnAliasPlacesIsNotEvidence() = runTest {
        database.listeningHistoryDao().upsertAll(listOf(history("5555", playedAt = 500L, audio = AUDIO_A), history("5556", playedAt = 400L, audio = AUDIO_A)))
        database.listeningHistoryDao().upsertAll(listOf(history("6666", playedAt = 300L, audio = AUDIO_B)))
        identity.insertAliasesIfAbsent(listOf(alias("6666", key = "guid-other", recipe = RECIPE_C)))

        val refs = listOf(DiscoveryEpisodeRef(SHOW, RECIPE_A, AUDIO_A), DiscoveryEpisodeRef(SHOW, RECIPE_B, AUDIO_B))

        assertEquals(emptyMap<DiscoveryEpisodeRef, DiscoveryEpisodeOwner>(), ids.localOwners(refs))
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
        // Each answer keeps the recipe ID it named, translated or not.
        assertEquals(listOf(RECIPE_A, RECIPE_B), mapped.map { it.recipeId })
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

    private fun history(id: String, playedAt: Long, show: String = SHOW, audio: String = "https://cdn.example/$id.mp3",) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = audio,
        podcastName = "Show",
        progressMs = 100L,
        durationMs = 1_000L,
        isCompleted = false,
        lastPlayedAt = playedAt,
    )

    private fun queued(id: String, audio: String) = QueueItem(
        episodeId = id,
        title = "Queued",
        podcastId = SHOW,
        podcastTitle = "Show",
        imageUrl = null,
        audioUrl = audio,
        duration = 60,
        pubDate = 0L,
        description = null,
        position = 0,
    )

    private fun episode(id: String) = cx.aswin.boxlore.core.model.Episode(id = id, title = "Episode", description = "", audioUrl = "", podcastId = SHOW)

    private companion object {
        const val SHOW = "920666"
        const val RECIPE_A = 4_503_599_627_370_497L
        const val RECIPE_B = 4_503_599_627_370_498L
        const val RECIPE_C = 4_503_599_627_370_499L
        const val RECIPE_D = 4_503_599_627_370_500L
        const val AUDIO_A = "https://cdn.example/played.mp3"
        const val AUDIO_B = "https://cdn.example/queued.mp3"
    }
}
