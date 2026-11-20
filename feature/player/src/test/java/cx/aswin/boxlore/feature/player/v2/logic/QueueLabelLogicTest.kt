package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.player.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueueLabelLogicTest {
    @Test
    fun manualAndUnknownItemsHaveNoSourceLabel() {
        assertNull(queueSourceLabel(episode(contextType = null)))
        assertNull(queueSourceLabel(episode(contextType = "MANUAL")))
        assertNull(queueSourceLabel(episode(contextType = "UNKNOWN")))
    }

    @Test
    fun loreItemsAreIdentified() {
        assertEquals(UiText.res(R.string.player_queue_source_lore), queueSourceLabel(episode(contextType = "LORE")))
    }

    @Test
    fun knownAutoFillSourcesHaveSpecificLabels() {
        val expectations =
            mapOf(
                "same_podcast" to UiText.res(R.string.player_queue_source_same_podcast),
                "resume" to UiText.res(R.string.player_queue_source_resume),
                "resume_stale" to UiText.res(R.string.player_queue_source_resume_stale),
                "subscription" to UiText.res(R.string.player_queue_source_subscription),
                "server_rec" to UiText.res(R.string.player_queue_source_recommended),
                "personalized_rec" to UiText.res(R.string.player_queue_source_recommended),
                "similar_episode" to UiText.res(R.string.player_queue_source_similar_episode),
                "similar_liked" to UiText.res(R.string.player_queue_source_similar_liked),
            )

        expectations.forEach { (source, expected) ->
            assertEquals(
                expected,
                queueSourceLabel(episode(contextType = "AUTO_FILL", contextSourceId = source)),
            )
        }
    }

    @Test
    fun trendingSourceUsesMeaningfulGenre() {
        assertEquals(
            UiText.res(R.string.player_queue_source_trending_in, "Technology"),
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = "Technology",
                ),
            ),
        )
    }

    @Test
    fun trendingSourceFallsBackForGenericOrMissingGenre() {
        assertEquals(
            UiText.res(R.string.player_queue_source_trending),
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = "Podcast",
                ),
            ),
        )
        assertEquals(
            UiText.res(R.string.player_queue_source_trending),
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = " ",
                ),
            ),
        )
        assertEquals(
            UiText.res(R.string.player_queue_source_trending),
            queueSourceLabel(
                episode(
                    contextType = "AUTO_FILL",
                    contextSourceId = "trending",
                    podcastGenre = null,
                ),
            ),
        )
    }

    @Test
    fun unknownAutoFillSourceUsesGenericLabel() {
        assertEquals(
            UiText.res(R.string.player_queue_source_added),
            queueSourceLabel(
                episode(contextType = "AUTO_FILL", contextSourceId = "future_source"),
            ),
        )
        assertEquals(
            UiText.res(R.string.player_queue_source_added),
            queueSourceLabel(episode(contextType = "AUTO_FILL", contextSourceId = null)),
        )
    }
}
