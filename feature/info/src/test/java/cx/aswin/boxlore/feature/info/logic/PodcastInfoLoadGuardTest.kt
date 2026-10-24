package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoLoadGuardTest {
    private val loaded =
        PodcastInfoUiState.Success(
            podcast = TestFixtures.podcast(id = "rss:abc"),
            episodes = emptyList(),
            isSubscribed = true,
            searchQuery = "night",
        )

    @Test
    fun `a recreated page does not reload a show whose followed rss feed replaced the route id`() {
        assertTrue(
            PodcastInfoLoadGuard.shouldSkipLoad(
                routePodcastId = "123",
                requestedPodcastId = "123",
                currentPodcastId = "rss:abc",
                state = loaded,
            ),
        )
    }

    @Test
    fun `a recreated page does not reload a show whose catalog id differs from the route id`() {
        assertTrue(
            PodcastInfoLoadGuard.shouldSkipLoad(
                routePodcastId = "url:https://example.com/feed",
                requestedPodcastId = "url:https://example.com/feed",
                currentPodcastId = "987",
                state = loaded,
            ),
        )
    }

    @Test
    fun `an error page is kept until the listener retries`() {
        assertTrue(PodcastInfoLoadGuard.shouldSkipLoad("123", "123", "123", PodcastInfoUiState.Error))
    }

    @Test
    fun `a first load, a retry, a load still running and another show all load`() {
        assertFalse(PodcastInfoLoadGuard.shouldSkipLoad("123", requestedPodcastId = null, currentPodcastId = "", state = PodcastInfoUiState.Loading))
        // retryLoad clears both IDs before it loads again.
        assertFalse(PodcastInfoLoadGuard.shouldSkipLoad("123", requestedPodcastId = null, currentPodcastId = "", state = PodcastInfoUiState.Error))
        assertFalse(PodcastInfoLoadGuard.shouldSkipLoad("123", "123", "rss:abc", PodcastInfoUiState.Loading))
        assertFalse(PodcastInfoLoadGuard.shouldSkipLoad("456", "123", "rss:abc", loaded))
    }
}
