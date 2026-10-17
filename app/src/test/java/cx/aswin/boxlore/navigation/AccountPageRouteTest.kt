package cx.aswin.boxlore.navigation

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
}
