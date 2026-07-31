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
}
