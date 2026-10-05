package cx.aswin.boxlore.feature.info.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastAutoDownloadToggleTest {
    @Test fun enablingAppOpenOnlyDownloadsShowsAnOptionalNotice() {
        var toggles = 0
        var notices = 0
        handleAutoDownloadToggle(false, false, false, { notices++ }, { toggles++ })
        assertEquals(1, toggles)
        assertEquals(1, notices)
    }

    @Test fun allOtherDiscoveryAndDisableStatesToggleWithoutANotice() {
        val cases = listOf(
            Triple(false, false, true),
            Triple(false, true, false),
            Triple(false, true, true),
            Triple(true, false, false),
            Triple(true, false, true),
            Triple(true, true, false),
            Triple(true, true, true),
        )
        for ((auto, notifications, background) in cases) {
            var toggles = 0
            var notices = 0
            handleAutoDownloadToggle(auto, notifications, background, { notices++ }, { toggles++ })
            assertEquals(1, toggles)
            assertEquals(0, notices)
        }
    }
}
