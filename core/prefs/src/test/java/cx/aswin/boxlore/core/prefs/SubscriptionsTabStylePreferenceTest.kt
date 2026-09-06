package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubscriptionsTabStylePreferenceTest {
    @Test
    fun sanitize_keepsFloatingAndDefaultsToTop() {
        assertEquals(SubscriptionsTabStyle.TOP, SubscriptionsTabStyle.sanitize(null))
        assertEquals(SubscriptionsTabStyle.TOP, SubscriptionsTabStyle.sanitize(""))
        assertEquals(SubscriptionsTabStyle.TOP, SubscriptionsTabStyle.sanitize("unsupported"))
        assertEquals(SubscriptionsTabStyle.TOP, SubscriptionsTabStyle.sanitize("top"))
        assertEquals(SubscriptionsTabStyle.TOP, SubscriptionsTabStyle.sanitize(" TOP "))
        assertEquals(SubscriptionsTabStyle.FLOATING, SubscriptionsTabStyle.sanitize("floating"))
        assertEquals(SubscriptionsTabStyle.FLOATING, SubscriptionsTabStyle.sanitize(" Floating "))
        assertEquals(SubscriptionsTabStyle.FLOATING, SubscriptionsTabStyle.sanitize("FLOATING"))
    }
}
