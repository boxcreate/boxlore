package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.material3.SwipeToDismissBoxValue
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatestSwipeLogicTest {
    private fun podcast(id: String, status: EpisodeStatus = EpisodeStatus.UNPLAYED): Podcast =
        Podcast(
            id = id,
            title = id,
            artist = "Host",
            description = "",
            imageUrl = "",
            feedUrl = "https://example.com/feed",
            latestEpisode =
            Episode(
                id = "${id}_ep",
                title = "Latest",
                description = "",
                audioUrl = "https://example.com/a.mp3",
                imageUrl = null,
                publishedDate = 1_000L,
                duration = 600,
                podcastId = id,
            ),
            episodeStatus = status,
        )

    @Test
    fun startToEndQueuesAndEndToStartMarksPlayed() {
        assertEquals(LatestSwipeAction.AddToQueue, latestSwipeAction(SwipeToDismissBoxValue.StartToEnd))
        assertEquals(LatestSwipeAction.MarkPlayed, latestSwipeAction(SwipeToDismissBoxValue.EndToStart))
        assertNull(latestSwipeAction(SwipeToDismissBoxValue.Settled))
    }

    @Test
    fun queueOutcomeTellsAlreadyQueuedFromAFullQueue() {
        assertEquals(LatestQueueOutcome.Added, latestQueueOutcome(added = true, wasQueued = false, queueSize = 3, queueLimit = 50))
        assertEquals(LatestQueueOutcome.AlreadyQueued, latestQueueOutcome(added = false, wasQueued = true, queueSize = 50, queueLimit = 50))
        assertEquals(LatestQueueOutcome.QueueFull, latestQueueOutcome(added = false, wasQueued = false, queueSize = 50, queueLimit = 50))
        // Refused below the limit: the player already holds it under another of its IDs.
        assertEquals(LatestQueueOutcome.AlreadyQueued, latestQueueOutcome(added = false, wasQueued = false, queueSize = 7, queueLimit = 50))
    }

    @Test
    fun queueMessagesSayWhatHappened() {
        assertEquals(R.string.library_queue_added, latestQueueMessage(LatestQueueOutcome.Added))
        assertEquals(R.string.library_queue_already_queued, latestQueueMessage(LatestQueueOutcome.AlreadyQueued))
        assertEquals(R.string.library_queue_full, latestQueueMessage(LatestQueueOutcome.QueueFull))
    }

    @Test
    fun heldPlayedRowLeavesAtOnceWhenHidePlayedIsOn() {
        val rows = listOf(podcast("a"), podcast("b"), podcast("c", EpisodeStatus.COMPLETED))

        val shown = visibleLatestPodcasts(rows, hideCompleted = true, pendingPlayedIds = setOf("a_ep"))

        assertEquals(listOf("b"), shown.map { it.id })
    }

    @Test
    fun archivedLatestEpisodesNeverListWhateverHidePlayedSays() {
        val rows = listOf(podcast("a"), podcast("b"), podcast("c", EpisodeStatus.COMPLETED))

        val listable = latestWithoutArchived(rows, dismissedIds = setOf("a_ep", "c_ep"))

        assertEquals(listOf("b"), listable.map { it.id })
        assertEquals(listOf("b"), visibleLatestPodcasts(listable, hideCompleted = false, pendingPlayedIds = emptySet()).map { it.id })
        assertEquals(listOf("a", "b", "c"), latestWithoutArchived(rows, dismissedIds = emptySet()).map { it.id })
    }

    @Test
    fun heldPlayedRowStaysWhenHidePlayedIsOff() {
        val rows = listOf(podcast("a"), podcast("c", EpisodeStatus.COMPLETED))

        val shown = visibleLatestPodcasts(rows, hideCompleted = false, pendingPlayedIds = setOf("a_ep"))

        assertEquals(listOf("a", "c"), shown.map { it.id })
    }

    @Test
    fun heldSwipeLetsGoOnceTheListShowsItPlayed() {
        assertFalse(isLatestShownPlayed(listOf(podcast("a")), "a_ep"))
        assertTrue(isLatestShownPlayed(listOf(podcast("a", EpisodeStatus.COMPLETED)), "a_ep"))
        // A newer episode replaced it, so nothing still shows it unplayed.
        assertTrue(isLatestShownPlayed(listOf(podcast("b")), "a_ep"))
    }
}
