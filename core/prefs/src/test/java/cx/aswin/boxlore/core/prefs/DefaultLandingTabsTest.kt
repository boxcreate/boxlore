package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DefaultLandingTabsTest {
    @Test
    fun exploreSanitizeDefaultsToForYou() {
        assertEquals(ExploreDefaultTab.FOR_YOU, ExploreDefaultTab.sanitize(null))
        assertEquals(ExploreDefaultTab.FOR_YOU, ExploreDefaultTab.sanitize("unknown"))
        assertEquals(ExploreDefaultTab.FOR_YOU, ExploreDefaultTab.sanitize(" FOR_YOU "))
        assertEquals(ExploreDefaultTab.TOP, ExploreDefaultTab.sanitize("Top"))
    }

    @Test
    fun exploreResolveIndexPrefersNavAndCategoryOverSetting() {
        assertEquals(
            ExploreDefaultTab.INDEX_TOP,
            ExploreDefaultTab.resolveIndex(navTab = null, hasCategory = true, preferred = ExploreDefaultTab.FOR_YOU),
        )
        assertEquals(
            ExploreDefaultTab.INDEX_TOP,
            ExploreDefaultTab.resolveIndex(navTab = "trending", hasCategory = false, preferred = ExploreDefaultTab.FOR_YOU),
        )
        assertEquals(
            ExploreDefaultTab.INDEX_FOR_YOU,
            ExploreDefaultTab.resolveIndex(navTab = "for_you", hasCategory = false, preferred = ExploreDefaultTab.TOP),
        )
        assertEquals(
            ExploreDefaultTab.INDEX_TOP,
            ExploreDefaultTab.resolveIndex(navTab = null, hasCategory = false, preferred = ExploreDefaultTab.TOP),
        )
        assertEquals(
            ExploreDefaultTab.INDEX_FOR_YOU,
            ExploreDefaultTab.resolveIndex(navTab = null, hasCategory = false, preferred = null),
        )
    }

    @Test
    fun subscriptionsSanitizeDefaultsToShows() {
        assertEquals(SubscriptionsDefaultTab.SHOWS, SubscriptionsDefaultTab.sanitize(null))
        assertEquals(SubscriptionsDefaultTab.SHOWS, SubscriptionsDefaultTab.sanitize("Shows"))
        assertEquals(SubscriptionsDefaultTab.NEW_EPISODES, SubscriptionsDefaultTab.sanitize("New episodes"))
        assertEquals(SubscriptionsDefaultTab.NEW_EPISODES, SubscriptionsDefaultTab.sanitize("latest"))
    }

    @Test
    fun subscriptionsResolveIndexKeepsExplicitTab() {
        assertEquals(
            SubscriptionsDefaultTab.INDEX_SHOWS,
            SubscriptionsDefaultTab.resolveIndex(0, SubscriptionsDefaultTab.NEW_EPISODES),
        )
        assertEquals(
            SubscriptionsDefaultTab.INDEX_NEW_EPISODES,
            SubscriptionsDefaultTab.resolveIndex(1, SubscriptionsDefaultTab.SHOWS),
        )
        assertEquals(
            SubscriptionsDefaultTab.INDEX_NEW_EPISODES,
            SubscriptionsDefaultTab.resolveIndex(
                SubscriptionsDefaultTab.NAV_USE_PREF,
                SubscriptionsDefaultTab.NEW_EPISODES,
            ),
        )
        assertEquals(
            SubscriptionsDefaultTab.INDEX_SHOWS,
            SubscriptionsDefaultTab.resolveIndex(SubscriptionsDefaultTab.NAV_USE_PREF, null),
        )
    }
}
