package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CrossPromotionResolverTest {
    private fun show(id: String, title: String): Podcast = TestFixtures.podcast(id = id, title = title)

    @Test
    fun `search preserves full name including colon and with and checks every result`() = runTest {
        val name = "Science Weekly: Conversations with Alice"
        val expected = show("target", name)
        val resolver = CrossPromotionResolver(search = { query ->
            assertEquals(name, query)
            (1..12).map { show("other$it", "Unrelated $it") } + expected
        }, lookup = { null })
        assertEquals(expected, resolver.resolve(name))
    }

    @Test
    fun `host ambiguous matches and generic overlap never resolve`() = runTest {
        val resolver = CrossPromotionResolver({ emptyList() }, { null })
        assertNull(resolver.bestMatch(listOf(show("host", "Serial")), "Serial", "host"))
        assertNull(resolver.bestMatch(listOf(show("1", "Science Weekly"), show("2", "Science Weekly")), "Science Weekly", null))
        assertNull(resolver.bestMatch(listOf(show("1", "The Daily News")), "The Daily", null))
        assertEquals("2", resolver.bestMatch(listOf(show("1", "Science Weekly: Bonus"), show("2", "Science Weekly")), "Science Weekly", null)?.id)
    }

    @Test
    fun `explicit Apple show link resolves stable identity without guessing`() = runTest {
        val target = show("42", "Target Show")
        val links = ShowNotesParser.parse("<p>Listen to <a href='https://podcasts.apple.com/us/podcast/target/id9876'>Target Show</a></p>").links
        val resolver = CrossPromotionResolver(search = { error("Search should not run") }, lookup = { id ->
            assertEquals("itunes:9876", id)
            target
        })
        assertEquals(target, resolver.resolve("Target Show", "host", links))
    }

    @Test
    fun `multiple unnamed podcast links cannot select an arbitrary destination`() = runTest {
        val links = ShowNotesParser.parse("<p>Listen to Target Show on <a href='https://podcasts.apple.com/us/podcast/id123'>Apple Podcasts</a> or <a href='https://podcasts.apple.com/us/podcast/id456'>Apple Podcasts</a>.</p>").links
        val resolver = CrossPromotionResolver({ listOf(show("target", "Target Show")) }, { error("Ambiguous link must not be looked up") })
        assertEquals("target", resolver.resolve("Target Show", links = links)?.id)
    }

    @Test
    fun `network failure is retryable and cancellation propagates`() = runTest {
        var calls = 0
        val resolver = CrossPromotionResolver({
            calls++
            if (calls == 1) throw java.io.IOException("offline") else listOf(show("target", "Target Show"))
        }, { null })
        assertNull(resolver.resolve("Target Show"))
        assertEquals("target", resolver.resolve("Target Show")?.id)
        assertEquals(2, calls)
        val cancelled = CrossPromotionResolver({ throw CancellationException("cancel") }, { null })
        try {
            cancelled.resolve("Target Show")
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected: callers retain structured cancellation.
        }
    }

    @Test
    fun `failed Apple lookup falls back to an exact search match`() = runTest {
        val links = ShowNotesParser.parse("<a href='https://podcasts.apple.com/us/podcast/id123'>Target Show</a>").links
        val resolver = CrossPromotionResolver({ listOf(show("target", "Target Show")) }, { throw java.io.IOException("lookup offline") })
        assertEquals("target", resolver.resolve("Target Show", links = links)?.id)
    }

    @Test
    fun `empty results expire quickly and host identity scopes the cache`() = runTest {
        var time = 0L
        var calls = 0
        val resolver = CrossPromotionResolver({
            calls++
            if (calls == 1) emptyList() else listOf(show("target", "Target Show"))
        }, { null }, { time })
        assertNull(resolver.resolve("Target Show"))
        assertNull(resolver.resolve("Target Show"))
        assertEquals(1, calls)
        time = 60_001L
        assertEquals("target", resolver.resolve("Target Show")?.id)
        assertNull(resolver.resolve("Target Show", hostPodcastId = "target"))
        assertEquals(3, calls)
    }

    @Test
    fun `standalone listen now link resolves a verified title without search`() = runTest {
        val target = show("itunes:1828469754", "Conspiracy Theories, Cults, & Crimes")
        val name = "CONSPIRACY THEORIES, CULTS, AND CRIMES"
        listOf(
            "Listen Now: https://podcasts.apple.com/us/podcast/conspiracy-theories-cults-crimes/id1828469754",
            "<p><a href='https://podcasts.apple.com/us/podcast/id1828469754'>Listen Now</a></p>",
        ).forEach { description ->
            val resolver = CrossPromotionResolver({ error("Matching direct link should not search") }, { id ->
                assertEquals("itunes:1828469754", id)
                target
            })
            assertEquals(target, resolver.resolve(name, "host", ShowNotesParser.parse(description).links))
        }
    }

    @Test
    fun `lone generic link must match the name and cannot resolve the host`() = runTest {
        val links = ShowNotesParser.parse("https://podcasts.apple.com/us/podcast/id123").links
        listOf(show("other", "Unrelated Show"), show("host", "Target Show")).forEach { linked ->
            val resolver = CrossPromotionResolver({ emptyList() }, { linked })
            assertNull(resolver.resolve("Target Show", "host", links))
        }
        val resolver = CrossPromotionResolver({ listOf(show("correct", "Target Show")) }, { show("wrong", "Unrelated Show") })
        assertEquals("correct", resolver.resolve("Target Show", "host", links)?.id)
    }
}
