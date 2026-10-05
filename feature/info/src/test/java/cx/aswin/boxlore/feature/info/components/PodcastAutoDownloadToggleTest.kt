package cx.aswin.boxlore.feature.info.components

import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastAutoDownloadToggleTest {
    @Test fun enablingAppOpenOnlyDownloadsShowsAnOptionalNotice() {
        var toggles = 0
        var warning = ToolbarWarning.NONE
        handleAutoDownloadToggle(false, false, false, warning, { warning = it }, { toggles++ })
        assertEquals(1, toggles)
        assertEquals(ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY, warning)
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
            var warning = ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY
            handleAutoDownloadToggle(auto, notifications, background, warning, { warning = it }, { toggles++ })
            assertEquals(1, toggles)
            assertEquals(ToolbarWarning.NONE, warning)
        }
    }

    @Test fun blockedSystemPermissionWarningSurvivesEnablingAndDisablingAutoDownload() {
        for (enabled in listOf(false, true)) {
            var warning = ToolbarWarning.SYSTEM_PERMISSION_BLOCKED
            var toggles = 0
            handleAutoDownloadToggle(enabled, false, false, warning, { warning = it }, { toggles++ })
            assertEquals(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED, warning)
            assertEquals(1, toggles)
        }
    }
}
