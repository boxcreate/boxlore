package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastPlaybackPolicyTest {
    @Test
    fun `stop Cast halts remote playback before ending receiver session`() {
        val plan = CastStopPolicy.plan(isRemote = true)

        assertTrue(plan.stopRemotePlayback)
        assertTrue(plan.endReceiverSession)
    }

    @Test
    fun `stop Cast is a no-op on the local route`() {
        val plan = CastStopPolicy.plan(isRemote = false)

        assertFalse(plan.stopRemotePlayback)
        assertFalse(plan.endReceiverSession)
    }

    @Test
    fun `partial receiver snapshot does not collapse the local queue while connecting`() {
        val policy = CastQueueSnapshotPolicy()

        assertTrue(
            policy.shouldPreserveLocalQueue(
                remoteIds = listOf("current"),
                localIds = listOf("current", "next", "later"),
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun `complete or genuinely different receiver snapshots can reconcile`() {
        val policy = CastQueueSnapshotPolicy()

        assertFalse(
            policy.shouldPreserveLocalQueue(
                remoteIds = listOf("current", "next", "later"),
                localIds = listOf("current", "next", "later"),
                nowMs = 1_000L,
            ),
        )
        assertFalse(
            policy.shouldPreserveLocalQueue(
                remoteIds = listOf("different"),
                localIds = listOf("current", "next", "later"),
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun `stable sanitized receiver subset replaces unsupported local items`() {
        val policy = CastQueueSnapshotPolicy()
        val remoteIds = listOf("current")
        val localIds = listOf("current", "local-only")

        assertTrue(policy.shouldPreserveLocalQueue(remoteIds, localIds, nowMs = 1_000L))
        assertTrue(policy.shouldPreserveLocalQueue(remoteIds, localIds, nowMs = 1_999L))
        assertFalse(policy.shouldPreserveLocalQueue(remoteIds, localIds, nowMs = 2_000L))
    }

    @Test
    fun `a full snapshot resets an established candidate so its window starts over`() {
        val policy = CastQueueSnapshotPolicy()
        val subset = listOf("current")
        val localIds = listOf("current", "next")

        assertTrue(policy.shouldPreserveLocalQueue(subset, localIds, nowMs = 1_000L))
        assertFalse(policy.shouldPreserveLocalQueue(localIds, localIds, nowMs = 1_500L))
        // Seen again after the reset, the subset is a new candidate with a fresh window.
        assertTrue(policy.shouldPreserveLocalQueue(subset, localIds, nowMs = 2_100L))
        assertTrue(policy.shouldPreserveLocalQueue(subset, localIds, nowMs = 3_099L))
        assertFalse(policy.shouldPreserveLocalQueue(subset, localIds, nowMs = 3_100L))
    }

    @Test
    fun `a different valid subset becomes the candidate with its own window`() {
        val policy = CastQueueSnapshotPolicy()
        val localIds = listOf("current", "next", "later")

        assertTrue(policy.shouldPreserveLocalQueue(listOf("current"), localIds, nowMs = 1_000L))
        assertTrue(policy.shouldPreserveLocalQueue(listOf("current", "next"), localIds, nowMs = 1_800L))
        assertTrue(policy.shouldPreserveLocalQueue(listOf("current", "next"), localIds, nowMs = 2_799L))
        assertFalse(policy.shouldPreserveLocalQueue(listOf("current", "next"), localIds, nowMs = 2_800L))
    }

    @Test
    fun `ended Cast session rejects stale remote device state`() {
        assertFalse(CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveSession = false))
        assertTrue(CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveSession = true))
        assertTrue(CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveSession = null))
    }

    @Test
    fun `deferred Cast transition retries only for matching titled metadata`() {
        assertTrue(
            DeferredCastTransitionPolicy.shouldRetry(
                pendingMediaId = "episode:current",
                currentMediaId = "episode:current",
                title = "Resolved title",
            ),
        )
        assertFalse(
            DeferredCastTransitionPolicy.shouldRetry(
                pendingMediaId = "episode:old",
                currentMediaId = "episode:current",
                title = "Resolved title",
            ),
        )
        assertFalse(
            DeferredCastTransitionPolicy.shouldRetry(
                pendingMediaId = "episode:current",
                currentMediaId = "episode:current",
                title = " ",
            ),
        )
    }
}
