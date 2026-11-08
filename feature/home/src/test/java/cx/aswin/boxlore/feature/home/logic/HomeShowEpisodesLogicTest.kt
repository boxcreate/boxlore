package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeShowEpisodesLogicTest {
    /** A show of [count] episodes, ep-1 the oldest, one day apart. */
    private fun show(count: Int): List<Episode> = (1..count).map { episode(it) }

    private fun episode(
        number: Int,
        publishedDate: Long = number * DAY,
    ): Episode = TestFixtures.episode(id = "ep-$number", publishedDate = publishedDate)

    private fun ids(episodes: List<Episode>) = episodes.map { it.id }

    private fun ids(vararg numbers: Int) = numbers.map { "ep-$it" }

    private fun completed(vararg numbers: Int) = numbers.mapTo(HashSet()) { "ep-$it" }

    private fun completedRange(range: IntRange) = range.mapTo(HashSet()) { "ep-$it" }

    private fun inOrderPool(
        episodes: List<Episode>,
        placeId: String?,
        newest: List<Episode> = episodes.asReversed(),
    ) = ShowEpisodesPool("pod-1", inOrder = true, episodes = episodes, placeId = placeId, newest = newest)

    private fun newestPool(newestFirst: List<Episode>) = ShowEpisodesPool("pod-1", inOrder = false, episodes = newestFirst)

    // --- Newest first ---

    @Test
    fun `newest first lists unfinished episodes newest at the top`() {
        val list = HomeShowEpisodesLogic.build(newestPool(show(12).asReversed()), completed(12, 10), hideCompleted = true)

        assertEquals(ids(11, 9, 8, 7, 6, 5, 4, 3), ids(list.episodes))
        assertFalse(list.caughtUp)
    }

    @Test
    fun `newest first with every episode finished shows the latest ones caught up instead of nothing`() {
        val episodes = show(12).asReversed()

        val list = HomeShowEpisodesLogic.build(newestPool(episodes), completedRange(1..12), hideCompleted = true)

        assertTrue(list.caughtUp)
        assertEquals(ids(12, 11, 10, 9, 8, 7, 6, 5), ids(list.episodes))
    }

    @Test
    fun `newest first looks past finished recent episodes to older unfinished ones`() {
        // The newest 30 are finished; the old 25-episode limit left two rows at most, here none.
        val episodes = show(50).asReversed()

        val list = HomeShowEpisodesLogic.build(newestPool(episodes), completedRange(21..50), hideCompleted = true)

        assertFalse(list.caughtUp)
        assertEquals(ids(20, 19, 18, 17, 16, 15, 14, 13), ids(list.episodes))
    }

    @Test
    fun `newest first with hiding off keeps finished episodes and says caught up only when all are`() {
        val episodes = show(10).asReversed()

        val some = HomeShowEpisodesLogic.build(newestPool(episodes), completed(10), hideCompleted = false)
        val all = HomeShowEpisodesLogic.build(newestPool(episodes), completedRange(1..10), hideCompleted = false)

        assertEquals("ep-10", some.episodes.first().id)
        assertFalse(some.caughtUp)
        assertTrue(all.caughtUp)
    }

    @Test
    fun `a show with no episodes is empty, not caught up`() {
        val list = HomeShowEpisodesLogic.build(newestPool(emptyList()), emptySet(), hideCompleted = true)

        assertTrue(list.episodes.isEmpty())
        assertFalse(list.caughtUp)
    }

    // --- In order ---

    @Test
    fun `in order lists the episode in progress then the next ones`() {
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(20), placeId = "ep-5"), completedRange(1..4), hideCompleted = true)

        assertEquals(ids(5, 6, 7, 8, 9, 10, 11, 12), ids(list.episodes))
        assertFalse(list.caughtUp)
    }

    @Test
    fun `in order continues after the last finished episode`() {
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(20), placeId = "ep-4"), completedRange(1..4), hideCompleted = true)

        assertEquals("ep-5", list.episodes.first().id)
    }

    @Test
    fun `in order starts at the beginning when nothing is played`() {
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(20), placeId = null), emptySet(), hideCompleted = true)

        assertEquals("ep-1", list.episodes.first().id)
    }

    @Test
    fun `finishing the whole series shows the latest episodes caught up instead of nothing`() {
        val episodes = show(20)

        val list = HomeShowEpisodesLogic.build(inOrderPool(episodes, placeId = "ep-20"), completedRange(1..20), hideCompleted = true)

        assertTrue(list.caughtUp)
        assertEquals(ids(20, 19, 18, 17, 16, 15, 14, 13), ids(list.episodes))
    }

    @Test
    fun `replaying an early episode does not move the list back`() {
        // Finished 1 to 40, then finished episode 2 again: the place by time is ep-2.
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(60), placeId = "ep-2"), completedRange(1..40), hideCompleted = true)

        assertEquals("ep-41", list.episodes.first().id)
    }

    @Test
    fun `an early episode being replayed leads the list, then the episodes after the furthest one finished`() {
        val finished = completedRange(1..40) - "ep-2"

        val list = HomeShowEpisodesLogic.build(inOrderPool(show(60), placeId = "ep-2"), finished, hideCompleted = true)

        assertEquals(ids(2, 41, 42), ids(list.episodes).take(3))
    }

    @Test
    fun `trailers are skipped in order`() {
        val episodes = show(6).map { if (it.id == "ep-3") it.copy(episodeType = "trailer", duration = 60) else it }

        val list = HomeShowEpisodesLogic.build(inOrderPool(episodes, placeId = "ep-1"), completed(1), hideCompleted = true)

        assertEquals(ids(2, 4, 5, 6), ids(list.episodes))
    }

    @Test
    fun `a long episode marked as a trailer still counts as an episode`() {
        val episodes = show(4).map { if (it.id == "ep-2") it.copy(episodeType = "trailer", duration = 3_600) else it }

        val list = HomeShowEpisodesLogic.build(inOrderPool(episodes, placeId = "ep-1"), completed(1), hideCompleted = true)

        assertEquals(ids(2, 3, 4), ids(list.episodes))
    }

    @Test
    fun `in order with hiding off shows two finished episodes before the place`() {
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(20), placeId = "ep-10"), completedRange(1..9), hideCompleted = false)

        assertEquals(ids(8, 9, 10, 11, 12, 13, 14, 15), ids(list.episodes))
    }

    @Test
    fun `caught up falls back to the loaded episodes newest first when the newest ones failed to load`() {
        val list =
            HomeShowEpisodesLogic.build(
                inOrderPool(show(3), placeId = "ep-3", newest = emptyList()),
                completedRange(1..3),
                hideCompleted = true,
            )

        assertTrue(list.caughtUp)
        assertEquals(ids(3, 2, 1), ids(list.episodes))
    }

    // --- NEW tag ---

    @Test
    fun `only the show's two newest episodes can carry the NEW tag`() {
        val episodes = show(30)
        val newest = episodes.asReversed().take(10)

        val list = HomeShowEpisodesLogic.build(inOrderPool(episodes, placeId = "ep-5", newest = newest), completedRange(1..4), hideCompleted = true)

        assertEquals(setOf("ep-30", "ep-29"), list.newestIds)
        assertTrue(list.episodes.none { it.id in list.newestIds })
    }

    // --- Listening order ---

    @Test
    fun `episodes released at the same moment play in season and episode order`() {
        val sameMoment = 5 * DAY
        val season =
            listOf(3, 1, 4, 2).map { number ->
                TestFixtures.episode(id = "s1e$number", publishedDate = sameMoment).copy(seasonNumber = 1, episodeNumber = number)
            }
        val earlier = TestFixtures.episode(id = "trailer-ish", publishedDate = DAY)

        val ordered = HomeShowEpisodesLogic.listeningOrder(season + earlier)

        assertEquals(listOf("trailer-ish", "s1e1", "s1e2", "s1e3", "s1e4"), ids(ordered))
    }

    @Test
    fun `episode numbers never override publish dates`() {
        // A feed that numbers episodes out of order keeps its publish order.
        val first = TestFixtures.episode(id = "a", publishedDate = DAY).copy(episodeNumber = 9)
        val second = TestFixtures.episode(id = "b", publishedDate = 2 * DAY).copy(episodeNumber = 1)
        val unnumbered = TestFixtures.episode(id = "c", publishedDate = 3 * DAY)

        assertEquals(listOf("a", "b", "c"), ids(HomeShowEpisodesLogic.listeningOrder(listOf(unnumbered, second, first))))
    }

    @Test
    fun `listening order drops duplicate rows`() {
        val episodes = show(3)

        assertEquals(ids(1, 2, 3), ids(HomeShowEpisodesLogic.listeningOrder(episodes + episodes.first())))
    }

    // --- Daily mix's next episode ---

    @Test
    fun `daily mix suggests the episode after the one in progress`() {
        val next = HomeShowEpisodesLogic.nextUp(show(10), placeId = "ep-4", completedIds = completedRange(1..3), inProgressIds = setOf("ep-4"))

        assertEquals("ep-5", next?.id)
    }

    @Test
    fun `daily mix suggests the episode after the last finished one`() {
        val next = HomeShowEpisodesLogic.nextUp(show(10), placeId = "ep-4", completedIds = completedRange(1..4), inProgressIds = emptySet())

        assertEquals("ep-5", next?.id)
    }

    @Test
    fun `daily mix skips finished episodes ahead of the one in progress`() {
        val next = HomeShowEpisodesLogic.nextUp(show(10), placeId = "ep-4", completedIds = completed(1, 2, 3, 5), inProgressIds = setOf("ep-4"))

        // ep-5 was heard already; the list and Daily mix agree on ep-6.
        assertEquals("ep-6", next?.id)
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(10), placeId = "ep-4"), completed(1, 2, 3, 5), hideCompleted = true)
        assertEquals("ep-6", list.episodes.first { it.id != "ep-4" }.id)
    }

    @Test
    fun `daily mix and the list agree after a replay`() {
        val finished = completedRange(1..40)

        val next = HomeShowEpisodesLogic.nextUp(show(60), placeId = "ep-2", completedIds = finished, inProgressIds = emptySet())
        val list = HomeShowEpisodesLogic.build(inOrderPool(show(60), placeId = "ep-2"), finished, hideCompleted = true)

        assertEquals("ep-41", next?.id)
        assertEquals(next?.id, list.episodes.first().id)
    }

    @Test
    fun `daily mix has nothing to suggest for a finished series`() {
        assertNull(HomeShowEpisodesLogic.nextUp(show(10), placeId = "ep-10", completedIds = completedRange(1..10), inProgressIds = emptySet()))
    }

    // --- The place ---

    @Test
    fun `the place is the episode in progress, else the last one finished`() {
        val history =
            listOf(
                item("ep-3", completed = true, lastPlayedAt = 30L),
                item("ep-4", completed = false, progressMs = 10L, lastPlayedAt = 20L),
                item("other", completed = false, progressMs = 10L, lastPlayedAt = 99L, podcastId = "pod-2"),
            )

        assertEquals("ep-4", HomeShowEpisodesLogic.placeHistoryId(history, setOf("pod-1")))
        assertEquals("ep-3", HomeShowEpisodesLogic.placeHistoryId(history.filter { it.isCompleted }, setOf("pod-1")))
        assertNull(HomeShowEpisodesLogic.placeHistoryId(emptyList(), setOf("pod-1")))
    }

    private fun item(
        episodeId: String,
        completed: Boolean,
        progressMs: Long = 0L,
        lastPlayedAt: Long = 0L,
        podcastId: String = "pod-1",
    ) = HomeListeningHistoryItem(
        episodeId = episodeId,
        podcastId = podcastId,
        podcastName = "Show",
        podcastImageUrl = null,
        progressMs = progressMs,
        durationMs = 100L,
        isCompleted = completed,
        isLiked = false,
        lastPlayedAt = lastPlayedAt,
    )

    private companion object {
        const val DAY = 86_400L
    }
}
