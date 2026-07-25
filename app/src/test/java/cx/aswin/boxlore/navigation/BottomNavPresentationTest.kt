package cx.aswin.boxlore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomNavPresentationTest {
    @Test
    fun bottomNavTabRoutesKeepTheExistingRouteIdentities() {
        assertEquals("home", bottomNavTabRoutePattern("home"))
        assertEquals("learn", bottomNavTabRoutePattern("learn"))
        assertEquals(ExploreTabRoutePattern, bottomNavTabRoutePattern("explore"))
        assertEquals("library", bottomNavTabRoutePattern("library"))
        assertNull(bottomNavTabRoutePattern("unknown"))
    }

    @Test
    fun routeResolverKeepsLoreAndPillDestinationsSelected() {
        assertEquals("home", resolveBottomNavTab("home", emptyList()))
        assertEquals("learn", resolveBottomNavTab("learn/history", emptyList()))
        assertEquals("explore", resolveBottomNavTab("explore?entryPoint=bottom_nav", emptyList()))
        assertEquals("library", resolveBottomNavTab("library/downloads", emptyList()))
    }

    @Test
    fun stackSlideOrderMatchesTheCurrentNavigationPresentation() {
        assertEquals(0, getRouteIndex("home"))
        assertEquals(1, getRouteIndex("explore"))
        assertEquals(2, getRouteIndex("library"))
        assertEquals(3, getRouteIndex("learn"))
    }
}
