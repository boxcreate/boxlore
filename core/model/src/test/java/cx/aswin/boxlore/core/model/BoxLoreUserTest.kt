package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxLoreUserTest {

    @Test
    fun `user properties preserve authenticated state`() {
        val user =
            BoxLoreUser(
                uid = "uid-12345",
                email = "user@boxlore.example",
                displayName = "Boxlore Tester",
                isEmailVerified = true,
                isAnonymous = false,
            )

        assertEquals("uid-12345", user.uid)
        assertEquals("user@boxlore.example", user.email)
        assertEquals("Boxlore Tester", user.displayName)
        assertTrue(user.isEmailVerified)
        assertFalse(user.isAnonymous)
    }

    @Test
    fun `default values match unverified and non-anonymous`() {
        val user =
            BoxLoreUser(
                uid = "anon-67890",
                email = null,
                displayName = null,
            )

        assertEquals("anon-67890", user.uid)
        assertEquals(null, user.email)
        assertEquals(null, user.displayName)
        assertFalse(user.isEmailVerified)
        assertFalse(user.isAnonymous)
    }
}
