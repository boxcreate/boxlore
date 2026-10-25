package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity.IncomingRow
import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity.OutgoingRow
import cx.aswin.boxlore.core.catalog.sync.SyncEpisodeIdentity.WireIdentity
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.KEY
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.KEY_2
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.PI_ID
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.RECIPE
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.RECIPE_2
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.SHOW
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.alias
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.catalogRow
import cx.aswin.boxlore.core.catalog.sync.SyncIdentityFixtures.history
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroup
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncPullResponse
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

/** [SyncEpisodeIdentity]: which identity a pushed row carries, and which aliases a pull records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEpisodeIdentityTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var identity: SyncEpisodeIdentity

    @Before
    fun setUp() {
        database = SyncIdentityFixtures.directDatabase(ApplicationProvider.getApplicationContext<Context>())
        identity = SyncEpisodeIdentity(database, nowMillis = { 500L })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun aliases(vararg rows: EpisodeAliasEntity) {
        database.episodeIdentityDao().insertAliasesIfAbsent(rows.toList())
    }

    private suspend fun storedAlias(id: String) = database.episodeIdentityDao().getAlias(id)

    private suspend fun out(id: String, show: String = SHOW): WireIdentity = identity.outgoing(listOf(OutgoingRow(id, show))).getValue(OutgoingRow(id, show))

    @Test
    fun aTrustedIdCarriesItsEpisodesRecipeIdAndKeyWhateverIdItIs() = runTest {
        aliases(alias(RECIPE.toString(), owner = true), alias(PI_ID))

        assertEquals(WireIdentity(RECIPE, KEY), out(RECIPE.toString()))
        assertEquals(WireIdentity(RECIPE, KEY), out(PI_ID))
    }

    @Test
    fun rowsWithoutATrustedIdentityUnderTheirOwnShowCarryNone() = runTest {
        aliases(
            alias("-11", state = EpisodeAliasState.UNRESOLVED, key = null),
            alias("-12", state = EpisodeAliasState.AMBIGUOUS),
            // An RSS episode linked to the Podcast Index show it replaced: its row is stored under the RSS show.
            alias("-13", provenance = EpisodeAliasProvenance.RSS_SOURCE_LINK),
        )

        assertEquals(WireIdentity.NONE, out("-11"))
        assertEquals(WireIdentity.NONE, out("-12"))
        assertEquals(WireIdentity.NONE, out("-13", show = "rss:abc"))
        assertEquals(WireIdentity.NONE, out("no-alias"))
        assertEquals(WireIdentity.NONE, out("briefing_us_2026-10-25", show = "briefing"))
    }

    @Test
    fun aRecipeRangeIdThatIsNotItsEpisodesRecipeIdIsSentWithoutAKey() = runTest {
        // A served ID trusted through another key would be refused by the server if another device stored a different key for it.
        aliases(alias(RECIPE_2.toString(), key = KEY, provenance = EpisodeAliasProvenance.RSS_SOURCE_AUDIO_MATCH))

        assertEquals(WireIdentity.NONE, out(RECIPE_2.toString()))
    }

    @Test
    fun aRecipeIdThatTwoKeysOfTheShowShareIsLeftOutForBoth() = runTest {
        // Same-show collision: KEY took the recipe ID, KEY_2's episode took a reserved ID with the same recipe ID.
        aliases(alias(RECIPE.toString(), owner = true), alias("-5", key = KEY_2, recipeId = RECIPE, provenance = EpisodeAliasProvenance.FEED_RESERVED))

        assertEquals(WireIdentity(null, KEY_2), out("-5"))
        assertEquals(WireIdentity(null, KEY), out(RECIPE.toString()))
    }

    @Test
    fun aRecipeIdWhoseLocalIdAnotherShowsAliasHoldsIsLeftOut() = runTest {
        aliases(alias("-6"), alias(RECIPE.toString(), show = "88", key = "other", recipeId = RECIPE))

        assertEquals(WireIdentity(null, KEY), out("-6"))
    }

    @Test
    fun aRecipeIdWhoseLocalIdAnotherShowsCatalogRowHoldsIsLeftOut() = runTest {
        aliases(alias("-6"), alias("-8", key = KEY_2))
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(catalogRow(RECIPE.toString(), key = "other", show = "88")))

        assertEquals(WireIdentity(null, KEY), out("-6"))
        // A catalog row of the same episode is no conflict.
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(catalogRow(RECIPE_2.toString(), key = KEY_2)))
        assertEquals(WireIdentity(RECIPE_2, KEY_2), out("-8"))
    }

    @Test
    fun richRemovalsDropOnlyARecipeIdThatNamesAnotherEpisode() = runTest {
        aliases(alias(RECIPE.toString(), owner = true), alias("-5", key = KEY_2, recipeId = RECIPE))
        val shared = QueueRemovalIdentity("-5", recipeId = RECIPE, show = SHOW, catalogKey = KEY_2)
        val own = QueueRemovalIdentity("-7", recipeId = RECIPE_2, show = SHOW, catalogKey = "guid-3")
        val plain = QueueRemovalIdentity("123")

        val sent = identity.outgoingRemovals(listOf(shared, own, plain))

        assertEquals(listOf(shared.copy(recipeId = null), own, plain), sent)
    }

    @Test
    fun anUnknownPulledRowGetsAnUnresolvedAliasThatOwnsNothing() = runTest {
        identity.recordIncoming(listOf(IncomingRow(PI_ID, SHOW, recipeId = RECIPE, catalogKey = KEY)))

        val recorded = storedAlias(PI_ID)!!
        assertEquals(EpisodeAliasState.UNRESOLVED, recorded.state)
        assertNull(recorded.catalogKey)
        assertNull(recorded.recipeId)
        assertFalse(recorded.owner)
        assertEquals(EpisodeAliasProvenance.CLOUD_SYNC, recorded.provenance)
        assertEquals(500L, recorded.createdAt)
    }

    @Test
    fun aPulledRowNamingAKnownEpisodeJoinsIt() = runTest {
        aliases(alias(RECIPE.toString(), owner = true))

        identity.recordIncoming(
            listOf(
                IncomingRow(PI_ID, SHOW, recipeId = null, catalogKey = KEY),
                // No key, but the recipe ID names exactly one key of the show.
                IncomingRow("61842097689", SHOW, recipeId = RECIPE, catalogKey = null),
            ),
        )

        for (id in listOf(PI_ID, "61842097689")) {
            val recorded = storedAlias(id)!!
            assertEquals(EpisodeAliasState.TRUSTED, recorded.state)
            assertEquals(KEY, recorded.catalogKey)
            assertEquals(RECIPE, recorded.recipeId)
            assertFalse(recorded.owner)
        }
        // One episode: every ID reads the same token.
        assertEquals(setOf(EpisodeGroup.tokenOf(SHOW, KEY)), identity.tokens(listOf(RECIPE.toString(), PI_ID, "61842097689")).values.toSet())
    }

    @Test
    fun aKeyThePhoneDoesNotKnowWinsOverARecipeIdAndAnAmbiguousRecipeIdNamesNothing() = runTest {
        aliases(alias(RECIPE.toString()), alias("-5", key = KEY_2, recipeId = RECIPE))

        identity.recordIncoming(
            listOf(
                IncomingRow("100", SHOW, recipeId = RECIPE, catalogKey = "guid-unknown"),
                IncomingRow("101", SHOW, recipeId = RECIPE, catalogKey = null),
            ),
        )

        assertEquals(EpisodeAliasState.UNRESOLVED, storedAlias("100")!!.state)
        assertEquals(EpisodeAliasState.UNRESOLVED, storedAlias("101")!!.state)
    }

    @Test
    fun aRecipeRangeIdThatIsNotTheMatchedRecipeIdStaysUnresolved() = runTest {
        aliases(alias(PI_ID))

        identity.recordIncoming(listOf(IncomingRow(RECIPE_2.toString(), SHOW, recipeId = null, catalogKey = KEY)))

        assertEquals(EpisodeAliasState.UNRESOLVED, storedAlias(RECIPE_2.toString())!!.state)
    }

    @Test
    fun existingAliasesStayAndAnUnresolvedOneOfTheShowIsSettled() = runTest {
        aliases(
            alias(RECIPE.toString(), owner = true),
            alias("-1", state = EpisodeAliasState.UNRESOLVED, key = null, provenance = EpisodeAliasProvenance.UPGRADE_LISTENER_ROW),
            alias("-2", state = EpisodeAliasState.UNRESOLVED, key = null, show = "88"),
            alias("-3", key = KEY_2),
            alias("-4", state = EpisodeAliasState.AMBIGUOUS, key = KEY_2),
        )

        identity.recordIncoming(listOf("-1", "-2", "-3", "-4").map { IncomingRow(it, SHOW, recipeId = RECIPE, catalogKey = KEY) })

        val settled = storedAlias("-1")!!
        assertEquals(EpisodeAliasState.TRUSTED, settled.state)
        assertEquals(KEY, settled.catalogKey)
        assertEquals(RECIPE, settled.recipeId)
        assertEquals(EpisodeAliasProvenance.CLOUD_SYNC, settled.provenance)
        assertEquals(1L, settled.createdAt)
        assertEquals(alias("-2", state = EpisodeAliasState.UNRESOLVED, key = null, show = "88"), storedAlias("-2"))
        assertEquals(alias("-3", key = KEY_2), storedAlias("-3"))
        assertEquals(alias("-4", state = EpisodeAliasState.AMBIGUOUS, key = KEY_2), storedAlias("-4"))
    }

    @Test
    fun rowsThatCannotHaveAnAliasHereAreLeftAlone() = runTest {
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(catalogRow("-20", key = "catalog-key")))
        database.listeningHistoryDao().upsert(history("-21", show = "88"))
        database.podcastDao().upsert(PodcastEntity("rss:abc", "RSS", "", "", null))
        database.rssEpisodeDao().upsertAll(
            listOf(
                RssEpisodeEntity(
                    episodeId = "-22",
                    podcastId = "rss:abc",
                    guid = "rss-guid",
                    title = "RSS",
                    description = "",
                    audioUrl = "https://example.com/rss.mp3",
                    imageUrl = null,
                    duration = 1,
                    publishedDate = 1L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            ),
        )

        identity.recordIncoming(
            listOf(
                IncomingRow("-30", "rss:abc", recipeId = null, catalogKey = null),
                IncomingRow("briefing_us_2026-10-25", SHOW, recipeId = null, catalogKey = null),
                IncomingRow("not-a-number", SHOW, recipeId = null, catalogKey = null),
                IncomingRow("-20", SHOW, recipeId = null, catalogKey = null),
                IncomingRow("-21", SHOW, recipeId = null, catalogKey = null),
                IncomingRow("-22", SHOW, recipeId = null, catalogKey = null),
            ),
        )

        assertTrue(database.episodeIdentityDao().aliasesForShow(SHOW).isEmpty())
        assertTrue(database.episodeIdentityDao().aliasesForShow("rss:abc").isEmpty())
    }

    @Test
    fun tokensAndRemovalTokensNameOneEpisodeAcrossItsIds() = runTest {
        aliases(alias(RECIPE.toString(), owner = true), alias(PI_ID))
        val token = EpisodeGroup.tokenOf(SHOW, KEY)!!

        val tokens = identity.tokens(listOf(RECIPE.toString(), PI_ID, "other"))

        assertEquals(mapOf(RECIPE.toString() to token, PI_ID to token, "other" to "other"), tokens)
        assertEquals(setOf(token), identity.removalTokens(listOf(QueueRemovalIdentity(PI_ID))))
        assertEquals(setOf("x", token), identity.removalTokens(listOf(QueueRemovalIdentity("x", show = SHOW, catalogKey = KEY))))
        assertEquals(setOf("y", token), identity.removalTokens(listOf(QueueRemovalIdentity("y", recipeId = RECIPE, show = SHOW))))
    }

    @Test
    fun theCatchUpSendsBackOnlyCloudCopiesThatLackAKnownIdentityWithNothingElseChanged() = runTest {
        aliases(
            alias(PI_ID, owner = true),
            alias("61842097001", key = KEY_2),
            alias("61842097002", key = "guid-3"),
            alias("-11", state = EpisodeAliasState.UNRESOLVED, key = null),
        )
        fun row(id: String, recipeId: Long? = null, key: String? = null) =
            ListeningHistorySyncDto(id, SHOW, progressMs = 42L, isCompleted = true, lastPlayedAt = 7L, updatedAt = 900L, recipeId = recipeId, catalogKey = key)
        val pulled = SyncPullResponse(
            history = listOf(
                row(PI_ID),
                // Already identified, or naming another key than this phone's: left as they are.
                row("61842097001", recipeId = RECIPE_2, key = KEY_2),
                row("61842097002", key = "guid-other"),
                // Unknown here, or unresolved: nothing to add.
                row("999"),
                row("-11"),
            ),
            queue = QueueSyncDto(
                items = listOf(QueueItemSyncDto(PI_ID, SHOW, position = 0, updatedAt = 30L), QueueItemSyncDto("999", SHOW, position = 1, updatedAt = 30L)),
                queueUpdatedAt = 30L,
                queueSequence = 4L,
                recentRemovedEpisodeIds = listOf("-9"),
                recentRemovedEpisodes = listOf(QueueRemovalSyncDto("-9", show = SHOW, catalogKey = KEY_2)),
            ),
            syncedAt = 1_000L,
        )

        val catchUp = identity.identityCatchUp(pulled)

        assertEquals(listOf(row(PI_ID, recipeId = RECIPE, key = KEY)), catchUp.history)
        val queue = catchUp.queue!!
        assertEquals(listOf(RECIPE to KEY, null to null), queue.items.map { it.recipeId to it.catalogKey })
        assertEquals(pulled.queue!!.copy(items = queue.items, recentRemovedEpisodes = null), queue)
        assertTrue(identity.identityCatchUp(SyncPullResponse(history = listOf(row("999")), syncedAt = 1L)).isEmpty)
    }

    @Test
    fun theCatchUpAddsOnlyTheFieldACloudCopyLacks() = runTest {
        aliases(alias(PI_ID, owner = true))
        val keyOnly = ListeningHistorySyncDto(PI_ID, SHOW, updatedAt = 900L, catalogKey = KEY)
        val otherRecipe = ListeningHistorySyncDto(PI_ID, SHOW, updatedAt = 900L, recipeId = RECIPE_2)

        assertEquals(listOf(keyOnly.copy(recipeId = RECIPE)), identity.identityCatchUp(SyncPullResponse(history = listOf(keyOnly), syncedAt = 1L)).history)
        assertTrue(identity.identityCatchUp(SyncPullResponse(history = listOf(otherRecipe), syncedAt = 1L)).isEmpty)
    }
}
