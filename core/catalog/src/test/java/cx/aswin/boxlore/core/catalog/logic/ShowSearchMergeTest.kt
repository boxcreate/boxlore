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
    fun fillsMissingGenreFromSameIdWithoutReplacingCatalogMetadataOrOrder() {
        val catalog = Podcast("100", "Catalog title", "Catalog host", "catalog.jpg")
        val other = Podcast("200", "Other show", "Host", "", genre = "Comedy")
        val hybrid = catalog.copy(title = "Hybrid title", imageUrl = "hybrid.jpg", genre = "Technology")
        val merged = mergeShowSearchResults(listOf(catalog, other), listOf(hybrid))
        assertEquals(listOf(catalog.copy(genre = "Technology"), other), merged.catalog)
        assertTrue(merged.alsoFound.isEmpty())
    }

    @Test
    fun fillsMissingGenreAcrossExistingFeedUrlIdentityMatch() {
        val catalog = Podcast("100", "Show", "Host", "", genre = " podcasts ", feedUrl = "https://feeds.example/show")
        val hybrid = catalog.copy(id = "itunes:55", genre = "Science")
        val merged = mergeShowSearchResults(listOf(catalog), listOf(hybrid))
        assertEquals(listOf(catalog.copy(genre = "Science")), merged.catalog)
        assertTrue(merged.alsoFound.isEmpty())
    }

    @Test
    fun preservesKnownCatalogGenreAndDoesNotGuessFromTitles() {
        val known = Podcast("100", "Show", "Host", "", genre = "News")
        val missing = known.copy(id = "200", genre = "Podcast")
        val sameTitle = known.copy(id = "300", genre = "Comedy")
        val merged = mergeShowSearchResults(listOf(known, missing), listOf(known.copy(genre = "Science"), sameTitle))
        assertEquals(listOf(known, missing), merged.catalog)
        assertEquals(listOf(sameTitle), merged.alsoFound)
    }

    @Test
    fun leavesUnknownGenreWhenMatchingMetadataIsGenericOrConflicting() {
        val catalog = Podcast("100", "Show", "Host", "", feedUrl = "https://feeds.example/show")
        val generic = catalog.copy(genre = " ")
        assertEquals(listOf(catalog), mergeShowSearchResults(listOf(catalog), listOf(generic)).catalog)
        val conflicting = listOf(catalog.copy(genre = "Science"), catalog.copy(id = "itunes:55", genre = "News"))
        assertEquals(listOf(catalog), mergeShowSearchResults(listOf(catalog), conflicting).catalog)
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
