package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.wearlink.PhoneLinkEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneLinkAnalyticsTest {
    @Test
    fun `every link event is handed on, and a failure while sending stays here`() {
        val sent = mutableListOf<PhoneLinkEvent>()
        val analytics =
            PhoneLinkAnalytics { event ->
                check(event != PhoneLinkEvent.WatchLinked) { "analytics is not ready" }
                sent += event
            }

        // The link calls this from its own work; an exception here would stop a merge or a hand-over.
        analytics.note(PhoneLinkEvent.WatchLinked)
        analytics.note(PhoneLinkEvent.PlaybackMoved(toWatch = true))

        assertEquals(listOf<PhoneLinkEvent>(PhoneLinkEvent.PlaybackMoved(toWatch = true)), sent)
    }
}
