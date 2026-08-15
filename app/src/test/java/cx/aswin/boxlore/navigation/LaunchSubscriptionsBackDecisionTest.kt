package cx.aswin.boxlore.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LaunchSubscriptionsBackDecisionTest {
    @Test
    fun launchFlagNavigatesHome() {
        assertEquals(
            LaunchSubscriptionsBackAction.NavigateHome,
            resolveLaunchSubscriptionsBack(openedToLandingOnLaunch = true),
        )
    }

    @Test
    fun normalEntryPopsBackStack() {
        assertEquals(
            LaunchSubscriptionsBackAction.PopBackStack,
            resolveLaunchSubscriptionsBack(openedToLandingOnLaunch = false),
        )
    }
}
