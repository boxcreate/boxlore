package cx.aswin.boxlore.feature.library

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.prefs.SubscriptionsTabStyle
import cx.aswin.boxlore.feature.library.subscriptions.SubscriptionsTabSelectorFabHeight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionsTabStyleTest {

    @Test
    fun subscriptionsTabSelectorFabHeightIs44Dp() {
        assertEquals(44.dp, SubscriptionsTabSelectorFabHeight)
    }

    @Test
    fun tabStyleResolutionMatchesExpectation() {
        assertTrue(SubscriptionsTabStyle.sanitize(null) == SubscriptionsTabStyle.TOP)
        assertTrue(SubscriptionsTabStyle.sanitize("") == SubscriptionsTabStyle.TOP)
        assertTrue(SubscriptionsTabStyle.sanitize("unknown") == SubscriptionsTabStyle.TOP)
        assertTrue(SubscriptionsTabStyle.sanitize("top") == SubscriptionsTabStyle.TOP)
        assertTrue(SubscriptionsTabStyle.sanitize("FLOATING") == SubscriptionsTabStyle.FLOATING)
        assertTrue(SubscriptionsTabStyle.sanitize("floating") == SubscriptionsTabStyle.FLOATING)
        assertFalse(SubscriptionsTabStyle.sanitize("top") == SubscriptionsTabStyle.FLOATING)
    }
}
