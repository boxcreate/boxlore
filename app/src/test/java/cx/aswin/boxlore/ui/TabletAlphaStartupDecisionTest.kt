package cx.aswin.boxlore.ui

import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletAlphaStartupDecisionTest {
    @Test fun `phone startup including landscape never requires the notice`() {
        listOf(360f to 800f, 412f to 915f, 800f to 360f, 599f to 1000f, 900f to 479f).forEach { (width, height) ->
            assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(width, height), false))
        }
    }

    @Test fun `unacknowledged tablet startup requires the notice in both orientations`() {
        listOf(600f to 960f, 1280f to 800f, 840f to 1280f, 673f to 841f).forEach { (width, height) ->
            assertTrue(shouldShowTabletAlphaNotice(BoxloreWindowLayout(width, height), false))
            assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(width, height), true))
        }
    }

    @Test fun `restoration retains the startup decision until explicit acknowledgment`() {
        assertTrue(shouldShowTabletAlphaNotice(BoxloreWindowLayout(412f, 915f), false, restoredPending = true))
        assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(1280f, 800f), false, restoredPending = false))
        assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(1280f, 800f), true, restoredPending = true))
    }
}
