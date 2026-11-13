package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import org.junit.Assert.assertEquals
import org.junit.Test

class CastSkippedEpisodesTest {
    private fun episode(id: String, audioUrl: String = "https://cdn.example.com/$id.mp3") = Episode(id, "Episode $id", "", audioUrl)

    private val homeOnly = episode("b", "http://192.168.1.5/b.mp3")

    @Test
    fun onlyEpisodesNoReceiverCanFetchCountAsSkipped() {
        val local = listOf(episode("a"), homeOnly, episode("c"), episode("removed"))

        assertEquals(listOf("b"), CastSkippedEpisodes.skipped(local, listOf("a", "c")).map(Episode::id))
    }

    @Test
    fun skippedEpisodesKeepTheirPlaceWhenTheCastQueueChanges() {
        val local = listOf(episode("a"), homeOnly, episode("c"))
        val remote = listOf(episode("a"), episode("c"), episode("d"))

        val merged = CastSkippedEpisodes.withSkipped(remote, local, listOf(homeOnly))

        assertEquals(listOf("a", "b", "c", "d"), merged.map(Episode::id))
    }

    @Test
    fun aSkippedEpisodeThatLedTheQueueFollowsThePlayingOne() {
        val merged = CastSkippedEpisodes.withSkipped(listOf(episode("c"), episode("d")), listOf(homeOnly, episode("c"), episode("d")), listOf(homeOnly))

        assertEquals(listOf("c", "b", "d"), merged.map(Episode::id))
    }

    @Test
    fun skippedEpisodesGoBackAfterTheEpisodeTheyFollowedOnThePhone() {
        val local = listOf(episode("a"), homeOnly, episode("c"), episode("e", "http://nas.local/e.mp3"))

        val plan = CastSkippedEpisodes.insertions(local, listOf("old", "a", "c"), currentIndex = 1, missing = setOf("b", "e"))

        assertEquals(listOf(2 to "b", 4 to "e"), plan.map { (index, episode) -> index to episode.id })
    }

    @Test
    fun anEpisodeTheListenerRemovedWhileCastingStaysOut() {
        val plan = CastSkippedEpisodes.insertions(listOf(episode("a"), episode("c")), listOf("a", "c"), currentIndex = 0, missing = setOf("b"))

        assertEquals(emptyList<Pair<Int, Episode>>(), plan)
    }
}
