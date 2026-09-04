package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
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
