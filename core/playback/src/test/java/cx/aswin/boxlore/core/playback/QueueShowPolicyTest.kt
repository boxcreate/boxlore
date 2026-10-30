package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class QueueShowPolicyTest {
    private val previous = Podcast("1001", "Conspiracy Theories", "Spotify Studios", "https://example.com/ct.jpg", genre = "Society")
    private val vergecast = Podcast("2002", "The Vergecast", "The Verge", "https://example.com/verge.jpg", genre = "Technology")

    private fun episode(podcastId: String? = null, podcastTitle: String? = null) = Episode(
        id = "22",
        title = "The latest Vergecast",
        description = "",
        audioUrl = "https://example.com/22.mp3",
        podcastId = podcastId,
        podcastTitle = podcastTitle,
    )

    @Test
    fun anotherShowsIdNeverTakesThePreviousShowOrItsFields() {
        val show = QueueShowPolicy.currentShow(episode(podcastId = "2002"), previous, stored = null)!!

        assertEquals("2002", show.id)
        assertEquals("Unknown Podcast", show.title)
        assertEquals("", show.artist)
        assertEquals("", show.imageUrl)
        assertEquals("Podcast", show.genre)
    }

    @Test
    fun aShowIdPrefersTheStoredShowThenThePreviousOneWithThatId() {
        assertSame(vergecast, QueueShowPolicy.currentShow(episode(podcastId = "2002"), previous, stored = vergecast))
        assertSame(previous, QueueShowPolicy.currentShow(episode(podcastId = "1001"), previous, stored = null))
        // A stored show without a real title gives way to the episode's own title.
        val untitled = vergecast.copy(title = "Unknown Podcast")
        assertEquals("The Vergecast", QueueShowPolicy.currentShow(episode("2002", "The Vergecast"), previous, untitled)?.title)
    }

    @Test
    fun aBlankIdWithItsOwnTitleKeepsThatTitle() {
        // Room saves a missing show ID as "".
        val show = QueueShowPolicy.currentShow(episode(podcastId = "", podcastTitle = "The Vergecast"), previous, stored = null)!!

        assertEquals("The Vergecast", show.title)
        assertEquals("", show.id)
    }

    @Test
    fun anEntryWithNoShowAtAllContinuesThePreviousShow() {
        // A list played from one show's page whose episodes carry no show fields.
        assertSame(previous, QueueShowPolicy.currentShow(episode(), previous, stored = null))
        assertSame(previous, QueueShowPolicy.currentShow(episode(podcastTitle = "Conspiracy Theories"), previous, stored = null))
        assertNull(QueueShowPolicy.currentShow(episode(), previous = null, stored = null))
    }

    @Test
    fun withShowFillsOnlyMissingFields() {
        val tagged = QueueShowPolicy.withShow(episode(podcastId = "", podcastTitle = "Unknown Podcast"), vergecast)

        assertEquals("2002", tagged.podcastId)
        assertEquals("The Vergecast", tagged.podcastTitle)
        assertEquals("The Verge", tagged.podcastArtist)
        assertEquals("Technology", tagged.podcastGenre)
        assertEquals("https://example.com/verge.jpg", tagged.podcastImageUrl)

        val own = episode(podcastId = "3003", podcastTitle = "Decoder").copy(podcastArtist = "Nilay Patel")
        // Another show's episode stays as it is: no genre or artwork from the Vergecast either.
        assertEquals(own, QueueShowPolicy.withShow(own, vergecast))
        val titleOnly = episode(podcastTitle = "Decoder")
        assertEquals(titleOnly, QueueShowPolicy.withShow(titleOnly, vergecast))
        // The same show, named by its title alone, gets the rest of it.
        assertEquals("2002", QueueShowPolicy.withShow(episode(podcastTitle = "The Vergecast"), vergecast).podcastId)
    }

    @Test
    fun theUnknownPlaceholderIsNoShow() {
        // An episode page that doesn't know its show passes "unknown" and "Podcast".
        val unknownPage = Podcast("unknown", "Podcast", "", "")
        assertEquals(episode(), QueueShowPolicy.withShow(episode(), unknownPage))
        assertSame(previous, QueueShowPolicy.currentShow(episode(podcastId = "unknown"), previous, stored = null))
    }

    @Test
    fun aQueueStartsUnderItsFirstEpisodesShowWhenThatNamesAnotherShow() {
        val allDownloads = Podcast("downloads_all", "All Downloads", "", "")
        val started = QueueShowPolicy.startShow(episode("2002", "The Vergecast"), allDownloads)
        assertEquals("2002", started.id)
        assertEquals("The Vergecast", started.title)
        // An episode with no show of its own, or of the same show, starts under the given show.
        assertSame(allDownloads, QueueShowPolicy.startShow(episode(), allDownloads))
        assertSame(vergecast, QueueShowPolicy.startShow(episode("2002"), vergecast))
    }

    @Test
    fun anEntryWithNoShowPlaysUnderTheShowTheQueueStartedFrom() {
        // Play All from Conspiracy Theories, with a Vergecast episode played next in between.
        assertSame(previous, QueueShowPolicy.currentShow(episode(), previous = vergecast, stored = null, queueShow = previous))
        assertSame(previous, QueueShowPolicy.currentShow(episode(podcastTitle = "Conspiracy Theories"), vergecast, null, queueShow = previous))
        assertSame(previous, QueueShowPolicy.currentShow(episode(podcastId = "1001"), vergecast, null, queueShow = previous))
        // An entry of another show is never put under the queue's show.
        assertEquals("The Vergecast", QueueShowPolicy.currentShow(episode("2002", "The Vergecast"), previous, null, queueShow = previous)?.title)
    }
}
