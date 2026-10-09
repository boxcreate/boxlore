package cx.aswin.boxlore.core.catalog.shownotes

import cx.aswin.boxlore.core.model.EpisodeLinkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShowNotesParserTest {
    @Test
    fun `platform anchor labels are not profile titles and real handles survive`() {
        val profiles = listOf(
            Triple("https://instagram.com/radiolab", "Instagram", "@radiolab"),
            Triple("https://facebook.com/radiolab", "Visit Facebook", "@radiolab"),
            Triple("https://threads.net/@radiolab", "Threads", "@radiolab"),
            Triple("https://twitter.com/radiolab", "Twitter", "@radiolab"),
            Triple("https://x.com/radiolab", "Follow us on X", "@radiolab"),
            Triple("https://bsky.app/profile/radiolab.org", "Bluesky", "@radiolab.org"),
            Triple("https://linkedin.com/company/radiolab", "LinkedIn", "@radiolab"),
            Triple("https://tiktok.com/@radiolab", "TikTok", "@radiolab"),
            Triple("https://instagram.com/instagram", "Instagram", "@instagram"),
        )
        profiles.forEach { (url, label, handle) ->
            val link = ShowNotesParser.parse("<a href='$url'>$label</a>").links.single()
            assertEquals(null, link.title, url)
            assertEquals(handle, link.handle, url)
        }
    }

    @Test
    fun `social root login and post routes do not become usernames`() {
        listOf("https://instagram.com/", "https://instagram.com/accounts/login", "https://instagram.com/P/123", "https://facebook.com/profile.php?id=123", "https://facebook.com/share/123", "https://facebook.com/groups/123", "https://threads.com/intent/post", "https://x.com/i/flow/login", "https://linkedin.com/feed", "https://bsky.app/search", "https://tiktok.com/explore").forEach { url ->
            assertEquals(null, ShowNotesParser.parse(url).links.single().handle, url)
        }
    }

    @Test
    fun `meaningful publisher titles and names resembling platforms are preserved`() {
        listOf(
            "https://instagram.com/radiolab" to "Radiolab behind the scenes",
            "https://threads.com/@history" to "Threads of history",
            "https://instagram.com.evil.org/radiolab" to "Instagram",
            "https://example.org/news/youtube" to "YouTube",
        ).forEach { (url, label) ->
            assertEquals(label, ShowNotesParser.parse("<a href='$url'>$label</a>").links.single().title)
        }
    }

    @Test
    fun `HTML anchors preserve labels entities and known relative origin`() {
        val notes = ShowNotesParser.parse("<p><a href=/news/story?x=1&amp;y=2>Our research &amp; findings</a></p>", "https://radiolab.org/episodes/intro")
        assertEquals("Our research & findings", notes.links.single().title)
        assertEquals("https://radiolab.org/news/story?x=1&y=2", notes.links.single().url)
        assertEquals(EpisodeLinkKind.ARTICLE, notes.links.single().kind)
        assertTrue(ShowNotesParser.parse("<a href=/about>About us</a>").links.isEmpty())
    }

    @Test
    fun `raw URL punctuation and generic anchors retain meaningful destinations`() {
        val notes = ShowNotesParser.parse("<a href=https://radiolab.org>Visit our website</a><p>Research (https://example.org/paper_(2026)). And www.example.org/about!</p>")
        assertEquals(listOf("radiolab.org", "example.org", "www.example.org".removePrefix("www.")), notes.links.map { it.host })
        assertEquals(null, notes.links.first().title)
        assertEquals("https://example.org/paper_(2026)", notes.links[1].url)
        assertEquals("https://www.example.org/about", notes.links[2].url)
    }

    @Test
    fun `tracking duplicates collapse while case and fragment distinctions survive`() {
        val notes = ShowNotesParser.parse(
            """
            <a href="https://EXAMPLE.org/Paper?a=2&amp;b=1&amp;utm_source=feed">Research</a>
            https://example.org/Paper?b=1&amp;a=2
            https://example.org/paper?a=2&amp;b=1
            https://example.org/Paper?a=2&amp;b=1#methods
            https://example.org/Paper?a=2&amp;b=1#results
        """.trimIndent()
        )
        assertEquals(4, notes.links.size)
        assertEquals("Research", notes.links.first().title)
        assertTrue(notes.links.first().url.contains("utm_source"))
    }

    @Test
    fun `platform classification uses domain boundaries and path purpose`() {
        val urls = listOf("https://youtube.com/watch?v=1", "https://youtube.com/@radiolab", "https://youtube.com.evil.org/watch?v=1", "https://notyoutube.com/watch", "https://open.spotify.com/show/123", "https://open.spotify.com/track/123", "https://reddit.com/r/podcasts", "https://patreon.com/radiolab", "mailto:hello@radiolab.org")
        val links = ShowNotesParser.parse(urls.joinToString("\n") { "<a href='$it'>$it</a>" }).links
        assertEquals(listOf(EpisodeLinkKind.VIDEO, EpisodeLinkKind.SOCIAL, EpisodeLinkKind.WEBSITE, EpisodeLinkKind.WEBSITE, EpisodeLinkKind.PODCAST, EpisodeLinkKind.WEBSITE, EpisodeLinkKind.COMMUNITY, EpisodeLinkKind.SUPPORT, EpisodeLinkKind.EMAIL), links.map { it.kind })
        assertEquals("@radiolab", links[1].handle)
        assertEquals("r/podcasts", links[6].handle)
        assertEquals(null, links[2].platform)
    }

    @Test
    fun `unsafe and infrastructure links do not become resources`() {
        val notes = ShowNotesParser.parse(
            """
            <script>https://bad.org</script><style>https://bad.org</style>
            <a href="javascript:alert(1)">Unsafe</a><a href="https://user:pass@example.org">Private</a>
            <a href="https://cdn.podtrac.com/audio.mp3">Audio</a><a href="https://example.org/feed.xml">Feed</a>
            <a href="https://podtrac.com.evil.org/about">Real site</a>
        """.trimIndent()
        )
        assertEquals("podtrac.com.evil.org", notes.links.single().host)
        assertFalse(notes.html.contains("javascript:"))
        assertFalse(notes.plainText.contains("bad.org"))
    }

    @Test
    fun `chapters support title first long minutes and ignore prose invalid times and duplicates`() {
        val notes = ShowNotesParser.parse(
            """
            <p>We met at 12:30 yesterday.</p>
            <p>00:00 — Introduction</p><p>00:00 — Duplicate</p>
            <p>The experiment — 12:30</p><p>90:05 Long episode</p>
            <p>01:60:00 Invalid</p><p>99:99 Invalid</p><p>02:00:00 After duration</p>
        """.trimIndent(),
            durationSeconds = 6_000
        )
        assertEquals(listOf(0.0, 750.0, 5405.0), notes.chapters.map { it.startTime })
        assertEquals(listOf("Introduction", "The experiment", "Long episode"), notes.chapters.map { it.title })
        assertTrue(notes.html.contains("We met at 12:30 yesterday."))
        assertEquals(3, Regex("play-position:").findAll(notes.html).count())
    }

    @Test
    fun `timestamps never rewrite attributes existing links or inline prose`() {
        val notes = ShowNotesParser.parse(
            """
            <p><strong>00:00</strong> Introduction</p><p>00:30 Interview</p>
            <p>We recorded at 00:30 today.</p><a href="https://example.org/00:30">00:30 External</a>
            <code>00:30 Example</code>
        """.trimIndent()
        )
        assertEquals(2, Regex("play-position:").findAll(notes.html).count())
        assertTrue(notes.html.contains("https://example.org/00:30"))
        assertTrue(notes.html.contains("We recorded at 00:30 today."))
        assertTrue(notes.html.contains("<code>00:30 Example</code>"))
    }

    @Test
    fun `single clock time is not a chapter list`() {
        assertTrue(ShowNotesParser.parse("<p>12:30 Lunch break</p>").chapters.isEmpty())
    }

    @Test
    fun `markup and whitespace without readable notes remain blank`() {
        listOf(" ", "<p> &nbsp; <br></p>", "<script>hidden()</script><style>hidden</style>", "<div><span></span></div>").forEach {
            assertTrue(ShowNotesParser.parse(it).plainText.isBlank(), it)
        }
    }

    @Test
    fun `generic destination labels remain filtered after pattern simplification`() {
        listOf("Click here", "Our website", "The website", "Visit website", "Visit our website", "Visit the website", "Read more", "Listen here", "www.example.org", "HTTPS://example.org").forEach {
            assertEquals(null, ShowNotesParser.parse("<a href='https://example.org'>$it</a>").links.single().title, it)
        }
    }
}
