package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.ExactPodcastLookupResult
import cx.aswin.boxlore.core.catalog.PodcastIndexSearchResult
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpmlImportLogicTest {
    @Test
    fun `transient exact lookup failure defers instead of creating RSS fallback`() {
        val decision =
            OpmlImportLogic.finalCatalogDecision(
                opmlTitle = "Show",
                opmlXmlUrl = "https://feeds.example/show.xml",
                urlLookup = ExactPodcastLookupResult.Failed,
                guidLookup = ExactPodcastLookupResult.NotFound,
                titleSearch = PodcastIndexSearchResult.Success(emptyList()),
            )

        assertEquals(OpmlCatalogDecision.Deferred, decision)
    }

    @Test
    fun `RSS fallback requires settled exact lookups and successful empty search`() {
        val decision =
            OpmlImportLogic.finalCatalogDecision(
                opmlTitle = "Show",
                opmlXmlUrl = "https://feeds.example/show.xml",
                urlLookup = ExactPodcastLookupResult.NotFound,
                guidLookup = ExactPodcastLookupResult.NotFound,
                titleSearch = PodcastIndexSearchResult.Success(emptyList()),
            )

        assertEquals(OpmlCatalogDecision.ConfirmedAbsent, decision)
    }

    @Test
    fun `exact PI identity wins even when title search fails`() {
        val podcast = TestFixtures.podcast(id = "42")
        val decision =
            OpmlImportLogic.finalCatalogDecision(
                opmlTitle = "Show",
                opmlXmlUrl = "https://feeds.example/show.xml",
                urlLookup = ExactPodcastLookupResult.Found(podcast),
                guidLookup = ExactPodcastLookupResult.NotFound,
                titleSearch = PodcastIndexSearchResult.Failed,
            )

        assertEquals(
            podcast,
            assertInstanceOf(OpmlCatalogDecision.Found::class.java, decision).podcast,
        )
    }

    @Test
    fun `url lookup wins over title search`() {
        val byUrl = TestFixtures.podcast(id = "pi-url", title = "Show")
        val byTitle = TestFixtures.podcast(id = "pi-title", title = "Show")
        assertEquals(
            byUrl,
            OpmlImportLogic.catalogMatch(
                opmlTitle = "Show",
                opmlXmlUrl = "https://feeds.example/show.xml",
                urlLookup = byUrl,
                titleSearch = listOf(byTitle),
            ),
        )
    }

    @Test
    fun `rss url lookup is ignored so catalog title can still match`() {
        val rssHit =
            TestFixtures.rssPodcast(id = "rss:show", title = "Show").copy(
                sourceType = Podcast.SOURCE_RSS,
            )
        val catalog = TestFixtures.podcast(id = "42", title = "Show")
        assertEquals(
            catalog,
            OpmlImportLogic.catalogMatch(
                opmlTitle = "Show",
                opmlXmlUrl = "https://feeds.example/show.xml",
                urlLookup = rssHit,
                titleSearch = listOf(catalog),
            ),
        )
    }

    @Test
    fun `title search does not take the first unrelated hit`() {
        val unrelated = TestFixtures.podcast(id = "1", title = "Other Show")
        val match = TestFixtures.podcast(id = "2", title = "The Daily")
        assertEquals(
            match,
            OpmlImportLogic.catalogMatch(
                opmlTitle = "The Daily",
                opmlXmlUrl = "https://feeds.example/daily.xml",
                urlLookup = null,
                titleSearch = listOf(unrelated, match),
            ),
        )
    }

    @Test
    fun `feed url match wins when titles differ`() {
        val match =
            TestFixtures.podcast(id = "9", title = "Publisher Name").copy(
                feedUrl = "https://feeds.example/show.xml/",
            )
        assertEquals(
            match,
            OpmlImportLogic.catalogMatch(
                opmlTitle = "Show",
                opmlXmlUrl = "HTTPS://feeds.example/show.xml",
                urlLookup = null,
                titleSearch = listOf(match),
            ),
        )
    }

    @Test
    fun `no catalog match when search titles and urls differ`() {
        assertNull(
            OpmlImportLogic.catalogMatch(
                opmlTitle = "Show",
                opmlXmlUrl = "https://feeds.example/show.xml",
                urlLookup = null,
                titleSearch = listOf(TestFixtures.podcast(id = "1", title = "Something Else")),
            ),
        )
    }

    @Test
    fun `url lookup candidates include scheme slash and www variants`() {
        val candidates =
            OpmlImportLogic.urlLookupCandidates("http://www.feeds.example/show.xml/")
        assertTrue(candidates.contains("https://feeds.example/show.xml"))
        assertTrue(candidates.contains("http://www.feeds.example/show.xml"))
    }

    @Test
    fun `httpsFeedUrl rewrites http outlines`() {
        assertEquals(
            "https://feeds.example/show.xml",
            OpmlImportLogic.httpsFeedUrl("http://feeds.example/show.xml"),
        )
        assertEquals(
            "https://feeds.example/show.xml",
            OpmlImportLogic.httpsFeedUrl("https://feeds.example/show.xml"),
        )
        assertNull(OpmlImportLogic.httpsFeedUrl("not-a-url"))
    }

    @Test
    fun `preferLookup keeps Found over Failed and Failed over NotFound`() {
        val podcast = TestFixtures.podcast(id = "42")
        assertEquals(
            ExactPodcastLookupResult.Found(podcast),
            OpmlImportLogic.preferLookup(
                initial = ExactPodcastLookupResult.Failed,
                redirected = ExactPodcastLookupResult.Found(podcast),
            ),
        )
        assertEquals(
            ExactPodcastLookupResult.Failed,
            OpmlImportLogic.preferLookup(
                initial = ExactPodcastLookupResult.Failed,
                redirected = ExactPodcastLookupResult.NotFound,
            ),
        )
        assertEquals(
            ExactPodcastLookupResult.Failed,
            OpmlImportLogic.preferLookup(
                initial = ExactPodcastLookupResult.NotFound,
                redirected = ExactPodcastLookupResult.Failed,
            ),
        )
        assertEquals(
            ExactPodcastLookupResult.NotFound,
            OpmlImportLogic.preferLookup(
                initial = ExactPodcastLookupResult.NotFound,
                redirected = ExactPodcastLookupResult.NotFound,
            ),
        )
    }

    @Test
    fun `collapseCandidateLookups prefers Found then Failed then NotFound`() {
        val podcast = TestFixtures.podcast(id = "42")
        assertEquals(
            ExactPodcastLookupResult.Found(podcast),
            OpmlImportLogic.collapseCandidateLookups(
                listOf(
                    ExactPodcastLookupResult.NotFound,
                    ExactPodcastLookupResult.Failed,
                    ExactPodcastLookupResult.Found(podcast),
                ),
            ),
        )
        assertEquals(
            ExactPodcastLookupResult.Failed,
            OpmlImportLogic.collapseCandidateLookups(
                listOf(ExactPodcastLookupResult.NotFound, ExactPodcastLookupResult.Failed),
            ),
        )
        assertEquals(
            ExactPodcastLookupResult.NotFound,
            OpmlImportLogic.collapseCandidateLookups(
                listOf(ExactPodcastLookupResult.NotFound),
            ),
        )
    }
}
