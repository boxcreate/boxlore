package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.playback.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroStylePreviewTest {
    private fun session(id: String) = PlaybackSession(
        podcastId = "show-$id",
        episodeId = id,
        positionMs = 60_000L,
        durationMs = 120_000L,
        timestamp = 1L,
        episodeTitle = "Episode $id",
        podcastTitle = "Show $id",
        imageUrl = "https://art/$id.jpg",
        podcastImageUrl = "https://art/show-$id.jpg",
        audioUrl = "https://audio/$id.mp3",
    )

    @Test fun `previews use the listener's recent episodes when there are enough`() {
        val single = heroPreviewPodcasts(listOf(session("a")), collection = false, packageName = "app")
        assertEquals(listOf("Episode a"), single.map { it.latestEpisode?.title })
        val collection = heroPreviewPodcasts((1..6).map { session("$it") }, collection = true, packageName = "app")
        assertEquals(listOf("Episode 1", "Episode 2", "Episode 3", "Episode 4"), collection.map { it.latestEpisode?.title })
    }

    @Test fun `with nothing played yet the previews use bundled covers and sample names`() {
        val single = heroPreviewPodcasts(emptyList(), collection = false, packageName = "cx.aswin.boxlore")
        assertEquals(listOf("Radiolab"), single.map { it.title })
        assertTrue(single.first().imageUrl.startsWith("android.resource://cx.aswin.boxlore/"))
        // One played episode is not enough for a collection, so the collection shows samples.
        val collection = heroPreviewPodcasts(listOf(session("a")), collection = true, packageName = "cx.aswin.boxlore")
        assertEquals(listOf("Radiolab", "Hidden Brain", "Short Wave", "Ologies"), collection.map { it.title })
        assertTrue(collection.all { (it.resumeProgress ?: 0f) > 0f && (it.latestEpisode?.duration ?: 0) > 0 })
    }
}
