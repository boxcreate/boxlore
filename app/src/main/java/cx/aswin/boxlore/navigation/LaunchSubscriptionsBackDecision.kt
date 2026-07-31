package cx.aswin.boxlore.navigation

/**
 * Cold-start Back contract when [openedToSubscriptionsOnLaunch] is set:
 * first Back leaves Subscriptions for Home; otherwise pop as usual.
 */
enum class LaunchSubscriptionsBackAction {
    NavigateHome,
    PopBackStack,
}

fun resolveLaunchSubscriptionsBack(openedToSubscriptionsOnLaunch: Boolean): LaunchSubscriptionsBackAction =
    if (openedToSubscriptionsOnLaunch) {
        LaunchSubscriptionsBackAction.NavigateHome
    } else {
        LaunchSubscriptionsBackAction.PopBackStack
    }
