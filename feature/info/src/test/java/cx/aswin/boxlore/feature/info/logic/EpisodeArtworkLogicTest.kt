package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeArtworkLogicTest {
    @Test
    fun `prefers episode art then podcast fields then show fallback`() {
        val episode =
            TestFixtures.episode(id = "-1").copy(
                imageUrl = "",
                podcastImageUrl = "https://cdn/show.jpg",
            )
        assertEquals(
            "https://cdn/item.jpg",
            EpisodeArtworkLogic.listUrl(
                episode.copy(imageUrl = "https://cdn/item.jpg"),
                podcastImageUrl = "https://cdn/fallback.jpg",
            ),
        )
        assertEquals("https://cdn/show.jpg", EpisodeArtworkLogic.listUrl(episode))
        assertEquals(
            "https://cdn/fallback.jpg",
            EpisodeArtworkLogic.listUrl(
                episode.copy(podcastImageUrl = "  "),
                podcastImageUrl = "https://cdn/fallback.jpg",
            ),
        )
        assertNull(EpisodeArtworkLogic.listUrl(episode.copy(podcastImageUrl = null)))
    }
}
