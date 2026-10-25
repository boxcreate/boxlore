package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.IdentityFixtureFeed.EP1_PADDED_GUID
import cx.aswin.boxlore.core.rss.IdentityFixtureFeed.LARGE_ROWS
import cx.aswin.boxlore.core.rss.IdentityFixtureFeed.SHOW_ID
import cx.aswin.boxlore.core.rss.IdentityFixtureFeed.SMALL_ROWS
import cx.aswin.boxlore.core.rss.IdentityFixtureFeed.namespace
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Episode identity follows the feed document: the first item wins a duplicate key, GUID-less items
 * are keyed on their raw enclosure URL, and every stored ID stays where it is.
 */
class FeedDocumentOrderTest {
    private val ep2 = "https://cdn.example.com/ep2.mp3"
    private val ep2Hq = "https://cdn.example.com/ep2-hq.m4a"
    private val ep0a = "https://cdn.example.com/ep0-a.mp3"
    private val ep0b = "https://cdn.example.com/ep0-b.mp3"

    @Test
    fun `a duplicate GUID whose later copy is newer keeps the first copy`() = runTest {
        for (bytes in listOf(IdentityFixtureFeed.small(), IdentityFixtureFeed.large())) {
            val parsed = IdentityFixtureFeed.parse(bytes)
            val ep4 = parsed.items.single { it.catalogKey == "ep-4" }

            assertEquals("Episode Four", ep4.episode.title)
            assertEquals("https://cdn.example.com/ep4.mp3", ep4.episode.audioUrl)
            assertEquals(1, ep4.documentIndex)
        }
    }

    @Test
    fun `persist keeps the first item in the document even when the list is newest first`() {
        val first = item(guid = "dup", title = "First", published = 10L, index = 0)
        val repost = item(guid = "dup", title = "Repost", published = 20L, index = 1, audio = "https://cdn.example/repost.mp3")

        val rows = persist(listOf(repost, first))

        assertEquals(listOf("First"), rows.map { it.title })
        assertEquals("dup", rows.single().guid)
    }

    @Test
    fun `GUID-less items where the parsers pick different URLs are one episode keyed on the enclosure`() = runTest {
        val small = IdentityFixtureFeed.parse(IdentityFixtureFeed.small())
        val large = IdentityFixtureFeed.parse(IdentityFixtureFeed.large())

        // Both parsers read ep2: the library picks the enclosure, the streaming parser the later media:content.
        val smallEp2 = small.items.single { it.episode.title.startsWith("Episode Two") }
        val largeEp2 = large.items.single { it.episode.title.startsWith("Episode Two") }
        assertEquals(ep2, smallEp2.catalogKey)
        assertEquals(ep2, largeEp2.catalogKey)
        assertEquals(listOf(ep2Hq), smallEp2.legacyKeys)
        assertEquals(listOf(ep2Hq), largeEp2.legacyKeys)
        // IDs are minted as before, from the audio address each parser picked.
        assertEquals("-1745259125871161974", smallEp2.episode.episodeId)
        assertEquals("-5426929428811007990", largeEp2.episode.episodeId)

        // ep0 has two enclosures; the library reports only the last one, the key is the first.
        val smallEp0 = small.items.single { it.episode.title.startsWith("Episode Zero") }
        val largeEp0 = large.items.single { it.episode.title.startsWith("Episode Zero") }
        assertEquals(ep0a, smallEp0.catalogKey)
        assertEquals(ep0a, largeEp0.catalogKey)
        assertEquals(listOf(ep0b), smallEp0.legacyKeys)

        assertEquals(6, small.episodes.size)
        assertEquals(small.items.map { it.catalogKey }.toSet(), large.items.map { it.catalogKey }.toSet())
    }

    @Test
    fun `a feed crossing 4 MiB keeps every stored ID in both directions`() = runTest {
        val small = IdentityFixtureFeed.parse(IdentityFixtureFeed.small())
        val large = IdentityFixtureFeed.parse(IdentityFixtureFeed.large())

        for ((first, second) in listOf(small to large, large to small)) {
            val stored = persist(first.items)
            val reread = persist(second.items, existing = stored.map { LocalEpisodeIdentity(it.episodeId, it.guid, it.audioUrl) })

            assertEquals(stored.map { it.episodeId to it.guid }.toSet(), reread.map { it.episodeId to it.guid }.toSet())
        }
    }

