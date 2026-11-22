package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.analytics.WatchListeningSummary
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

    @Test
    fun `merges are counted into the day's summary instead of sent one by one`() {
        val disk =
            object : WatchListeningSummary.Store {
                var day: WatchListeningSummary.Day? = null

                override fun load(): WatchListeningSummary.Day? = day

                override fun save(day: WatchListeningSummary.Day?) {
                    this.day = day
                }

                override fun lastSent(): Long? = null

                override fun saveLastSent(epochDay: Long) = Unit
            }
        val nov21 = 20_778L
        val analytics = PhoneLinkAnalytics(disk, today = { nov21 })

        analytics.note(PhoneLinkEvent.WatchChangesMerged(episodes = 1, follows = 0))
        analytics.note(PhoneLinkEvent.WatchChangesMerged(episodes = 2, follows = 1))

        assertEquals(WatchListeningSummary.Day(nov21, merges = 2, episodes = 3, follows = 1), disk.day)
    }
}
