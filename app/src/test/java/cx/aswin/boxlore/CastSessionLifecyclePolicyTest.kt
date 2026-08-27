package cx.aswin.boxlore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CastSessionLifecyclePolicyTest {
    @Test
    fun `start resume and suspension keep Cast active`() {
        val activeEvents =
            listOf(
                CastSessionEvent.STARTING,
                CastSessionEvent.STARTED,
                CastSessionEvent.RESUMING,
                CastSessionEvent.RESUMED,
                CastSessionEvent.SUSPENDED,
            )

        activeEvents.forEach { event ->
            assertEquals(CastSessionAction.KEEP_ACTIVE, CastSessionLifecyclePolicy.action(event))
        }
    }

    @Test
    fun `failed or ended sessions defer route clearing`() {
        val clearingEvents =
            listOf(
                CastSessionEvent.START_FAILED,
                CastSessionEvent.RESUME_FAILED,
                CastSessionEvent.ENDED,
            )

        clearingEvents.forEach { event ->
            assertEquals(CastSessionAction.DEFER_CLEAR, CastSessionLifecyclePolicy.action(event))
        }
    }

    @Test
    fun `ending callback waits for the terminal event`() {
        assertEquals(
            CastSessionAction.NONE,
            CastSessionLifecyclePolicy.action(CastSessionEvent.ENDING),
        )
    }

    @Test
    fun `remote to local transfer clears only after successful handoff`() {
        assertEquals(
            CastSessionAction.KEEP_ACTIVE,
            CastSessionTransferPolicy.action(
                isRemoteToLocal = true,
                outcome = CastTransferOutcome.TRANSFERRING,
            ),
        )
        assertEquals(
            CastSessionAction.CLEAR_NOW,
            CastSessionTransferPolicy.action(
                isRemoteToLocal = true,
                outcome = CastTransferOutcome.TRANSFERRED,
            ),
        )
        assertEquals(
            CastSessionAction.DEFER_CLEAR,
            CastSessionTransferPolicy.action(
                isRemoteToLocal = true,
                outcome = CastTransferOutcome.FAILED,
            ),
        )
    }

    @Test
    fun `unrelated transfer types do not change Cast route state`() {
        assertEquals(
            CastSessionAction.NONE,
            CastSessionTransferPolicy.action(
                isRemoteToLocal = false,
                outcome = CastTransferOutcome.TRANSFERRED,
            ),
        )
    }
}
