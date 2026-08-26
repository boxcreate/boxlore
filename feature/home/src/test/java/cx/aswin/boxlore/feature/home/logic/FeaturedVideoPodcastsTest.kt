package cx.aswin.boxlore.feature.home.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeaturedVideoPodcastsTest {
    @Test
    fun tedIsPinnedFirstAndRemainingOrderIsStable() {
        val podcasts = featuredVideoPodcasts()

        assertEquals("TED Talks Daily", podcasts.first().title)
        assertEquals("This Week in Tech", podcasts[1].title)
        assertEquals("Security Now", podcasts[2].title)
        assertEquals(14, podcasts.size)
    }

    @Test
    fun catalogContainsUniquePlayableVideoFeedIdentities() {
        val podcasts = featuredVideoPodcasts()

        assertEquals(podcasts.size, podcasts.map { it.id }.distinct().size)
        assertEquals(podcasts.size, podcasts.map { it.feedUrl }.distinct().size)
        assertTrue(podcasts.all { it.medium == "video" })
        assertTrue(podcasts.all { it.imageUrl.startsWith("https://") })
        assertTrue(podcasts.all { it.feedUrl?.startsWith("https://") == true })
    }

    @Test
    fun tedStandardDefinitionTargetsTheSdFeed() {
        val sdPodcast = featuredTedTalksSdPodcast()

        assertEquals("588746", sdPodcast.id)
        assertEquals("https://feeds.feedburner.com/TEDTalks_video", sdPodcast.feedUrl)
        assertEquals("TED Talks Daily", sdPodcast.title)
    }
}
