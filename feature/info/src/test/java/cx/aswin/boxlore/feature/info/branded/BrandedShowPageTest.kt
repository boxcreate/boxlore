package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrandedShowPageTest {
    private val ids = BrandedShowIds(podcastIndexId = "415434", podcastGuid = "b9daf664-eed0-5f5b-876a-22afdf28bd97", feedUrl = "https://feeds.example.com/show")
    private val page = FakePage(ids)

    @Test
    fun `matches the show by its Podcast Index id`() {
        assertTrue(ids.matches(TestFixtures.podcast(id = "415434")))
    }

    @Test
    fun `matches an RSS copy linked to the Podcast Index show`() {
        val rss = TestFixtures.podcast(id = "rss:abc", sourceType = Podcast.SOURCE_RSS).copy(linkedPodcastIndexId = "415434")
        assertTrue(ids.matches(rss))
    }

    @Test
    fun `matches by GUID regardless of case`() {
        assertTrue(ids.matches(TestFixtures.podcast(id = "other").copy(podcastGuid = "B9DAF664-EED0-5F5B-876A-22AFDF28BD97")))
    }

    @Test
    fun `matches the feed URL without scheme, www or trailing slash`() {
        assertTrue(ids.matches(TestFixtures.podcast(id = "other").copy(feedUrl = "http://www.FEEDS.example.com/show/")))
    }

    @Test
    fun `other shows keep the regular page`() {
        val other = TestFixtures.podcast(id = "999").copy(podcastGuid = "another", feedUrl = "https://feeds.example.com/another")
        assertFalse(ids.matches(other))
        assertNull(brandedShowPageFor(other, listOf(page)))
    }

    @Test
    fun `a linked id on a Podcast Index show does not count`() {
        assertFalse(ids.matches(TestFixtures.podcast(id = "999").copy(linkedPodcastIndexId = "415434")))
    }

    @Test
    fun `finds the page for a matching show and nothing before the show loads`() {
        assertSame(page, brandedShowPageFor(TestFixtures.podcast(id = "415434"), listOf(page)))
        assertNull(brandedShowPageFor(null, listOf(page)))
        assertNull(brandedShowPageFor(TestFixtures.podcast(id = "415434"), emptyList()))
    }

    @Test
    fun `chip row keeps a full touch target`() {
        assertEquals(48.dp, BrandedChipRowHeight)
    }

    private class FakePage(override val ids: BrandedShowIds) : BrandedShowPage {
        override val headerTop = 0.dp
        override fun headerToButtons(description: BrandedDescription) = 0.dp
        override fun style(base: ColorScheme, dark: Boolean): BrandedPageStyle = error("unused")

        @Composable
        override fun Header(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) = Unit

        @Composable
        override fun Tag(modifier: Modifier) = Unit

        @Composable
        override fun Spotlight(spotlight: BrandedSpotlight, modifier: Modifier) = Unit
    }
}
