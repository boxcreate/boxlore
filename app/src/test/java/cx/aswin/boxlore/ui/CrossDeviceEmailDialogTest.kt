package cx.aswin.boxlore.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossDeviceEmailDialogTest {

    @Test
    fun isValidAuthEmail_validEmails_returnsTrue() {
        assertTrue(isValidAuthEmail("listener@boxlore.example"))
        assertTrue(isValidAuthEmail("user.name+tag@subdomain.domain.org"))
        assertTrue(isValidAuthEmail("simple@domain.co"))
    }

    @Test
    fun isValidAuthEmail_invalidEmails_returnsFalse() {
        assertFalse(isValidAuthEmail(""))
        assertFalse(isValidAuthEmail("   "))
        assertFalse(isValidAuthEmail("missingatsign.com"))
        assertFalse(isValidAuthEmail("@missinguser.com"))
        assertFalse(isValidAuthEmail("user@"))
        assertFalse(isValidAuthEmail("user@domain"))
    }
}
