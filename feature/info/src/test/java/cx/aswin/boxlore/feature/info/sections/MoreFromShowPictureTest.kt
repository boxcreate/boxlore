package cx.aswin.boxlore.feature.info.sections

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MoreFromShowPictureTest {
    private val episode = TestFixtures.episode(id = "1")

    @Test
    fun `the row shows the page episode's show cover`() {
        assertEquals("https://art/show.jpg", moreFromShowPicture(episode.copy(podcastImageUrl = "https://art/show.jpg"), emptyList()))
    }

    @Test
    fun `without one it takes the cover from the show's other episodes`() {
        // The row showed the placeholder on every show when the page episode came without its show's cover.
        val related = listOf(TestFixtures.episode(id = "2").copy(podcastImageUrl = " "), TestFixtures.episode(id = "3").copy(podcastImageUrl = "https://art/show.jpg"))
        assertEquals("https://art/show.jpg", moreFromShowPicture(episode.copy(podcastImageUrl = null), related))
        assertNull(moreFromShowPicture(episode.copy(podcastImageUrl = ""), emptyList()))
    }
}
