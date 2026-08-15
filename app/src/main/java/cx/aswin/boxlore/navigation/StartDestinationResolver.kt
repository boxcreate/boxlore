package cx.aswin.boxlore.navigation

import cx.aswin.boxlore.core.prefs.OpenAppTo

/**
 * Resolves NavHost [startDestination] for a cold start.
 * Precedence: onboarding → offline downloads → open-app-to subscriptions/downloads → home.
 */
fun resolveStartDestination(
    onboardingCompleted: Boolean,
    isOfflineOnLaunch: Boolean,
    hasDeepLink: Boolean,
    openAppTo: String,
): String =
    when {
        !onboardingCompleted -> "onboarding"
        isOfflineOnLaunch && !hasDeepLink -> NavRoutes.LIBRARY_DOWNLOADS
        !hasDeepLink && openAppTo == OpenAppTo.SUBSCRIPTIONS -> NavRoutes.LIBRARY_SUBSCRIPTIONS
        !hasDeepLink && openAppTo == OpenAppTo.DOWNLOADS -> NavRoutes.LIBRARY_DOWNLOADS
        else -> "home"
    }

/** True when cold start opened Subscriptions or Downloads via **Open app to**. */
fun shouldMarkOpenedToLandingOnLaunch(
    destination: String,
    openAppTo: String,
): Boolean =
    destination == NavRoutes.LIBRARY_SUBSCRIPTIONS ||
        (destination == NavRoutes.LIBRARY_DOWNLOADS && openAppTo == OpenAppTo.DOWNLOADS)

/**
 * Predictive-back / toolbar Back treats these as the launch landing root.
 * Nested Downloads settings/show keep a back stack, so they are excluded.
 */
fun isLaunchLandingBackRoute(route: String?): Boolean {
    val current = route ?: return false
    return current.startsWith(NavRoutes.LIBRARY_SUBSCRIPTIONS) ||
        current == NavRoutes.LIBRARY_DOWNLOADS
}
