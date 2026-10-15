package cx.aswin.boxlore.ui

import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletAlphaStartupDecisionTest {
    private var acknowledgmentReads = 0
    private fun acknowledged(value: Boolean): () -> Boolean = {
        acknowledgmentReads++
        value
    }

    @Test fun `phone startup including landscape never requires the notice`() {
        listOf(360f to 800f, 412f to 915f, 800f to 360f, 599f to 1000f, 900f to 479f).forEach { (width, height) ->
            assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(width, height), minOf(width, height), acknowledged(false)))
        }
        assertEquals("Phones never read the notice acknowledgment", 0, acknowledgmentReads)
    }

    @Test fun `large phones at a small display size skip the notice even when landscape passes the tablet gate`() {
        listOf(480f, 520f, 599f).forEach { shortSide ->
            val landscape = BoxloreWindowLayout(shortSide * 2.2f, shortSide)
            assertTrue(landscape.usesTabletLayout)
            assertFalse(shouldShowTabletAlphaNotice(landscape, shortSide, acknowledged(false)))
            assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(shortSide, shortSide * 2.2f), shortSide, acknowledged(false)))
        }
        assertEquals(0, acknowledgmentReads)
    }

    @Test fun `unacknowledged tablet startup requires the notice in both orientations`() {
        listOf(600f to 960f, 1280f to 800f, 840f to 1280f, 673f to 841f).forEach { (width, height) ->
            assertTrue(shouldShowTabletAlphaNotice(BoxloreWindowLayout(width, height), minOf(width, height), acknowledged(false)))
            assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(width, height), minOf(width, height), acknowledged(true)))
        }
    }

    @Test fun `a narrow split-screen window on a tablet keeps the phone startup`() {
        assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(400f, 800f), 800f, acknowledged(false)))
        assertEquals(0, acknowledgmentReads)
    }

    @Test fun `restoration retains the startup decision until explicit acknowledgment`() {
        assertTrue(shouldShowTabletAlphaNotice(BoxloreWindowLayout(412f, 915f), 412f, acknowledged(false), restoredPending = true))
        assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(1280f, 800f), 800f, acknowledged(false), restoredPending = false))
        assertFalse(shouldShowTabletAlphaNotice(BoxloreWindowLayout(1280f, 800f), 800f, acknowledged(true), restoredPending = true))
    }
}
