package cx.aswin.boxlore.feature.info.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastAutoDownloadToggleTest {
    @Test fun autoDownloadWorksIndependentlyOfNotificationPreference() {
        for (auto in listOf(false, true)) {
            for (notifications in listOf(false, true)) {
            var toggles = 0
            var warnings = 0
            handleAutoDownloadToggle(auto, notifications, { warnings++ }, { toggles++ })
            assertEquals(1, toggles)
            assertEquals(0, warnings)
        }
        }
    }
}
