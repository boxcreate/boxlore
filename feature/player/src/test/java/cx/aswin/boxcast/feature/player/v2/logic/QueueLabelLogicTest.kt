package cx.aswin.boxcast.feature.player.v2.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueLabelLogicTest {
    @Test
    fun manualAndUnknownItemsHaveNoSourceLabel() {
        assertNull(queueSourceLabel(episode(contextType = null)))
        assertNull(queueSourceLabel(episode(contextType = "MANUAL")))
        assertNull(queueSourceLabel(episode(contextType = "UNKNOWN")))
    }

    @Test
    fun loreItemsAreIdentified() {
        assertEquals("From Lore", queueSourceLabel(episode(contextType = "LORE")))
    }

    @Test
    fun knownAutoFillSourcesHaveSpecificLabels() {
        val expectations = mapOf(
            "same_podcast" to "Continuing series",
            "resume" to "Pick up where you left off",
            "subscription" to "From your subscriptions",
            "server_rec" to "Recommended for you",
            "personalized_rec" to "Recommended for you",
            "similar_episode" to "Based on what you're playing",
            "similar_liked" to "Based on something you liked"
        )

        expectations.forEach { (source, expected) ->
            assertEquals(
                expected,
                queueSourceLabel(episode(contextType = "AUTO_FILL", contextSourceId = source))
            )
        }
    }

    @Test
    fun trendingSourceUsesMeaningfulGenre() {
        assertEquals(
            "Trending in Technology",
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = "Technology"
                )
            )
        )
    }

    @Test
    fun trendingSourceFallsBackForGenericOrMissingGenre() {
        assertEquals(
            "Trending now",
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = "Podcast"
                )
            )
        )
        assertEquals(
            "Trending now",
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = " "
                )
            )
        )
        assertEquals(
            "Trending now",
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = null
                )
            )
        )
    }

    @Test
    fun unknownAutoFillSourceUsesGenericLabel() {
        assertEquals(
            "Added for you",
            queueSourceLabel(
                episode(contextType = "AUTO_FILL", contextSourceId = "future_source")
            )
        )
        assertEquals(
            "Added for you",
            queueSourceLabel(episode(contextType = "AUTO_FILL", contextSourceId = null))
        )
    }
}
