package cx.aswin.boxlore.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthExceptionsTest {

    @Test
    fun `RecentLoginRequiredException defaults and hierarchy`() {
        val cause = RuntimeException("credential_too_old")
        val exception = RecentLoginRequiredException(message = "Recent login required", cause = cause)

        assertTrue((exception as Any) is AuthException)
        assertEquals("Recent login required", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun `RecentLoginRequiredException default message`() {
        val exception = RecentLoginRequiredException()
        assertEquals("Recent authentication required", exception.message)
    }

    @Test
    fun `fake PendingEmailStore set and get`() {
        var stored: String? = null
        val store = object : PendingEmailStore {
            override fun getPendingEmail(): String? = stored
            override fun setPendingEmail(email: String?) {
                stored = email
            }
        }

        store.setPendingEmail("user@boxlore.example")
        assertEquals("user@boxlore.example", store.getPendingEmail())

        store.setPendingEmail(null)
        assertEquals(null, store.getPendingEmail())
    }

    @Test
    fun `fake PendingEmailStore lifecycle in magic link and sign out flows`() {
        var storedEmail: String? = null
        val store = object : PendingEmailStore {
            override fun getPendingEmail(): String? = storedEmail
            override fun setPendingEmail(email: String?) {
                storedEmail = email
            }
        }

        // sendMagicLink stores email on success
        val email = "listener@boxlore.example"
        store.setPendingEmail(email)
        assertEquals(email, store.getPendingEmail())

        // sendMagicLink failure clears pending email
        store.setPendingEmail(null)
        assertEquals(null, store.getPendingEmail())

        // signInWithEmailLink clears pending email in finally block
        store.setPendingEmail(email)
        assertEquals(email, store.getPendingEmail())
        store.setPendingEmail(null)
        assertEquals(null, store.getPendingEmail())

        // signOut clears pending email
        store.setPendingEmail(email)
        store.setPendingEmail(null)
        assertEquals(null, store.getPendingEmail())
    }
}
