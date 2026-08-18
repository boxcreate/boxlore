package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class LegacyRssRepairLogicTest {
    @Test
    fun `one shot gate admits one pass and never loops`() {
        val gate = LegacyRssRepairOneShotGate()

        assertEquals(false, gate.hasAttempted())
        assertEquals(true, gate.tryBegin())
        assertEquals(true, gate.hasAttempted())
        assertEquals(false, gate.tryBegin())
    }

    @Test
    fun `one shot gate retries only when preflight persistence fails`() {
        val gate = LegacyRssRepairOneShotGate()

        assertEquals(true, gate.tryBegin())
        gate.resetAfterFailure()

        assertEquals(false, gate.hasAttempted())
        assertEquals(true, gate.tryBegin())
    }

    @Test
    fun `repair pass starts only for eligible work while online`() {
        assertEquals(true, LegacyRssRepairLogic.shouldStartPass(hasEligibleSources = true, isOnline = true))
        assertEquals(false, LegacyRssRepairLogic.shouldStartPass(hasEligibleSources = true, isOnline = false))
        assertEquals(false, LegacyRssRepairLogic.shouldStartPass(hasEligibleSources = false, isOnline = true))
    }

    @Test
    fun `pass stops after consecutive transient lookup failures`() {
        assertEquals(false, LegacyRssRepairLogic.shouldStopPass(0))
        assertEquals(false, LegacyRssRepairLogic.shouldStopPass(2))
        assertEquals(true, LegacyRssRepairLogic.shouldStopPass(3))
    }

    @Test
    fun `completed pass settles unless an id migration is still pending`() {
        assertEquals(true, LegacyRssRepairLogic.shouldMarkCompleted(hasPendingIdRepair = false))
        assertEquals(false, LegacyRssRepairLogic.shouldMarkCompleted(hasPendingIdRepair = true))
    }

    @Test
    fun `intentional linked RSS source is excluded from automatic repair`() {
        val source =
            PodcastEntity(
                podcastId = "rss:old",
                title = "Show",
                author = "Author",
                imageUrl = "",
                description = null,
                isSubscribed = true,
                sourceType = PodcastEntity.SOURCE_RSS,
                linkedPodcastIndexId = "42",
            )

        assertEquals(false, LegacyRssRepairLogic.isEligibleSource(source))
        assertEquals(
            true,
            LegacyRssRepairLogic.isEligibleSource(source.copy(linkedPodcastIndexId = null)),
        )
    }

    @Test
    fun `exact feed URL alone is sufficient`() {
        val podcast = TestFixtures.podcast(id = "42")

        val result =
            LegacyRssRepairLogic.selectMatch(
                urlLookup = ExactPodcastLookupResult.Found(podcast),
                guidLookup = ExactPodcastLookupResult.NotFound,
            )

        assertEquals(podcast, assertInstanceOf(ExactRepairMatch.Found::class.java, result).podcast)
    }

    @Test
    fun `exact podcast GUID alone is sufficient`() {
        val podcast = TestFixtures.podcast(id = "42")

        val result =
            LegacyRssRepairLogic.selectMatch(
                urlLookup = ExactPodcastLookupResult.NotFound,
                guidLookup = ExactPodcastLookupResult.Found(podcast),
            )

        assertEquals(podcast, assertInstanceOf(ExactRepairMatch.Found::class.java, result).podcast)
    }

    @Test
    fun `conflicting URL and GUID identities are rejected`() {
        val result =
            LegacyRssRepairLogic.selectMatch(
                urlLookup = ExactPodcastLookupResult.Found(TestFixtures.podcast(id = "42")),
                guidLookup = ExactPodcastLookupResult.Found(TestFixtures.podcast(id = "84")),
            )

        assertEquals(ExactRepairMatch.NoMatch, result)
    }

    @Test
    fun `lookup failure prevents a silent migration`() {
        val result =
            LegacyRssRepairLogic.selectMatch(
                urlLookup = ExactPodcastLookupResult.Found(TestFixtures.podcast(id = "42")),
                guidLookup = ExactPodcastLookupResult.Failed,
            )

        assertEquals(ExactRepairMatch.TransientFailure, result)
    }

    @Test
    fun `non Podcast Index identity is rejected`() {
        val result =
            LegacyRssRepairLogic.selectMatch(
                urlLookup = ExactPodcastLookupResult.Found(TestFixtures.rssPodcast(id = "rss:42")),
                guidLookup = ExactPodcastLookupResult.NotFound,
            )

        assertEquals(ExactRepairMatch.NoMatch, result)
    }
}
