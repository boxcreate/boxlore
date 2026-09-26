package cx.aswin.boxlore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun shouldShowBottomNav_whenOnboardingNotCompleted_returnsFalse() {
        assertFalse(shouldShowBottomNav(onboardingCompleted = false, currentRoute = "home", isFromOnboarding = false))
        assertFalse(shouldShowBottomNav(onboardingCompleted = false, currentRoute = "settings", isFromOnboarding = false))
    }

    @Test
    fun shouldShowBottomNav_whenOnboardingOrPlayerRoute_returnsFalse() {
        assertFalse(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "onboarding", isFromOnboarding = false))
        assertFalse(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "player", isFromOnboarding = false))
        assertFalse(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "player/queue", isFromOnboarding = false))
    }

    @Test
    fun shouldShowBottomNav_whenFromOnboardingRoute_returnsFalse() {
        assertFalse(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "settings?page=account", isFromOnboarding = true))
        assertFalse(shouldShowBottomNav(onboardingCompleted = false, currentRoute = "settings?page=account", isFromOnboarding = true))
    }

    @Test
    fun shouldShowBottomNav_whenCompletedAndOnMainDestinations_returnsTrue() {
        assertTrue(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "home", isFromOnboarding = false))
        assertTrue(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "library", isFromOnboarding = false))
        assertTrue(shouldShowBottomNav(onboardingCompleted = true, currentRoute = "settings", isFromOnboarding = false))
    }

    @Test
    fun shouldShowBottomNav_whenNullRoute_returnsFalse() {
        assertFalse(shouldShowBottomNav(onboardingCompleted = true, currentRoute = null, isFromOnboarding = false))
    }

    @Test
    fun resolveIsFromOnboarding_returnsTrueOnlyWhenOnboardingActiveAndNotHomeBanner() {
        assertTrue(resolveIsFromOnboarding(onboardingCompleted = false, opmlImportSource = "welcome_screen"))
        assertTrue(resolveIsFromOnboarding(onboardingCompleted = false, opmlImportSource = "welcome_import_button"))
        assertFalse(resolveIsFromOnboarding(onboardingCompleted = false, opmlImportSource = "home_import_banner"))
        assertFalse(resolveIsFromOnboarding(onboardingCompleted = true, opmlImportSource = "welcome_screen"))
        assertFalse(resolveIsFromOnboarding(onboardingCompleted = true, opmlImportSource = "home_import_banner"))
    }

    @Test
    fun handleSettingsOnBack_whenFromOnboardingAndUserVerified_completesOnboardingAndNavigatesHome() {
        var onCompletedCalled = false
        var silentMarkCalled = false
        var navigateHomeCalled = false
        var popBackStackCalled = false

        handleSettingsOnBack(
            isFromOnboarding = true,
            isUserVerified = true,
            onOnboardingCompleted = { onCompletedCalled = true },
            markOnboardingCompletedSilent = { onDone ->
                silentMarkCalled = true
                onDone()
            },
            navigateToHome = { navigateHomeCalled = true },
            popBackStack = { popBackStackCalled = true },
        )

        assertTrue(onCompletedCalled)
        assertTrue(silentMarkCalled)
        assertTrue(navigateHomeCalled)
        assertFalse(popBackStackCalled)
    }

    @Test
    fun handleSettingsOnBack_whenNotFromOnboardingOrUnverified_popsBackStack() {
        var popBackStackCalled = false

        handleSettingsOnBack(
            isFromOnboarding = false,
            isUserVerified = true,
            onOnboardingCompleted = {},
            markOnboardingCompletedSilent = null,
            navigateToHome = {},
            popBackStack = { popBackStackCalled = true },
        )
        assertTrue(popBackStackCalled)

        popBackStackCalled = false
        handleSettingsOnBack(
            isFromOnboarding = true,
            isUserVerified = false,
            onOnboardingCompleted = {},
            markOnboardingCompletedSilent = null,
            navigateToHome = {},
            popBackStack = { popBackStackCalled = true },
        )
        assertTrue(popBackStackCalled)
    }
}