    @Test
    fun `stored IDs are identical before and after on the fixture feed`() = runTest {
        // Each item updates the row an older build stored for it under the same read: which row
        // goes with which item, not only that every ID is one of the stored ones.
        val smallExpected =
            mapOf(
                "Episode Five" to ("-1620196808107782941" to "ep-5"),
                "Episode Four" to ("-4092870849651754619" to "ep-4"),
                "Episode Three Without A GUID" to ("-230289529853465550" to "https://cdn.example.com/ep3.mp3"),
                "Episode Two With Two Media Elements" to ("-1745259125871161974" to ep2),
                "Episode One With A Padded GUID" to ("-4473233887734360468" to "ep-1"),
                "Episode Zero With Two Enclosures" to ("-171063312026563321" to ep0a),
            )
        val largeExpected =
            smallExpected +
                mapOf(
                    "Episode Two With Two Media Elements" to ("-5426929428811007990" to ep2Hq),
                    "Episode Zero With Two Enclosures" to ("-5875828860057459777" to ep0b),
                )
        val cases =
            listOf(
                Triple(IdentityFixtureFeed.small(), SMALL_ROWS, smallExpected),
                Triple(IdentityFixtureFeed.large(), LARGE_ROWS, largeExpected),
            )
        for ((bytes, before, expected) in cases) {
            val parsed = IdentityFixtureFeed.parse(bytes)

            val rows = persist(parsed.items, existing = before.map { (id, key) -> LocalEpisodeIdentity(id, key, audioFor(key)) })

            assertEquals(expected, rows.associate { it.title to (it.episodeId to it.guid) })
        }
        // The two rows older builds stored for ep2 and ep0 on the small read; the joined read updates
        // one of each pair. The other is no longer written: no row the persist returns carries it.
        val smallRows = persist(IdentityFixtureFeed.parse(IdentityFixtureFeed.small()).items, existing = SMALL_ROWS.map { (id, key) -> LocalEpisodeIdentity(id, key, audioFor(key)) })
        assertEquals(SMALL_ROWS.toSet() - IdentityFixtureFeed.SMALL_ORPHANS.toSet(), smallRows.map { it.episodeId to it.guid }.toSet())
    }

    @Test
    fun `rows stored on one side of 4 MiB keep their IDs when the feed is read on the other side`() = runTest {
        // A device stored the feed while it was under 4 MiB (both parsers) and reads it over 4 MiB, or the reverse.
        val cases =
            listOf(
                IdentityFixtureFeed.large() to SMALL_ROWS,
                IdentityFixtureFeed.small() to LARGE_ROWS,
            )
        for ((bytes, before) in cases) {
            val parsed = IdentityFixtureFeed.parse(bytes)

            val rows = persist(parsed.items, existing = before.map { (id, key) -> LocalEpisodeIdentity(id, key, audioFor(key)) })

            assertTrue(before.containsAll(rows.map { it.episodeId to it.guid }), "no new ID or key: $rows")
            assertEquals(parsed.items.size, rows.size)
        }
    }

    @Test
    fun `a read by the library parser alone finds the rows a joined read stored`() = runTest {
        for (bytes in listOf(IdentityFixtureFeed.small(), IdentityFixtureFeed.sharedEnclosure())) {
            val parsed = IdentityFixtureFeed.parse(bytes)
            val stored = persist(parsed.items)

            val reread = persist(IdentityFixtureFeed.libraryOnly(parsed), existing = stored.map { LocalEpisodeIdentity(it.episodeId, it.guid, it.audioUrl) })

            assertEquals(stored.map { it.episodeId to it.guid }.toSet(), reread.map { it.episodeId to it.guid }.toSet())
        }
        // ep0's library read is keyed on the audio address the library picked, not the last enclosure.
        val libraryEp0 = IdentityFixtureFeed.libraryOnly(IdentityFixtureFeed.parse(IdentityFixtureFeed.small())).single { it.episode.title.startsWith("Episode Zero") }
        assertEquals(ep0a, libraryEp0.catalogKey)
    }

