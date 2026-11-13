package cx.aswin.boxlore.core.wearlink

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WearLinkCodecTest {
    private fun row(index: Int) =
        ListeningHistorySyncDto(
            episodeId = "episode-$index",
            podcastId = "show-${index % 7}",
            episodeTitle = "Episode number $index with a title of ordinary length",
            episodeAudioUrl = "https://cdn.example.org/audio/${index * 7919}-${index * 104729}.mp3",
            progressMs = index * 1_000L,
            durationMs = 2_400_000L,
            lastPlayedAt = 1_700_000_000_000L + index,
            updatedAt = 1_700_000_000_000L + index,
            recipeId = 4_503_599_627_370_496L + index,
            catalogKey = "guid-$index",
        )

    private fun show(index: Int) =
        WearLinkShow(
            podcastId = (9_000_000 + index * 7919).toString(),
            title = "Show $index ${(index * 2654435761L).toString(36)}",
            author = "Publisher ${(index * 40503L).toString(36)}",
            imageUrl = "https://img.example.org/${(index * 2246822519L).toString(36)}/cover-${index * 31}.jpg",
        )

    @Test
    fun `every link value survives packing`() {
        val library = PhoneLibrary(shows = listOf(show(1), show(2)), history = listOf(row(1)), acked = mapOf("watch" to 12L), sentAt = 5L)
        val changes =
            WatchChanges(
                watchId = "watch",
                revision = 4L,
                subscriptions = listOf(WatchSubscriptionChange(3L, UserSubscriptionSyncDto("9", subscribedAt = 7L, updatedAt = 7L), show(9))),
                history = listOf(WatchHistoryChange(4L, row(2))),
                storedBytes = 123L,
                downloads = 2,
            )
        val playing = PhoneNowPlaying(WearLinkEpisode("e", "s", "Title", durationMs = 10L, recipeId = 5L), isPlaying = true, positionMs = 3L, speed = 1.5f, at = 9L)
        val command = WearLinkCommand(WearLinkCommand.PLAY_EPISODE, positionMs = 42L, episode = playing.episode)

        assertEquals(library, WearLinkCodec.decode<PhoneLibrary>(WearLinkCodec.encode(library)))
        assertEquals(changes, WearLinkCodec.decode<WatchChanges>(WearLinkCodec.encode(changes)))
        assertEquals(playing, WearLinkCodec.decode<PhoneNowPlaying>(WearLinkCodec.encode(playing)))
        assertEquals(command, WearLinkCodec.decode<WearLinkCommand>(WearLinkCodec.encode(command)))
        assertEquals(WearLinkSettings("List", 250, 8L), WearLinkCodec.decode<WearLinkSettings>(WearLinkCodec.encode(WearLinkSettings("List", 250, 8L))))
        assertEquals(EpisodeListReply("s", listOf(playing.episode!!)), WearLinkCodec.decode<EpisodeListReply>(WearLinkCodec.encode(EpisodeListReply("s", listOf(playing.episode!!)))))
    }

    @Test
    fun `bytes that are not a link value decode to null instead of failing`() {
        assertNull(WearLinkCodec.decode<PhoneLibrary>(null))
        assertNull(WearLinkCodec.decode<PhoneLibrary>(byteArrayOf(1, 2, 3)))
        assertNull(WearLinkCodec.decode<PhoneLibrary>(WearLinkCodec.pack("not json")))
        assertNull(WearLinkCodec.decode<WearLinkCommand>(WearLinkCodec.pack("{}")))
    }

    @Test
    fun `fields a newer app adds are ignored by an older one`() {
        val newer = WearLinkCodec.pack("""{"showsLayout":"List","storageLimitMb":1000,"changedAt":3,"somethingNew":true}""")

        assertEquals(WearLinkSettings("List", 1000, 3L), WearLinkCodec.decode<WearLinkSettings>(newer))
    }

    @Test
    fun `an ordinary library fits one item untouched`() {
        val library = PhoneLibrary(shows = List(300, ::show), history = List(30, ::row))

        val bytes = WearLinkCodec.encodeLibrary(library)

        assertTrue(bytes.size <= WearLinkCodec.MAX_ITEM_BYTES, "was ${bytes.size}")
        assertEquals(library, WearLinkCodec.decode<PhoneLibrary>(bytes))
    }

    @Test
    fun `a library too large for one item loses old history first, then shows, and says so`() {
        val library = PhoneLibrary(shows = List(400, ::show), history = List(40, ::row))
        val whole = WearLinkCodec.encode(library).size

        val onlyHistoryCut = WearLinkCodec.decode<PhoneLibrary>(WearLinkCodec.encodeLibrary(library, maxBytes = whole - 200))!!
        assertEquals(400, onlyHistoryCut.shows.size)
        assertTrue(onlyHistoryCut.history.size < 40)
        assertEquals(library.history.take(onlyHistoryCut.history.size), onlyHistoryCut.history)
        assertFalse(onlyHistoryCut.showsTruncated)

        val small = WearLinkCodec.encodeLibrary(library, maxBytes = whole / 3)
        val cut = WearLinkCodec.decode<PhoneLibrary>(small)!!
        assertTrue(small.size <= whole / 3)
        assertTrue(cut.showsTruncated)
        assertTrue(cut.shows.size < 400)
        assertEquals(library.shows.take(cut.shows.size), cut.shows)
        assertEquals(5, cut.history.size)
    }

    @Test
    fun `the phone's position moves on only while it plays`() {
        val episode = WearLinkEpisode("e", "s", "Title", durationMs = 100_000L)

        assertEquals(5_000L, PhoneNowPlaying(episode, isPlaying = false, positionMs = 5_000L, at = 1_000L).positionAt(now = 60_000L))
        assertEquals(35_000L, PhoneNowPlaying(episode, isPlaying = true, positionMs = 5_000L, speed = 1.5f, at = 1_000L).positionAt(now = 21_000L))
        assertEquals(100_000L, PhoneNowPlaying(episode, isPlaying = true, positionMs = 5_000L, at = 1_000L).positionAt(now = 900_000L))
        assertEquals(5_000L, PhoneNowPlaying(episode, isPlaying = true, positionMs = 5_000L, at = 9_000L).positionAt(now = 1_000L))
    }
}
