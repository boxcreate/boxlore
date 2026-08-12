package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSupplementEligibilityTest {
    @Test
    fun `PI podcast with https feedUrl is eligible`() {
        val podcast =
            TestFixtures.podcast(id = "123").copy(feedUrl = "https://feeds.example.com/show.xml")
        assertTrue(EpisodeSupplementEligibility.canLoadMissingEpisodes(podcast))
    }

    @Test
    fun `RSS-owned podcast is never eligible`() {
        val podcast =
            TestFixtures.podcast(
                id = "rss:abc",
                sourceType = Podcast.SOURCE_RSS,
            ).copy(feedUrl = "https://feeds.example.com/show.xml")
        assertFalse(EpisodeSupplementEligibility.canLoadMissingEpisodes(podcast))
    }

    @Test
    fun `missing or http feedUrl is not eligible`() {
        assertFalse(
            EpisodeSupplementEligibility.canLoadMissingEpisodes(
                TestFixtures.podcast(id = "1").copy(feedUrl = null),
            ),
        )
        assertFalse(
            EpisodeSupplementEligibility.canLoadMissingEpisodes(
                TestFixtures.podcast(id = "1").copy(feedUrl = "http://insecure.example/feed"),
            ),
        )
    }
}
