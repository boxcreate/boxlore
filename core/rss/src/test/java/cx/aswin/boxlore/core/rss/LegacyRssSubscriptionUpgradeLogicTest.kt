package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyRssSubscriptionUpgradeLogicTest {
    @Test
    fun `feed identity permits only structural URL variants`() {
        val snapshot = snapshot(sourceUrl = "http://www.example.com/feed.xml/")
        val target =
            TestFixtures
                .podcast(id = "42")
                .copy(feedUrl = "https://example.com/feed.xml")

        assertTrue(LegacyRssUpgradeLogic.isExactTarget(snapshot, target))
        assertFalse(
            LegacyRssUpgradeLogic.isExactTarget(
                snapshot,
                target.copy(feedUrl = "https://example.com/different.xml"),
            ),
        )
    }

    @Test
    fun `podcast GUID is exact without title fallback`() {
        val snapshot = snapshot(sourceUrl = "https://old.example/feed", podcastGuid = "GUID-42")
        val target =
            TestFixtures
                .podcast(id = "42", title = "Completely Different")
                .copy(
                    feedUrl = "https://new.example/feed",
                    podcastGuid = "guid-42",
                )

        assertTrue(LegacyRssUpgradeLogic.isExactTarget(snapshot, target))
        assertFalse(
            LegacyRssUpgradeLogic.isExactTarget(
                snapshot.copy(podcastGuid = null),
                target,
            ),
        )
    }

    @Test
    fun `negative listener ids must exist in copied local catalog`() {
        assertTrue(
            LegacyRssUpgradeLogic.listenerIdsResolve(
                rows = setOf("-1", "-2"),
                listenerIds = setOf("-1", "123"),
            ),
        )
        assertFalse(
            LegacyRssUpgradeLogic.listenerIdsResolve(
                rows = setOf("-1"),
                listenerIds = setOf("-2"),
            ),
        )
    }

    @Test
    fun `target entity changes identity but preserves listener settings`() {
        val source =
            PodcastEntity(
                podcastId = "rss:old",
                title = "Old",
                author = "Old Author",
                imageUrl = "https://example.com/old.jpg",
                description = "Old description",
                isSubscribed = true,
                subscribedAt = 123L,
                preferredSort = "oldest",
                notificationsEnabled = true,
                autoDownloadEnabled = true,
                skipBeginningOverrideMs = 5_000L,
                skipEndingOverrideMs = 8_000L,
                sourceType = PodcastEntity.SOURCE_RSS,
                rssHasNewEpisodes = true,
            )
        val target =
            TestFixtures
                .podcast(id = "42", title = "Catalog title")
                .copy(feedUrl = "https://example.com/feed.xml")

        val entity =
            LegacyRssUpgradeLogic.targetEntity(
                source = source,
                target = target,
                snapshot = snapshot(sourceUrl = "https://example.com/feed.xml"),
                latestEpisode = null,
                nowMillis = 999L,
            )

        assertEquals("42", entity.podcastId)
        assertEquals(PodcastEntity.SOURCE_PODCAST_INDEX, entity.sourceType)
        assertEquals(123L, entity.subscribedAt)
        assertEquals("oldest", entity.preferredSort)
        assertTrue(entity.notificationsEnabled)
        assertTrue(entity.autoDownloadEnabled)
        assertEquals(5_000L, entity.skipBeginningOverrideMs)
        assertTrue(entity.rssHasNewEpisodes)
    }

    @Test
    fun `the copied catalog keeps every stored RSS ID and gives a shared key to the feed item`() = runTest {
        // Stored RSS rows as older builds wrote the fixture feed (including both rows for ep2 and ep0),
        // plus a stale stored copy that shares the key of a feed item.
        val stale = rssRow("-999", key = "ep-5").copy(title = "Stale copy")
        for (before in listOf(IdentityFixtureFeed.SMALL_ROWS, IdentityFixtureFeed.LARGE_ROWS)) {
            for (bytes in listOf(IdentityFixtureFeed.small(), IdentityFixtureFeed.large())) {
                val stored = before.map { (id, key) -> rssRow(id, key) } + stale
                val rows =
                    LegacyRssUpgradeLogic.copiedLocalRows(
                        feedItems = IdentityFixtureFeed.parse(bytes).items,
                        stored = stored,
                        storedIdentities = stored.map { RssEpisodeIdentity(it.episodeId, it.guid, it.audioUrl) },
                        target = LegacyRssUpgradeLogic.CopyTarget(IdentityFixtureFeed.SHOW_ID, IdentityFixtureFeed.namespace, null, null),
                    )

                val ids = rows.map { it.episodeId }
                assertTrue(ids.containsAll(before.map { it.first }), "every stored ID is copied: $ids")
                assertTrue(LegacyRssUpgradeLogic.listenerIdsResolve(ids, before.map { it.first }))
                assertEquals(ids.size, ids.toSet().size)
                assertEquals(rows.size, rows.map { it.guid }.toSet().size, "one row per key")
                val ep5 = rows.single { it.guid == "ep-5" }
                assertEquals("-1620196808107782941" to "Episode Five", ep5.episodeId to ep5.title)
                assertFalse("-999" in ids)
            }
        }
    }

    @Test
    fun `only the show's trusted owners of copied contract keys under other IDs are displaced`() {
        val planned = listOf(planned("-1", "g1"), planned("-2", "g2"), planned("-3", "g3\n"), planned("-4", "phone-only", followsContract = false))
        val aliases =
            listOf(
                alias("501", "g1", owner = true),
                alias("506", "g3", owner = true),
                alias("-2", "g2", owner = true),
                alias("502", "g2", owner = false),
                alias("503", "phone-only", owner = true),
                alias("504", "g9", owner = true),
                alias("505", "g1", owner = true, state = EpisodeAliasState.AMBIGUOUS),
            )

        assertEquals(listOf("501", "506"), LegacyRssUpgradeLogic.displacedOwners(planned, aliases).map { it.aliasId })
    }

    @Test
    fun `a displaced owner is demoted once a new copied alias owns its key`() {
        val displaced = alias("501", "g1", owner = true)
        val writes = CatalogAliasRecords.Writes(inserts = listOf(alias("-1", "g1", owner = true)), updates = emptyList())

        val rewrites = LegacyRssUpgradeLogic.ownerHandover(listOf(displaced), writes, listOf(planned("-1", "g1")), aliasesById = emptyMap())

        assertEquals(listOf(displaced.copy(owner = false)), rewrites)
    }

    @Test
    fun `the copied ID's existing trusted alias is promoted and the displaced owner demoted`() {
        val displaced = alias("501", "g1", owner = true)
        val copied = alias("-1", "g1", owner = false)
        val none = CatalogAliasRecords.Writes(emptyList(), emptyList())

        val rewrites = LegacyRssUpgradeLogic.ownerHandover(listOf(displaced), none, listOf(planned("-1", "g1")), mapOf("-1" to copied))

        assertEquals(listOf(copied.copy(owner = true), displaced.copy(owner = false)), rewrites)
    }

    @Test
    fun `a key no copied ID can own keeps its owner`() {
        val displaced = alias("501", "g1", owner = true)
        val none = CatalogAliasRecords.Writes(emptyList(), emptyList())
        val planned = listOf(planned("-1", "g1"))

        for (copied in listOf(
            alias("-1", "g1", owner = false, state = EpisodeAliasState.AMBIGUOUS),
            alias("-1", "g1", owner = false).copy(podcastId = "920667"),
            alias("-1", "g2", owner = false),
        )) {
            assertEquals(emptyList<EpisodeAliasEntity>(), LegacyRssUpgradeLogic.ownerHandover(listOf(displaced), none, planned, mapOf("-1" to copied)))
        }
        assertEquals(emptyList<EpisodeAliasEntity>(), LegacyRssUpgradeLogic.ownerHandover(emptyList(), none, planned, emptyMap()))
    }

    private fun planned(id: String, key: String, followsContract: Boolean = true) = PlannedEpisodeRow(
        entity =
        LocalEpisodeEntity(
            episodeId = id,
            podcastId = "920666",
            guid = key,
            title = "Ep $key",
            description = "",
            audioUrl = "https://cdn.example.com/$id.mp3",
            imageUrl = null,
            duration = 60,
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
        source = PlannedIdSource.STORED,
        keyFollowsContract = followsContract,
    )

    private fun alias(id: String, key: String, owner: Boolean, state: String = EpisodeAliasState.TRUSTED,) = EpisodeAliasEntity(
        aliasId = id,
        podcastId = "920666",
        catalogKey = key,
        recipeId = 4_503_599_627_370_497L,
        state = state,
        owner = owner,
        provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        createdAt = 1L,
    )

    private fun rssRow(id: String, key: String) = RssEpisodeEntity(
        episodeId = id,
        podcastId = IdentityFixtureFeed.namespace,
        guid = key.takeUnless { it.startsWith("https://") },
        title = "Stored $id",
        description = "",
        audioUrl = if (key.startsWith("https://")) key else "https://cdn.example.com/$key.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 1L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    private fun snapshot(sourceUrl: String, podcastGuid: String? = null,): LegacyRssFeedSnapshot = LegacyRssFeedSnapshot(
        sourceFeedUrl = sourceUrl,
        finalFeedUrl = sourceUrl,
        podcastGuid = podcastGuid,
        etag = null,
        lastModified = null,
        parsed =
        ParsedRssFeed(
            title = "Show",
            author = "Author",
            description = null,
            imageUrl = null,
            genre = null,
            podcastType = Podcast.SOURCE_PODCAST_INDEX,
            podcastGuid = podcastGuid,
            declaredUpdatedAt = null,
            episodes = emptyList(),
        ),
    )
}
