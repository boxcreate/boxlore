package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSupplementDisconnectLogicTest {
    @Test
    fun `feed-only episodes mean disconnect`() {
        assertTrue(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 3,
                newestFeedPublishedDate = 100L,
                newestBaselinePublishedDate = 200L,
            ),
        )
    }

    @Test
    fun `newer feed tip alone means disconnect`() {
        assertTrue(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 0,
                newestFeedPublishedDate = 300L,
                newestBaselinePublishedDate = 100L,
            ),
        )
    }

    @Test
    fun `matching tip and no feed-only means no disconnect`() {
        assertFalse(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 0,
                newestFeedPublishedDate = 100L,
                newestBaselinePublishedDate = 100L,
            ),
        )
        assertFalse(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 0,
                newestFeedPublishedDate = 50L,
                newestBaselinePublishedDate = 100L,
            ),
        )
    }

    @Test
    fun `empty baseline with a feed tip means disconnect`() {
        assertTrue(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 0,
                newestFeedPublishedDate = 10L,
                newestBaselinePublishedDate = 0L,
            ),
        )
    }

    @Test
    fun `unknown feed tip date without extras is not a disconnect`() {
        assertFalse(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 0,
                newestFeedPublishedDate = 0L,
                newestBaselinePublishedDate = 100L,
            ),
        )
        assertFalse(
            EpisodeSupplementDisconnectLogic.shouldOptIn(
                feedOnlyCount = 0,
                newestFeedPublishedDate = -1L,
                newestBaselinePublishedDate = 0L,
            ),
        )
    }
}
