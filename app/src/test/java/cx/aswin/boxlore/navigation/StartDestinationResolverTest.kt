package cx.aswin.boxlore.navigation

import cx.aswin.boxlore.core.prefs.OpenAppTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartDestinationResolverTest {
    @Test
    fun onboardingWinsOverEverything() {
        assertEquals(
            "onboarding",
            resolveStartDestination(
                onboardingCompleted = false,
                isOfflineOnLaunch = true,
                hasDeepLink = false,
                openAppTo = OpenAppTo.SUBSCRIPTIONS,
            ),
        )
    }

    @Test
    fun offlineDownloadsWinsOverOpenAppToSubscriptions() {
        assertEquals(
            NavRoutes.LIBRARY_DOWNLOADS,
            resolveStartDestination(
                onboardingCompleted = true,
                isOfflineOnLaunch = true,
                hasDeepLink = false,
                openAppTo = OpenAppTo.SUBSCRIPTIONS,
            ),
        )
    }

    @Test
    fun deepLinkSkipsOfflineAndOpenAppTo() {
        assertEquals(
            "home",
            resolveStartDestination(
                onboardingCompleted = true,
                isOfflineOnLaunch = true,
                hasDeepLink = true,
                openAppTo = OpenAppTo.SUBSCRIPTIONS,
            ),
        )
    }

    @Test
    fun openAppToSubscriptionsWhenOnline() {
        assertEquals(
            NavRoutes.LIBRARY_SUBSCRIPTIONS,
            resolveStartDestination(
                onboardingCompleted = true,
                isOfflineOnLaunch = false,
                hasDeepLink = false,
                openAppTo = OpenAppTo.SUBSCRIPTIONS,
            ),
        )
    }

    @Test
    fun openAppToDownloadsWhenOnline() {
        assertEquals(
            NavRoutes.LIBRARY_DOWNLOADS,
            resolveStartDestination(
                onboardingCompleted = true,
                isOfflineOnLaunch = false,
                hasDeepLink = false,
                openAppTo = OpenAppTo.DOWNLOADS,
            ),
        )
    }

    @Test
    fun defaultsToHome() {
        assertEquals(
            "home",
            resolveStartDestination(
                onboardingCompleted = true,
                isOfflineOnLaunch = false,
                hasDeepLink = false,
                openAppTo = OpenAppTo.HOME,
            ),
        )
    }

    @Test
    fun marksLandingForSubscriptionsAndChosenDownloads() {
        assertEquals(
            true,
            shouldMarkOpenedToLandingOnLaunch(
                NavRoutes.LIBRARY_SUBSCRIPTIONS,
                OpenAppTo.SUBSCRIPTIONS,
            ),
        )
        assertEquals(
            true,
            shouldMarkOpenedToLandingOnLaunch(
                NavRoutes.LIBRARY_DOWNLOADS,
                OpenAppTo.DOWNLOADS,
            ),
        )
        assertEquals(
            false,
            shouldMarkOpenedToLandingOnLaunch(
                NavRoutes.LIBRARY_DOWNLOADS,
                OpenAppTo.HOME,
            ),
        )
        assertEquals(
            false,
            shouldMarkOpenedToLandingOnLaunch("home", OpenAppTo.DOWNLOADS),
        )
    }

    @Test
    fun launchLandingBackRouteIsSubscriptionsOrDownloadsRoot() {
        assertEquals(true, isLaunchLandingBackRoute(NavRoutes.LIBRARY_SUBSCRIPTIONS))
        assertEquals(true, isLaunchLandingBackRoute("${NavRoutes.LIBRARY_SUBSCRIPTIONS}?tab=1"))
        assertEquals(true, isLaunchLandingBackRoute(NavRoutes.LIBRARY_DOWNLOADS))
        assertEquals(false, isLaunchLandingBackRoute(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS))
        assertEquals(false, isLaunchLandingBackRoute("library/downloads/show?podcastId=1"))
        assertEquals(false, isLaunchLandingBackRoute("home"))
        assertEquals(false, isLaunchLandingBackRoute(null))
    }
}
