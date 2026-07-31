package cx.aswin.boxlore.navigation

import cx.aswin.boxlore.core.prefs.OpenAppTo

/**
 * Resolves NavHost [startDestination] for a cold start.
 * Precedence: onboarding → offline downloads → open-app-to subscriptions → home.
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
        else -> "home"
    }
