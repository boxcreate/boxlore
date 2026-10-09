package cx.aswin.boxlore.feature.info.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PodcastEpisodeActionsTest {
    @Test
    fun `resumed episode keeps its listening progress`() {
        assertEquals(0.45f, podcastEpisodeCardProgress(isResume = true, isCompleted = false, progress = 0.45f))
    }

    @Test
    fun `completed and unstarted episodes do not reserve progress space`() {
        assertNull(podcastEpisodeCardProgress(isResume = true, isCompleted = true, progress = 1f))
        assertNull(podcastEpisodeCardProgress(isResume = false, isCompleted = false, progress = 0.45f))
        assertNull(podcastEpisodeCardProgress(isResume = true, isCompleted = false, progress = 0f))
    }

    @Test
    fun `invalid progress does not create an unusable indicator`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -0.1f).forEach {
            assertNull(podcastEpisodeCardProgress(isResume = true, isCompleted = false, progress = it))
        }
    }

    @Test
    fun `progress beyond duration is capped at the end of the track`() {
        assertEquals(1f, podcastEpisodeCardProgress(isResume = true, isCompleted = false, progress = 1.1f))
    }
}
