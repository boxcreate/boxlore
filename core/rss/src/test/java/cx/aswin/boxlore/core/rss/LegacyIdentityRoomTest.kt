package cx.aswin.boxlore.core.rss

import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stored data older builds left keeps its IDs through the real refresh on a real Room database:
 * the legacy rows and aliases exactly as the v39 upgrade leaves them, IDs only listener rows hold
 * (kept by the item that provably owns them, never re-keyed), `rss:` shows, and the legacy RSS
 * subscription upgrade (docs/EPISODE_IDS.md §3–5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyIdentityRoomTest : RoomCatalogTestBase() {
    @Test
    fun anOrphanTheNewItemMatchesKeepsItsIdAndNothingIsRekeyed() = runBlocking {
        // A restored history row whose catalog row never came back: the new item keeps its ID.
        follow(SHOW)
        history("-55", SHOW, audio = SAME_AUDIO, title = "Same title")
        val before = database.listeningHistoryDao().getHistoryItem("-55")
        feed.items = listOf(Item("g9", title = "Same title", audio = SAME_AUDIO))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g9" to "-55"), rows(SHOW))
        assertEquals("the history row is untouched", before, database.listeningHistoryDao().getHistoryItem("-55"))
        assertNull(database.listeningHistoryDao().getHistoryItem(recipe(SHOW, "g9")))
        assertEquals(EpisodeAliasProvenance.FEED_LISTENER_MATCH, alias("-55")?.provenance)
    }

    @Test
    fun anOrphanWhoseEpisodeHasAStoredRowIsStillRematchedToIt() = runBlocking {
        // The orphan rematch for legacy negative IDs stays for episodes that already have a row.
        follow(SHOW)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("5001", SHOW, "g9", audio = SAME_AUDIO, title = "Same title")))
        history("-55", SHOW, audio = SAME_AUDIO, title = "Same title")
        feed.items = listOf(Item("g9", title = "Same title", audio = SAME_AUDIO))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g9" to "5001"), rows(SHOW))
        assertNull(database.listeningHistoryDao().getHistoryItem("-55"))
        assertNotNull(database.listeningHistoryDao().getHistoryItem("5001"))
    }

    @Test
    fun aHealedLegacyRowKeepsItsIdAndItsUnresolvedAliasBecomesTrusted() = runBlocking {
        follow(SHOW)
        val namespace = RssIdGenerator.podcastId(feedUrl(SHOW))
        val minted = StickyEpisodeIdentity.ownMintedId(namespace, "g1")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row(minted, SHOW, "id:$minted")))
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(EpisodeAliasEntity(minted, SHOW, null, null, EpisodeAliasState.UNRESOLVED, false, EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY, 1L)),
        )
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to minted), rows(SHOW))
        val alias = requireNotNull(alias(minted))
        assertEquals(EpisodeAliasState.TRUSTED, alias.state)
        assertEquals(EpisodeAliasProvenance.HEAL_SYNTHETIC_KEY, alias.provenance)
        assertEquals("g1", alias.catalogKey)
        assertEquals(EpisodeRecipeId.forNamespace(SHOW.toLong(), "g1"), alias.recipeId)
        assertTrue(alias.owner)
        assertEquals(1L, alias.createdAt)
    }

    @Test
    fun aPaddedGuidWithItsSeededTrustedAliasRecordsTheHealAndKeepsItsIdAcrossEviction() = runBlocking {
        // As the v39 upgrade leaves it: key "g1\n" from Room migration 31→32, alias re-trimmed and trusted.
        follow(SHOW)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-31", SHOW, "g1\n")))
        seedAlias("-31", SHOW, "g1")
        history("-31", SHOW)
        val before = database.listeningHistoryDao().getHistoryItem("-31")
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        assertEquals(mapOf("g1" to "-31"), rows(SHOW))
        val healed = requireNotNull(alias("-31"))
        assertEquals(EpisodeAliasProvenance.HEAL_RETRIMMED_KEY, healed.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, healed.state)
        assertTrue(healed.owner)
        assertEquals(1L, healed.createdAt)

        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        assertEquals(mapOf("g1" to "-31"), rows(SHOW))
        assertEquals(before, database.listeningHistoryDao().getHistoryItem("-31"))
    }

    @Test
    fun aPaddedGuidWithoutAnAliasGetsOneThatRecordsTheHeal() = runBlocking {
        follow(SHOW)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("-32", SHOW, "x\n")))
        feed.items = listOf(Item("x"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("x" to "-32"), rows(SHOW))
        assertEquals(EpisodeAliasProvenance.HEAL_RETRIMMED_KEY, alias("-32")?.provenance)
        assertEquals("x", alias("-32")?.catalogKey)
    }

    @Test
    fun aGuidlessAudioKeyedRowSettlesItsUnresolvedAliasAndKeepsItsIdAcrossEviction() = runBlocking {
        // A phone-minted GUID-less row keyed on its own audio address: the v39 upgrade seeds it unresolved.
        follow(SHOW)
        val audio = "https://cdn.example/no-guid.mp3"
        val minted = StickyEpisodeIdentity.ownMintedId(namespace(), audio)
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row(minted, SHOW, audio, audio = audio)))
        seedUnresolved(minted, SHOW, EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY)
        history(minted, SHOW, audio = audio)
        val before = database.listeningHistoryDao().getHistoryItem(minted)
        feed.items = listOf(Item(guid = null, audio = audio, enclosure = audio))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        assertEquals(mapOf(audio to minted), rows(SHOW))
        val settled = requireNotNull(alias(minted))
        assertEquals(EpisodeAliasState.TRUSTED, settled.state)
        assertEquals(EpisodeAliasProvenance.FEED_CONFIRMED_KEY, settled.provenance)
        assertEquals(audio, settled.catalogKey)

        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals("the re-read keeps the old ID", mapOf(audio to minted), rows(SHOW))
        assertEquals("the history row is untouched", before, database.listeningHistoryDao().getHistoryItem(minted))
    }

    @Test
    fun aGuidlessAudioKeyedRowEvictedBeforeAnyReadKeepsItsId() = runBlocking {
        follow(SHOW)
        val audio = "https://cdn.example/no-guid.mp3"
        val minted = StickyEpisodeIdentity.ownMintedId(namespace(), audio)
        seedUnresolved(minted, SHOW, EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY)
        history(minted, SHOW, audio = audio)
        val before = database.listeningHistoryDao().getHistoryItem(minted)
        feed.items = listOf(Item(guid = null, audio = audio, enclosure = audio))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf(audio to minted), rows(SHOW))
        assertEquals(before, database.listeningHistoryDao().getHistoryItem(minted))
        assertEquals(EpisodeAliasProvenance.FEED_OWN_MINT, alias(minted)?.provenance)
        assertEquals(EpisodeAliasState.TRUSTED, alias(minted)?.state)
    }

    @Test
    fun aSyntheticKeyRowEvictedBeforeItWasHealedKeepsItsId() = runBlocking {
        follow(SHOW)
        val minted = StickyEpisodeIdentity.ownMintedId(namespace(), "g1")
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row(minted, SHOW, "id:$minted")))
        seedUnresolved(minted, SHOW, EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY)
        history(minted, SHOW)
        val before = database.listeningHistoryDao().getHistoryItem(minted)
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to minted), rows(SHOW))
        assertEquals(before, database.listeningHistoryDao().getHistoryItem(minted))
        val settled = requireNotNull(alias(minted))
        assertEquals(EpisodeAliasState.TRUSTED, settled.state)
        assertEquals("g1", settled.catalogKey)
        assertEquals(EpisodeAliasProvenance.FEED_OWN_MINT, settled.provenance)
    }

    @Test
    fun aHistoryOnlyIdEqualToTheItemsOwnMintIsKeptWithoutARekey() = runBlocking {
        follow(SHOW)
        val minted = StickyEpisodeIdentity.ownMintedId(namespace(), "g1")
        history(minted, SHOW, audio = "https://cdn.example/elsewhere.mp3")
        val before = database.listeningHistoryDao().getHistoryItem(minted)
        feed.items = listOf(Item("g1"), Item("g2"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to minted, "g2" to recipe(SHOW, "g2")), rows(SHOW))
        assertEquals(before, database.listeningHistoryDao().getHistoryItem(minted))
        assertEquals(EpisodeAliasProvenance.FEED_OWN_MINT, alias(minted)?.provenance)
    }

    @Test
    fun aPlayedFeedOnlyExtraKeepsItsIdWhenTheShowIsFollowed() = runBlocking {
        // "Missing episodes?" stored the extra with the same phone mint a catalog read gives the item.
        val minted = RssIdGenerator.episodeIdForPodcast(namespace(), "g1", "https://cdn.example/g1.mp3", 10L, "Ep g1")
        database.episodeSupplementDao().upsertItems(listOf(supplementRow(minted, SHOW, "g1")))
        history(minted, SHOW, audio = "https://cdn.example/g1.mp3")
        seedUnresolved(minted, SHOW, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        val before = database.listeningHistoryDao().getHistoryItem(minted)
        follow(SHOW)
        feed.items = listOf(Item("g1"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to minted), rows(SHOW))
        assertEquals(before, database.listeningHistoryDao().getHistoryItem(minted))
        val settled = requireNotNull(alias(minted))
        assertEquals(EpisodeAliasState.TRUSTED, settled.state)
        assertEquals("g1", settled.catalogKey)
        assertEquals(EpisodeAliasProvenance.FEED_OWN_MINT, settled.provenance)
    }

    @Test
    fun aPodcastIndexIdInHistoryIsKeptForItsEpisodeOnTheFirstRead() = runBlocking {
        // Played from search before the show was followed: history holds the Podcast Index ID.
        history("5001", SHOW, audio = "https://cdn.example/g1.mp3", title = "Ep g1")
        seedUnresolved("5001", SHOW, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
        val before = database.listeningHistoryDao().getHistoryItem("5001")
        follow(SHOW)
        feed.items = listOf(Item("g1"), Item("g2"))

        assertTrue(refresh(SHOW) is RefreshOutcome.Success)

        assertEquals(mapOf("g1" to "5001", "g2" to recipe(SHOW, "g2")), rows(SHOW))
        assertEquals("the episode's progress and like stay on it", before, database.listeningHistoryDao().getHistoryItem("5001"))
        val settled = requireNotNull(alias("5001"))
        assertEquals(EpisodeAliasState.TRUSTED, settled.state)
        assertEquals("g1", settled.catalogKey)
        assertEquals(EpisodeAliasProvenance.FEED_LISTENER_MATCH, settled.provenance)
        assertTrue(settled.owner)

        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        assertTrue(refresh(SHOW) is RefreshOutcome.Success)
        assertEquals(mapOf("g1" to "5001", "g2" to recipe(SHOW, "g2")), rows(SHOW))
    }

    @Test
    fun rssShowsReadTheirFeedsWithPhoneMintsOnlyAndNoAliases() = runBlocking {
        val rss = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database, feed)
        feed.items = listOf(Item("g1"), Item(guid = null, audio = "https://cdn.example/no-guid.mp3", enclosure = "https://cdn.example/no-guid.mp3"))

        val subscribed = rss.addSubscription(RSS_FEED_URL)
        val showId = subscribed.podcast.id
        assertTrue(showId.startsWith("rss:"))
        feed.items = feed.items + Item("g2")
        assertTrue(rss.refreshCatalog(showId).isSuccess)

        val ids = database.rssEpisodeDao().getAllNewest(showId).map { it.episodeId }
        assertEquals(3, ids.size)
        assertTrue(ids.toString(), ids.all { it.toLong() < 0L })
        assertTrue(ids.none { EpisodeRecipeId.isRecipeId(it.toLong()) || it.toLong() > 0L })
        assertTrue(database.episodeIdentityDao().aliasesForShow(showId).isEmpty())
        assertTrue(database.episodeIdentityDao().getAliases(ids).isEmpty())
        assertTrue(database.localEpisodeCatalogDao().listIdentities(showId).isEmpty())
    }

    @Test
    fun theLegacyRssUpgradeRecordsAnAliasForEveryIdItCarries() = runBlocking {
        val rss = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database, feed)
        feed.items = listOf(Item("g1"), Item("g2"))
        val rssId = rss.addSubscription(RSS_FEED_URL).podcast.id
        val rssIds = database.rssEpisodeDao().getAllNewest(rssId).associate { it.guid to it.episodeId }
        val snapshot = rss.legacySubscriptionRepair.inspect(rssId).getOrThrow()

        val outcome = rss.legacySubscriptionRepair.upgrade(rssId, target(), snapshot)

        assertEquals(LegacyRssUpgradeOutcome.MIGRATED, outcome)
        assertEquals(mapOf("g1" to rssIds.getValue("g1"), "g2" to rssIds.getValue("g2")), rows(SHOW))
        for ((key, id) in rssIds) {
            val alias = requireNotNull(alias(id)) { "alias of $key" }
            assertEquals(SHOW, alias.podcastId)
            assertEquals(key, alias.catalogKey)
            assertEquals(EpisodeAliasState.TRUSTED, alias.state)
            assertEquals(EpisodeAliasProvenance.RSS_FOLLOW_UPGRADE, alias.provenance)
            assertTrue(alias.owner)
        }

        // Evicted, the show reads back under the same IDs.
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        feed.items = listOf(Item("g1"), Item("g2"))
        assertTrue(refresh(SHOW, url = RSS_FEED_URL) is RefreshOutcome.Success)
        assertEquals(mapOf("g1" to rssIds.getValue("g1"), "g2" to rssIds.getValue("g2")), rows(SHOW))
    }

    @Test
    fun theLegacyRssUpgradeHandsOwnershipFromAnEarlierReadToTheIdsItCarries() = runBlocking {
        // An earlier read of the Podcast Index show, since evicted, left a trusted owner for g1 under
        // its recipe ID. The show holds no listener state, so the carried RSS ID takes the episode over.
        val earlier = recipe(SHOW, "g1")
        seedAlias(earlier, SHOW, "g1")
        val rss = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database, feed)
        feed.items = listOf(Item("g1"), Item("g2"))
        val rssId = rss.addSubscription(RSS_FEED_URL).podcast.id
        val rssIds = database.rssEpisodeDao().getAllNewest(rssId).associate { it.guid to it.episodeId }
        history(rssIds.getValue("g1"), rssId)
        val snapshot = rss.legacySubscriptionRepair.inspect(rssId).getOrThrow()

        assertEquals(LegacyRssUpgradeOutcome.MIGRATED, rss.legacySubscriptionRepair.upgrade(rssId, target(), snapshot))

        val carried = requireNotNull(alias(rssIds.getValue("g1")))
        assertEquals(EpisodeAliasState.TRUSTED, carried.state)
        assertTrue("the carried ID owns g1", carried.owner)
        val displaced = requireNotNull(alias(earlier))
        assertFalse("the earlier owner is demoted", displaced.owner)
        assertEquals("the earlier ID still resolves", EpisodeAliasState.TRUSTED, displaced.state)
        assertEquals(listOf(rssIds.getValue("g1")), database.episodeIdentityDao().aliasesForKey(SHOW, "g1").filter { it.owner }.map { it.aliasId })

        // Evicted, g1 reads back under the carried ID that holds its history, not the earlier owner.
        database.localEpisodeCatalogDao().deleteCatalog(SHOW)
        feed.items = listOf(Item("g1"), Item("g2"))
        assertTrue(refresh(SHOW, url = RSS_FEED_URL) is RefreshOutcome.Success)
        assertEquals(mapOf("g1" to rssIds.getValue("g1"), "g2" to rssIds.getValue("g2")), rows(SHOW))
        assertEquals(SHOW, database.listeningHistoryDao().getHistoryItem(rssIds.getValue("g1"))?.podcastId)
        assertNull(database.listeningHistoryDao().getHistoryItem(earlier))
    }

    @Test
    fun theLegacyRssUpgradeStaysOnRssWhenTheOwnerItWouldDisplaceHoldsListenerState() = runBlocking {
        // The target show's owner of g1 holds history under another show, which the show-level
        // in-use check can't see: ownership never moves off it, so nothing is written.
        val earlier = recipe(SHOW, "g1")
        seedAlias(earlier, SHOW, "g1")
        history(earlier, OTHER_SHOW)
        val rss = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database, feed)
        feed.items = listOf(Item("g1"))
        val rssId = rss.addSubscription(RSS_FEED_URL).podcast.id
        val carried = database.rssEpisodeDao().getAllNewest(rssId).single().episodeId
        val snapshot = rss.legacySubscriptionRepair.inspect(rssId).getOrThrow()

        assertEquals(LegacyRssUpgradeOutcome.TARGET_ALREADY_IN_USE, rss.legacySubscriptionRepair.upgrade(rssId, target(), snapshot))

        assertTrue(rows(SHOW).isEmpty())
        assertTrue(requireNotNull(alias(earlier)).owner)
        assertNull(alias(carried))
        assertEquals(true, database.podcastDao().getPodcast(rssId)?.isSubscribed)
    }

    @Test
    fun theLegacyRssUpgradeStopsAtAnotherShowsAlias() = runBlocking {
        val rss = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database, feed)
        feed.items = listOf(Item("g1"))
        val rssId = rss.addSubscription(RSS_FEED_URL).podcast.id
        val carried = database.rssEpisodeDao().getAllNewest(rssId).single().episodeId
        // Another show's evicted episode holds the same ID (the feed listed twice in Podcast Index).
        seedAlias(carried, OTHER_SHOW, "g1")
        val snapshot = rss.legacySubscriptionRepair.inspect(rssId).getOrThrow()

        assertEquals(LegacyRssUpgradeOutcome.INCOMPLETE_CATALOG, rss.legacySubscriptionRepair.upgrade(rssId, target(), snapshot))
        assertTrue(rows(SHOW).isEmpty())
        assertEquals(OTHER_SHOW, alias(carried)?.podcastId)
    }
}
