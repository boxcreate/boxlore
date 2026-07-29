package cx.aswin.boxlore.core.catalog.logic

import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShowSearchMergeTest {
    @Test
    fun prefersTypeaheadWhenSameFeedUrl() {
        val meili =
            Podcast(
                id = "745392",
                title = "Serial",
                artist = "NYT",
                imageUrl = "",
                feedUrl = "https://feeds.example/serial",
            )
        val hybridDupFeed =
            Podcast(
                id = "itunes:917918570",
                title = "Serial",
                artist = "NYT",
                imageUrl = "",
                feedUrl = "https://feeds.example/serial",
            )
        val other =
            Podcast(id = "999", title = "Serial Killers", artist = "X", imageUrl = "")

        val merged =
            mergeShowSearchResults(
                typeahead = listOf(meili),
                hybrid = listOf(hybridDupFeed, other),
            )

        assertEquals(1, merged.catalog.size)
        assertEquals("745392", merged.catalog.first().id)
        assertEquals(1, merged.alsoFound.size)
        assertEquals("999", merged.alsoFound.first().id)
        assertEquals(2, merged.all.size)
    }

    @Test
    fun emptyTypeaheadPutsHybridInAlsoFound() {
        val hybrid =
            listOf(
                Podcast(id = "1", title = "Niche Show", artist = "A", imageUrl = ""),
            )
        val merged = mergeShowSearchResults(typeahead = emptyList(), hybrid = hybrid)
        assertTrue(merged.catalog.isEmpty())
        assertEquals(1, merged.alsoFound.size)
    }

    @Test
    fun identityFallsBackToTitleArtist() {
        val a = Podcast(id = "0", title = "Foo", artist = "Bar", imageUrl = "")
        val b = Podcast(id = "", title = "Foo", artist = "Bar", imageUrl = "")
        assertEquals(podcastIdentityKeys(a), podcastIdentityKeys(b))
    }

    @Test
    fun absorbsAlternateKeysFromSkippedDuplicates() {
        // Meili hit with numeric id only; hybrid shares feed URL but also carries itunes: —
        // absorbing keys on skip prevents a later itunes-only hit from reappearing.
        val meili =
            Podcast(
                id = "100",
                title = "Show",
                artist = "Host",
                imageUrl = "",
                feedUrl = "https://feeds.example/show",
            )
        val hybridSameFeedWithItunes =
            Podcast(
                id = "itunes:55",
                title = "Show",
                artist = "Host",
                imageUrl = "",
                feedUrl = "https://feeds.example/show",
            )
        val laterItunesOnly =
            Podcast(
                id = "itunes:55",
                title = "Show Dup",
                artist = "Host",
                imageUrl = "",
            )

        val merged =
            mergeShowSearchResults(
                typeahead = listOf(meili),
                hybrid = listOf(hybridSameFeedWithItunes, laterItunesOnly),
            )

        assertEquals(1, merged.catalog.size)
        assertTrue(merged.alsoFound.isEmpty())
        assertEquals(1, merged.all.size)
    }
}
