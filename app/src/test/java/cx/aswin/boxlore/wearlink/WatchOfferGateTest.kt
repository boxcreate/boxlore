package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.wearlink.PhoneWatchDownloads
import cx.aswin.boxlore.core.wearlink.RecordingWearLinkTransport
import cx.aswin.boxlore.core.wearlink.WearLinkStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** What the phone offers for the watch: only while the watch can actually be told. */
class WatchOfferGateTest {
    private val inReach = WearLinkStatus(watchKnown = true, reachable = true)
    private val away = WearLinkStatus(watchKnown = true, reachable = false)
    private val neverLinked = WearLinkStatus(watchKnown = false, reachable = true)

    // Regression: Send to watch stayed on the episode page once a watch had linked, even with it away.
    @Test
    fun `the episode page gets its watch button only while the watch is in reach`() =
        runTest {
            val downloads = PhoneWatchDownloads(RecordingWearLinkTransport(), backgroundScope)

            assertNotNull(watchEpisodeActions(inReach, stored = emptyList(), downloads = downloads))
            assertNull(watchEpisodeActions(away, stored = emptyList(), downloads = downloads))
            assertNull(watchEpisodeActions(neverLinked, stored = emptyList(), downloads = downloads))
        }

    @Test
    fun `the players offer the watch by the same rule, and the offer plays there`() {
        var played = 0

        val offer = watchPlayOffer(inReach) { played++ }
        offer?.onPlayOnWatch?.invoke()

        assertEquals(1, played)
        assertNull(watchPlayOffer(away) { played++ })
        assertNull(watchPlayOffer(neverLinked) { played++ })
    }
}
