package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Add to queue on a show page's selection fills the queue to its cap and says what didn't fit. */
class PodcastInfoQueueSelectionTest {
    /** A queue that refuses adds past [limit] and repeats, like the player's. */
    private class FakeQueue(initial: List<String>, private val limit: Int = 50) {
        val ids = initial.toMutableList()

        fun add(episode: Episode): Boolean {
            if (episode.id in ids || ids.size >= limit) return false
            ids += episode.id
            return true
        }
    }

    private fun episodes(count: Int, prefix: String = "e") = (1..count).map { TestFixtures.episode(id = "$prefix$it") }

    @Test
    fun `a selection bigger than the room left adds what fits and counts the rest`() = runTest {
        // Reported: 8 queued, Select all on a 220-episode serial, Add to queue: 42 added, 178 dropped silently.
        val queue = FakeQueue(initial = (1..8).map { "q$it" })
        val selection = episodes(220)

        val outcome = addSelectionToQueue(selection, queue.ids.toSet(), 50, { queue.ids.size }, queue::add)

        assertEquals(QueueSelectionOutcome(added = 42, didNotFit = 178), outcome)
        assertEquals(50, queue.ids.size)
        assertEquals(selection.take(42).map { it.id }, queue.ids.drop(8), "the first ones selected, in order")
        assertEquals(partlyAdded(42, 178), queueSelectionMessage(outcome))
    }

    @Test
    fun `episodes already queued take no room and are not counted as left out`() = runTest {
        val queue = FakeQueue(initial = (1..47).map { "q$it" } + listOf("e1", "e2"))
        val selection = episodes(5)

        val outcome = addSelectionToQueue(selection, queue.ids.toSet(), 50, { queue.ids.size }, queue::add)

        assertEquals(QueueSelectionOutcome(added = 1, didNotFit = 2), outcome)
        assertEquals(partlyAdded(1, 2), queueSelectionMessage(outcome))
    }

    @Test
    fun `a full queue adds nothing and says how to make room`() = runTest {
        val queue = FakeQueue(initial = (1..50).map { "q$it" })
        var calls = 0

        val outcome =
            addSelectionToQueue(episodes(3), queue.ids.toSet(), 50, { queue.ids.size }) {
                calls++
                queue.add(it)
            }

        assertEquals(QueueSelectionOutcome(added = 0, didNotFit = 3), outcome)
        assertEquals(0, calls)
        assertEquals(UiText.res(R.string.podcast_info_queue_full), queueSelectionMessage(outcome))
    }

    @Test
    fun `a selection that fits says nothing`() = runTest {
        val queue = FakeQueue(initial = emptyList())

        val outcome = addSelectionToQueue(episodes(3) + episodes(1), emptySet(), 50, { queue.ids.size }, queue::add)

        assertEquals(QueueSelectionOutcome(added = 3, didNotFit = 0), outcome)
        assertNull(queueSelectionMessage(outcome))
    }

    @Test
    fun `one left out reads in the singular`() {
        // The plural follows each count on its own; the English is checked in EpisodeDurationFormatterTest.
        assertEquals(partlyAdded(2, 1), queueSelectionMessage(QueueSelectionOutcome(added = 2, didNotFit = 1)))
    }

    private fun partlyAdded(added: Int, left: Int) = UiText.res(
        R.string.podcast_info_queue_partly_added,
        UiText.plural(R.plurals.podcast_info_queue_added, added, added),
        UiText.plural(R.plurals.podcast_info_queue_not_added, left, left),
    )
}
