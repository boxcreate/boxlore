package cx.aswin.boxlore.navigation

/**
 * Cold-start Back contract when [openedToLandingOnLaunch] is set:
 * first Back leaves Subscriptions or Downloads for Home; otherwise pop as usual.
 */
enum class LaunchSubscriptionsBackAction {
    NavigateHome,
    PopBackStack,
}

fun resolveLaunchSubscriptionsBack(openedToLandingOnLaunch: Boolean): LaunchSubscriptionsBackAction =
    if (openedToLandingOnLaunch) {
        LaunchSubscriptionsBackAction.NavigateHome
    } else {
        LaunchSubscriptionsBackAction.PopBackStack
    }
