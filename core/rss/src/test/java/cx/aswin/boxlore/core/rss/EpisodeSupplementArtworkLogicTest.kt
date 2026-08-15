package cx.aswin.boxlore.core.rss

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeSupplementArtworkLogicTest {
    @Test
    fun `item art wins then channel then show`() {
        assertEquals(
            "https://cdn/item.jpg",
            EpisodeSupplementArtworkLogic.resolvedImageUrl(
                itemImageUrl = "https://cdn/item.jpg",
                channelImageUrl = "https://cdn/channel.jpg",
                showImageUrl = "https://cdn/show.jpg",
            ),
        )
        assertEquals(
            "https://cdn/channel.jpg",
            EpisodeSupplementArtworkLogic.resolvedImageUrl(
                itemImageUrl = "",
                channelImageUrl = "https://cdn/channel.jpg",
                showImageUrl = "https://cdn/show.jpg",
            ),
        )
        assertEquals(
            "https://cdn/show.jpg",
            EpisodeSupplementArtworkLogic.resolvedImageUrl(
                itemImageUrl = null,
                channelImageUrl = "  ",
                showImageUrl = "https://cdn/show.jpg",
            ),
        )
        assertNull(
            EpisodeSupplementArtworkLogic.resolvedImageUrl(
                itemImageUrl = "",
                channelImageUrl = null,
                showImageUrl = null,
            ),
        )
    }
}