    @Test
    fun `GUID-less items sharing a cover image or a trailer enclosure stay apart`() = runTest {
        val cover = "https://cdn.example.com/cover.jpg"
        val small = IdentityFixtureFeed.parse(IdentityFixtureFeed.sharedEnclosure())
        val large = IdentityFixtureFeed.parse(IdentityFixtureFeed.padded(IdentityFixtureFeed.sharedEnclosure()))

        for (parsed in listOf(small, large)) {
            val byTitle = parsed.items.filterNot { it.catalogKey == "https://cdn.example.com/trailer.mp3" }.associateBy { it.episode.title.substringAfter("Episode ").first() }
            // A non-audio enclosure never keys an item; a shared trailer leaves each item on its own audio.
            assertEquals(
                mapOf('A' to "a.mp3", 'B' to "b.mp3", 'C' to "c.mp3", 'D' to "d.mp3").mapValues { "https://cdn.example.com/${it.value}" },
                byTitle.mapValues { it.value.catalogKey },
            )
            assertTrue(parsed.items.none { it.catalogKey == cover })
            assertEquals(4, byTitle.values.map { it.episode.episodeId }.toSet().size)
        }
        // The same items get the same IDs whichever parser read the feed.
        val ids = { parsed: ParsedRssFeed -> parsed.items.associate { it.catalogKey to it.episode.episodeId } }
        assertEquals(ids(large), ids(small).filterKeys { it in ids(large) })
    }

    @Test
    fun `true RSS rows keep their stored IDs on the fixture feed`() = runTest {
        // Both sides of 4 MiB, from rows stored on the same side and on the other side.
        for (before in listOf(SMALL_ROWS, LARGE_ROWS)) {
            for (bytes in listOf(IdentityFixtureFeed.small(), IdentityFixtureFeed.large())) {
                val parsed = IdentityFixtureFeed.parse(bytes)
                val stored = before.map { (id, key) -> RssEpisodeIdentity(id, key.takeUnless { it.startsWith("https://") }, audioFor(key)) }

                val ids = StickyRssEpisodeRemap.remap(parsed.items, stored).map { it.episodeId }

                assertTrue(before.map { it.first }.containsAll(ids), "every row keeps a stored ID: $ids")
                assertEquals(ids.size, ids.toSet().size)
                assertEquals(parsed.items.size, ids.size)
            }
        }
    }

    @Test
    fun `a new release sharing a cover enclosure never takes another true RSS episode's ID`() = runTest {
        val first = StickyRssEpisodeRemap.remap(IdentityFixtureFeed.parse(IdentityFixtureFeed.sharedEnclosure()).items, existing = emptyList())
        val stored = first.map { RssEpisodeIdentity(it.episodeId, it.guid, it.audioUrl) }

        val next = StickyRssEpisodeRemap.remap(IdentityFixtureFeed.parse(IdentityFixtureFeed.sharedEnclosureWithNewRelease()).items, stored)

        assertEquals(first.map { it.title to it.episodeId }.toSet(), next.filterNot { it.title.startsWith("Episode E") }.map { it.title to it.episodeId }.toSet())
        assertTrue(next.single { it.title.startsWith("Episode E") }.episodeId !in first.map { it.episodeId })
    }

    @Test
    fun `NBSP and U+180E padding are trimmed with the recipe set`() = runTest {
        val padded = item(guid = EP1_PADDED_GUID, title = "Padded", published = 10L, index = 0)
        val plain = item(guid = "ep-1", title = "Plain", published = 20L, index = 1, audio = "https://cdn.example/plain.mp3")

        assertEquals(EpisodeRecipeId.trimKey(EP1_PADDED_GUID), padded.catalogKey)
        assertEquals("ep-1", padded.catalogKey)
        assertEquals(listOf("Padded"), FeedDocumentOrder.firstInDocument(listOf(plain, padded)).map { it.episode.title })
        assertEquals(listOf("Plain"), FeedDocumentOrder.firstInDocument(listOf(plain.copy(documentIndex = 0), padded.copy(documentIndex = 1))).map { it.episode.title })

        // The fixture feed's padded item comes first in the document, before a newer plain copy.
        val parsed = IdentityFixtureFeed.parse(IdentityFixtureFeed.small())
        assertEquals("Episode One With A Padded GUID", parsed.items.single { it.catalogKey == "ep-1" }.episode.title)
        val row = persist(parsed.items, existing = SMALL_ROWS.map { (id, key) -> LocalEpisodeIdentity(id, key, audioFor(key)) }).single { it.guid == "ep-1" }
        assertEquals("-4473233887734360468", row.episodeId)
    }

