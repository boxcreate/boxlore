package cx.aswin.boxlore.core.designsystem.layout

import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxloreStatusBarOwnerTest {
    @Test
    fun `content without shell pane bounds owns the status bar`() {
        assertTrue(paneOwnsStatusBar(null, BoxloreTestWindows.PHONE, RAIL_WIDTH_DP))
        assertTrue(paneOwnsStatusBar(null, BoxloreWindowLayout.Unspecified, RAIL_WIDTH_DP))
    }

    @Test
    fun `a split tablet gives the status bar to neither the detail nor the browsing pane`() {
        val spec = boxlorePaneSpec(BoxloreTestWindows.TABLET, contextOpen = true)
        assertFalse(paneOwnsStatusBar(requireNotNull(spec.context), BoxloreTestWindows.TABLET, spec.railWidthDp), "detail on the right")
        assertFalse(paneOwnsStatusBar(spec.primary, BoxloreTestWindows.TABLET, spec.railWidthDp), "browsing beside a detail")
    }

    @Test
    fun `a pane filling the window beside the rail owns it, below a tabletop hinge it does not`() {
        val idleBook = boxlorePaneSpec(BoxloreTestWindows.FOLD_BOOK, contextOpen = false)
        assertTrue(paneOwnsStatusBar(idleBook.primary, BoxloreTestWindows.FOLD_BOOK, idleBook.railWidthDp))
        val tabletop = boxlorePaneSpec(BoxloreTestWindows.FOLD_TABLETOP, contextOpen = true)
        assertFalse(paneOwnsStatusBar(tabletop.primary, BoxloreTestWindows.FOLD_TABLETOP, tabletop.railWidthDp), "the lower half")
        val narrow = boxlorePaneSpec(BoxloreTestWindows.W599_H900, contextOpen = true)
        assertTrue(paneOwnsStatusBar(narrow.primary, BoxloreTestWindows.W599_H900, narrow.railWidthDp))
    }
}
