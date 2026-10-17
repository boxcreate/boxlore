package cx.aswin.boxlore.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPageRouteTest {
    private val settingsRoute = "settings?page={page}&fromOnboarding={fromOnboarding}&fromImport={fromImport}"

    @Test fun `a sign-in link doesn't stack a second Account page`() {
        assertTrue(isAccountPage(settingsRoute, "account"))
    }

    @Test fun `any other screen, or another Settings page, opens Account and sync`() {
        assertFalse(isAccountPage(settingsRoute, "hub"))
        assertFalse(isAccountPage(settingsRoute, null))
        assertFalse(isAccountPage("home", null))
        assertFalse(isAccountPage(null, null))
    }

    @Test fun `the route opens Settings on the Account page`() {
        assertTrue(ACCOUNT_PAGE_ROUTE.startsWith("settings?"))
        assertTrue(ACCOUNT_PAGE_ROUTE.contains("page=account"))
    }

    @Test fun `a newer request opens Account and sync`() {
        assertEquals(AccountPageRequestAction.Open, accountPageRequestAction(request = 2_000L, handled = 1_000L, route = "home", page = null))
        assertEquals(AccountPageRequestAction.Open, accountPageRequestAction(request = 1L, handled = 0L, route = settingsRoute, page = "hub"))
    }

    @Test fun `a request already handled, including before recreation, does nothing`() {
        // Recreated activity: the saved handled value matches the app-scoped request.
        assertEquals(AccountPageRequestAction.Ignore, accountPageRequestAction(request = 2_000L, handled = 2_000L, route = "home", page = null))
        // Process recreated: the app-scoped request reset to 0, the saved handled value didn't.
        assertEquals(AccountPageRequestAction.Ignore, accountPageRequestAction(request = 0L, handled = 2_000L, route = "home", page = null))
        // No link yet.
        assertEquals(AccountPageRequestAction.Ignore, accountPageRequestAction(request = 0L, handled = 0L, route = "home", page = null))
    }

    @Test fun `a new request while Account is showing is marked handled without navigating`() {
        assertEquals(AccountPageRequestAction.MarkHandled, accountPageRequestAction(request = 3_000L, handled = 2_000L, route = settingsRoute, page = "account"))
    }
}
