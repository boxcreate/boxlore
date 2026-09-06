package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoPlaybackResumptionPolicyTest {

    // --- LivePlayer tests ---

    @Test
    fun `resolveCase returns LivePlayer when live player has items regardless of other state`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-1",
            progressMs = 12_000L,
            durationMs = 60_000L,
            isCompleted = false,
        )

        val case1 = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = true,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.LivePlayer, case1)

        val case2 = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = true,
            isPlayerDismissed = true,
            candidate = null,
        )
        assertEquals(AutoResumptionCase.LivePlayer, case2)
    }

    @Test
    fun `evaluate returns LivePlayer decision with live position and target`() {
        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = true,
            isPlayerDismissed = false,
            candidate = null,
            liveEpisodeId = "live-ep-42",
            livePositionMs = 35_000L,
        )

        assertEquals(AutoResumptionCase.LivePlayer, decision.case)
        assertEquals("live-ep-42", decision.targetEpisodeId)
        assertEquals(35_000L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    @Test
    fun `evaluate coerces negative live position to zero`() {
        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = true,
            isPlayerDismissed = false,
            candidate = null,
            liveEpisodeId = "live-ep",
            livePositionMs = -500L,
        )

        assertEquals(0L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    @Test
    fun `evaluate returns LivePlayer decision with shouldResume true even when liveEpisodeId is null`() {
        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = true,
            isPlayerDismissed = false,
            candidate = null,
            liveEpisodeId = null,
            livePositionMs = 12_000L,
        )

        assertEquals(AutoResumptionCase.LivePlayer, decision.case)
        assertNull(decision.targetEpisodeId)
        assertEquals(12_000L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    @Test
    fun `evaluate returns LivePlayer decision with shouldResume true even when liveEpisodeId is blank`() {
        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = true,
            isPlayerDismissed = true,
            candidate = null,
            liveEpisodeId = "   ",
            livePositionMs = 0L,
        )

        assertEquals(AutoResumptionCase.LivePlayer, decision.case)
        assertNull(decision.targetEpisodeId)
        assertEquals(0L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    // --- ActiveMiniPlayer tests ---

    @Test
    fun `resolveCase returns ActiveMiniPlayer when not dismissed and candidate incomplete`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-active",
            progressMs = 45_000L,
            durationMs = 180_000L,
            isCompleted = false,
        )

        val case = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.ActiveMiniPlayer, case)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.ActiveMiniPlayer, decision.case)
        assertEquals("ep-active", decision.targetEpisodeId)
        assertEquals(45_000L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    @Test
    fun `resolveCase returns ActiveMiniPlayer when not dismissed even if candidate is completed but restarts at zero`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-finished",
            progressMs = 180_000L,
            durationMs = 180_000L,
            isCompleted = true,
        )

        val case = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.ActiveMiniPlayer, case)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.ActiveMiniPlayer, decision.case)
        assertEquals("ep-finished", decision.targetEpisodeId)
        assertEquals(0L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    @Test
    fun `ActiveMiniPlayer with completion by 90 percent duration threshold starts at zero`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-near-end",
            progressMs = 95_000L,
            durationMs = 100_000L,
            isCompleted = false,
        )

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.ActiveMiniPlayer, decision.case)
        assertEquals(0L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    // --- InactiveMiniPlayerWithIncomplete tests ---

    @Test
    fun `resolveCase returns InactiveMiniPlayerWithIncomplete when dismissed but candidate is incomplete`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-incomplete",
            progressMs = 50_000L,
            durationMs = 200_000L,
            isCompleted = false,
        )

        val case = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = true,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.InactiveMiniPlayerWithIncomplete, case)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = true,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.InactiveMiniPlayerWithIncomplete, decision.case)
        assertEquals("ep-incomplete", decision.targetEpisodeId)
        assertEquals(50_000L, decision.startPositionMs)
        assertTrue(decision.shouldResume)
    }

    @Test
    fun `resolveCase returns NoResumption when dismissed and candidate is completed`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-completed",
            progressMs = 200_000L,
            durationMs = 200_000L,
            isCompleted = true,
        )

        val case = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = true,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.NoResumption, case)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = true,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.NoResumption, decision.case)
        assertNull(decision.targetEpisodeId)
        assertEquals(0L, decision.startPositionMs)
        assertFalse(decision.shouldResume)
    }

    @Test
    fun `resolveCase returns NoResumption when dismissed and candidate exceeds 90 percent duration`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "ep-almost-done",
            progressMs = 92_000L,
            durationMs = 100_000L,
            isCompleted = false,
        )

        val case = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = true,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.NoResumption, case)
    }

    // --- NoResumption tests ---

    @Test
    fun `resolveCase returns NoResumption when candidate is null`() {
        val caseNotDismissed = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = null,
        )
        assertEquals(AutoResumptionCase.NoResumption, caseNotDismissed)

        val caseDismissed = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = true,
            candidate = null,
        )
        assertEquals(AutoResumptionCase.NoResumption, caseDismissed)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = null,
        )
        assertFalse(decision.shouldResume)
        assertNull(decision.targetEpisodeId)
    }

    @Test
    fun `resolveCase returns NoResumption when candidate episodeId is blank`() {
        val candidate = AutoResumptionCandidate(
            episodeId = "   ",
            progressMs = 10_000L,
            durationMs = 60_000L,
            isCompleted = false,
        )

        val case = AutoPlaybackResumptionPolicy.resolveCase(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertEquals(AutoResumptionCase.NoResumption, case)

        val decision = AutoPlaybackResumptionPolicy.evaluate(
            hasLivePlayerItems = false,
            isPlayerDismissed = false,
            candidate = candidate,
        )
        assertFalse(decision.shouldResume)
    }

    // --- Queue Alignment tests ---

    @Test
    fun `alignQueue preserves queue and sets index when target is already present`() {
        val queue = listOf("ep-1", "ep-2", "ep-3")
        val aligned = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "ep-2",
            queue = queue,
            idSelector = { it },
        )

        assertEquals(listOf("ep-1", "ep-2", "ep-3"), aligned.items)
        assertEquals(1, aligned.startIndex)
    }

    @Test
    fun `alignQueue sets index to 0 when target is head of queue`() {
        val queue = listOf("ep-1", "ep-2")
        val aligned = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "ep-1",
            queue = queue,
            idSelector = { it },
        )

        assertEquals(listOf("ep-1", "ep-2"), aligned.items)
        assertEquals(0, aligned.startIndex)
    }

    @Test
    fun `alignQueue sets index to last when target is tail of queue`() {
        val queue = listOf("ep-1", "ep-2", "ep-3")
        val aligned = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "ep-3",
            queue = queue,
            idSelector = { it },
        )

        assertEquals(listOf("ep-1", "ep-2", "ep-3"), aligned.items)
        assertEquals(2, aligned.startIndex)
    }

    @Test
    fun `alignQueue prepends target item when target is absent from queue`() {
        val queue = listOf("ep-1", "ep-2")
        val aligned = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "ep-new",
            queue = queue,
            idSelector = { it },
        )

        assertEquals(listOf("ep-new", "ep-1", "ep-2"), aligned.items)
        assertEquals(0, aligned.startIndex)
    }

    @Test
    fun `alignQueue with empty queue creates single-item list at index 0`() {
        val aligned = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "ep-solo",
            queue = emptyList<String>(),
            idSelector = { it },
        )

        assertEquals(listOf("ep-solo"), aligned.items)
        assertEquals(0, aligned.startIndex)
    }

    @Test
    fun `alignQueue strips prefixes when comparing target and queue item ids`() {
        val queue = listOf("episode:ep-1", "queue:ep-2", "learn:ep-3")

        val alignedWithQueuePrefix = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "queue:ep-2",
            queue = queue,
            idSelector = { it },
        )
        assertEquals(1, alignedWithQueuePrefix.startIndex)

        val alignedWithBareId = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "ep-3",
            queue = queue,
            idSelector = { it },
        )
        assertEquals(2, alignedWithBareId.startIndex)

        val alignedWithLearnPrefix = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "learn:ep-1",
            queue = queue,
            idSelector = { it },
        )
        assertEquals(0, alignedWithLearnPrefix.startIndex)
    }

    @Test
    fun `alignQueueIds helper functions identically to alignQueue`() {
        val queue = listOf("ep-a", "ep-b", "ep-c")
        val aligned = AutoPlaybackResumptionPolicy.alignQueueIds("ep-b", queue)

        assertEquals(queue, aligned.items)
        assertEquals(1, aligned.startIndex)

        val alignedNew = AutoPlaybackResumptionPolicy.alignQueueIds("ep-z", queue)
        assertEquals(listOf("ep-z", "ep-a", "ep-b", "ep-c"), alignedNew.items)
        assertEquals(0, alignedNew.startIndex)
    }

    @Test
    fun `alignQueueIds with blank target returns queue unchanged at index 0`() {
        val queue = listOf("ep-a", "ep-b")
        val aligned = AutoPlaybackResumptionPolicy.alignQueueIds("", queue)

        assertEquals(queue, aligned.items)
        assertEquals(0, aligned.startIndex)
    }

    @Test
    fun `alignQueueIds with whitespace-only target returns queue unchanged at index 0`() {
        val queue = listOf("ep-a", "ep-b")
        val aligned = AutoPlaybackResumptionPolicy.alignQueueIds("   ", queue)

        assertEquals(queue, aligned.items)
        assertEquals(0, aligned.startIndex)
    }

    @Test
    fun `alignQueue with blank target item does not prepend blank item`() {
        val queue = listOf("ep-a", "ep-b")
        val aligned = AutoPlaybackResumptionPolicy.alignQueue(
            targetItem = "",
            queue = queue,
            idSelector = { it },
        )

        assertEquals(queue, aligned.items)
        assertEquals(0, aligned.startIndex)
    }

    // --- Candidate Mapping tests ---

    @Test
    fun `AutoResumptionCandidate from entity maps fields accurately`() {
        val entity = ListeningHistoryEntity(
            episodeId = "ep-history",
            podcastId = "pod-history",
            episodeTitle = "Test Title",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = "https://example.com/audio.mp3",
            podcastName = "Test Podcast",
            progressMs = 25_000L,
            durationMs = 120_000L,
            isCompleted = false,
            lastPlayedAt = 123456789L,
        )

        val candidate = AutoResumptionCandidate.from(entity)
        assertEquals("ep-history", candidate.episodeId)
        assertEquals(25_000L, candidate.progressMs)
        assertEquals(120_000L, candidate.durationMs)
        assertFalse(candidate.isCompleted)
    }
}
