package cx.aswin.boxlore.surveys

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import cx.aswin.boxlore.surveys.internal.awaitTabletNoticeDismissal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TabletNoticeSurveyGateTest {
    @Test fun `queued survey waits for OK before it can present`() = runTest {
        val pending = mutableStateOf(true)
        var released = false
        val waiting = launch {
            awaitTabletNoticeDismissal { pending.value }
            released = true
        }
        runCurrent()
        assertFalse(released)
        pending.value = false
        Snapshot.sendApplyNotifications()
        runCurrent()
        waiting.join()
        assertTrue(released)
    }

    @Test fun `ordinary phone startup never queues a survey behind the tablet notice`() = runTest {
        awaitTabletNoticeDismissal { false }
    }
}
