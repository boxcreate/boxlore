package cx.aswin.boxlore.wear.ui.settings

import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.cloud.WatchAccountState
import cx.aswin.boxlore.wear.ui.WatchRoutes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** What Settings' first screen says, and where each of its rows leads. */
class SettingsCopyTest {
    @Test
    fun `the Account row names who is signed in, and says so plainly when nobody is`() {
        assertEquals(AccountSummary("me@example.org", R.string.wear_account_signed_in), AccountCopy.summary(WatchAccountState.SignedIn("me@example.org")))
        // The phone had no name to give: the row still says the watch is signed in.
        assertEquals(AccountSummary(null, R.string.wear_account_signed_in), AccountCopy.summary(WatchAccountState.SignedIn(null)))
        assertEquals(AccountSummary(null, R.string.wear_account_signed_in), AccountCopy.summary(WatchAccountState.SignedIn(" ")))
        assertEquals(AccountSummary(null, R.string.wear_account_signing_in), AccountCopy.summary(WatchAccountState.SigningIn))
        assertEquals(AccountSummary(null, R.string.wear_account_signed_out), AccountCopy.summary(WatchAccountState.SignedOut))
    }

    @Test
    fun `every Settings page has a screen of its own that a launch can open`() {
        val routes = SettingsPage.entries.map(WatchRoutes::forSettingsPage)

        assertEquals(SettingsPage.entries.size, routes.toSet().size)
        routes.forEach { assertEquals(it, WatchRoutes.launchRoute(it)) }
    }
}
