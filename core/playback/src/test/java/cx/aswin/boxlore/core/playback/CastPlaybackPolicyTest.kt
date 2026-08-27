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
        assertTrue(
            CastQueueSnapshotPolicy.shouldPreserveLocalQueue(
                remoteIds = listOf("current"),
                localIds = listOf("current", "next", "later"),
            ),
        )
    }

    @Test
    fun `complete or genuinely different receiver snapshots can reconcile`() {
        assertFalse(
            CastQueueSnapshotPolicy.shouldPreserveLocalQueue(
                remoteIds = listOf("current", "next", "later"),
                localIds = listOf("current", "next", "later"),
            ),
        )
        assertFalse(
            CastQueueSnapshotPolicy.shouldPreserveLocalQueue(
                remoteIds = listOf("different"),
                localIds = listOf("current", "next", "later"),
            ),
        )
    }

    @Test
    fun `ended Cast session rejects stale remote device state`() {
        assertFalse(CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveSession = false))
        assertTrue(CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveSession = true))
        assertTrue(CastSessionSyncPolicy.shouldAcceptRemoteRoute(hasActiveSession = null))
    }
}
