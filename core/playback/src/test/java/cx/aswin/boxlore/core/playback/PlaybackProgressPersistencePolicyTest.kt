package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackProgressPersistencePolicyTest {
    @Test
    fun `zero-start provenance is consumed only by the matching episode`() {
        val markedId = String("episode-a".toCharArray())
        val equalButDistinctId = String("episode-a".toCharArray())
        PlaybackLifecycleSignals.markPendingZeroStart(markedId)

        assertEquals(false, PlaybackLifecycleSignals.consumePendingZeroStart("episode-b"))
        assertEquals(true, PlaybackLifecycleSignals.consumePendingZeroStart(equalButDistinctId))
        assertEquals(false, PlaybackLifecycleSignals.consumePendingZeroStart("episode-a"))
    }

    @Test
    fun `older asynchronous snapshot cannot overwrite a newer seek`() {
        assertEquals(
            false,
            PlaybackProgressPersistencePolicy.shouldApplySnapshot(
                incomingSequence = 4L,
                lastAppliedSequence = 5L,
            ),
        )
        assertEquals(
            true,
            PlaybackProgressPersistencePolicy.shouldApplySnapshot(
                incomingSequence = 6L,
                lastAppliedSequence = 5L,
            ),
        )
    }

    @Test
    fun `metadata-less playback seed retries are bounded`() {
        assertEquals(true, PlaybackProgressPersistencePolicy.shouldAttemptMissingSeed(0))
        assertEquals(true, PlaybackProgressPersistencePolicy.shouldAttemptMissingSeed(1))
        assertEquals(false, PlaybackProgressPersistencePolicy.shouldAttemptMissingSeed(2))
        assertEquals(false, PlaybackProgressPersistencePolicy.shouldAttemptMissingSeed(100))
    }

    @Test
    fun `ordered Player snapshot can represent an intentional restart`() {
        assertEquals(
            60_000L,
            PlaybackProgressPersistencePolicy.resolvePositionMs(60_000L),
        )
    }

    @Test
    fun `ordered Player snapshot advances progress`() {
        assertEquals(
            3_610_000L,
            PlaybackProgressPersistencePolicy.resolvePositionMs(3_610_000L),
        )
    }

    @Test
    fun `negative Player position is sanitized to zero`() {
        assertEquals(
            0L,
            PlaybackProgressPersistencePolicy.resolvePositionMs(-1L),
        )
    }

    @Test
    fun `unknown player duration preserves the persisted duration`() {
        assertEquals(
            7_200_000L,
            PlaybackProgressPersistencePolicy.resolveDurationMs(
                existingDurationMs = 7_200_000L,
                incomingDurationMs = -1L,
            ),
        )
        assertEquals(
            7_200_000L,
            PlaybackProgressPersistencePolicy.resolveDurationMs(
                existingDurationMs = 7_200_000L,
                incomingDurationMs = 0L,
            ),
        )
    }

    @Test
    fun `known player duration replaces stale persisted duration`() {
        assertEquals(
            7_300_000L,
            PlaybackProgressPersistencePolicy.resolveDurationMs(
                existingDurationMs = 7_200_000L,
                incomingDurationMs = 7_300_000L,
            ),
        )
    }
}