    @Test
    fun `an exact key keeps its row ahead of an item that only matches it by an older key`() {
        val stored = listOf(LocalEpisodeIdentity("-77", "https://cdn.example/a.mp3", "https://cdn.example/a.mp3"))
        // X declares enclosure R but older builds keyed it by its audio A; Y declares A itself. X comes first.
        val x = item(guid = null, title = "X", published = 10L, index = 0, audio = "https://cdn.example/a.mp3", enclosures = listOf("https://cdn.example/r.mp3"))
        val y = item(guid = null, title = "Y", published = 20L, index = 1, audio = "https://cdn.example/a.mp3", enclosures = listOf("https://cdn.example/a.mp3"))

        val matches = StoredRowMatch.resolve(listOf(x, y), StoredRowMatch.StoredRows.of(listOf(Triple("-77", null, "https://cdn.example/a.mp3"))))

        assertEquals(StoredRowMatch("https://cdn.example/r.mp3", null), matches[0])
        assertEquals(StoredRowMatch("https://cdn.example/a.mp3", "-77"), matches[1])
        assertEquals("-77", persist(listOf(x, y), existing = stored).single { it.title == "Y" }.episodeId)
    }

    @Test
    fun `a row found by an older key keeps that key`() {
        val stored = listOf(LocalEpisodeIdentity("-77", "https://cdn.example/a.mp3", "https://cdn.example/a.mp3"))
        val x = item(guid = null, title = "X", published = 10L, index = 0, audio = "https://cdn.example/a.mp3", enclosures = listOf("https://cdn.example/r.mp3"))

        val row = persist(listOf(x), existing = stored).single()

        assertEquals("-77", row.episodeId)
        assertEquals("https://cdn.example/a.mp3", row.guid)
    }

    @Test
    fun `stored rows listed after the feed lose a shared key to the feed item`() {
        val feedItem = item(guid = "g", title = "From the feed", published = 10L, index = 0)
        val storedRow = feedItem.episode.copy(episodeId = "-5", title = "Stored")
        val combined = listOf(feedItem) + listOf(storedRow).asParsedFeedItems(firstIndex = 1)

        assertEquals(listOf("From the feed"), persist(combined).map { it.title })
    }

    @Test
    fun `an item with no GUID and no enclosure is keyed on its audio address`() {
        val mediaOnly = item(guid = null, title = "Media", published = 10L, index = 0, audio = " https://cdn.example/m.mp3 ")

        assertEquals("https://cdn.example/m.mp3", mediaOnly.catalogKey)
        assertTrue(mediaOnly.legacyKeys.isEmpty())
        assertNull(StickyEpisodeIdentity.feedItemKey(" ", "\u00A0", "\u180E"))
    }

    private fun persist(items: List<ParsedFeedItem>, existing: List<LocalEpisodeIdentity> = emptyList()) = LocalEpisodeCatalogPersist.toLocalEpisodes(
        podcastIndexId = SHOW_ID,
        rssNamespaceId = namespace,
        parsed = items,
        existing = existing,
        piBaseline = null,
        channelImageUrl = null,
        showImageUrl = null,
    )

    private fun audioFor(key: String): String = if (key.startsWith("https://")) key else "https://cdn.example.com/$key.mp3"

    @Suppress("LongParameterList")
    private fun item(
        guid: String?,
        title: String,
        published: Long,
        index: Int,
        audio: String = "https://cdn.example/a.mp3",
        enclosures: List<String> = emptyList(),
    ): ParsedFeedItem {
        val episode =
            RssEpisodeEntity(
                episodeId = RssIdGenerator.episodeIdForPodcast(namespace, guid, audio, published, title),
                podcastId = namespace,
                guid = guid,
                title = title,
                description = "",
                audioUrl = audio,
                imageUrl = null,
                duration = 60,
                publishedDate = published,
                chaptersUrl = null,
                transcriptUrl = null,
                transcripts = null,
                persons = null,
                seasonNumber = null,
                episodeNumber = null,
                episodeType = null,
                enclosureType = "audio/mpeg",
            )
        return ParsedFeedItem(episode, index, enclosureUrls = enclosures)
    }
}
