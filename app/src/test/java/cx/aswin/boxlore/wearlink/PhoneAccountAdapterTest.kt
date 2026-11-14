package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAccountAdapterTest {
    private val google = BoxLoreUser(uid = "u", email = "me@example.org", displayName = "Me", isEmailVerified = true, providerId = "google.com")

    @Test
    fun `a signed-in phone answers with a code asked for under its own account`() =
        runTest {
            val asked = mutableListOf<String>()
            val adapter = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { authorization -> asked.add(authorization).let { "code-1" } })

            assertEquals(WatchSignInReply(WatchSignInReply.OK, code = "code-1", account = "me@example.org"), adapter.watchSignIn())
            assertEquals(listOf("Bearer id-token"), asked)
        }

    @Test
    fun `a phone with no account, or one that cannot sync yet, says it is signed out and asks the API nothing`() =
        runTest {
            var asked = 0
            val unverified = BoxLoreUser(uid = "u", email = "me@example.org", displayName = null, isEmailVerified = false, providerId = "password")

            for (user in listOf(null, unverified)) {
                val adapter = PhoneAccountAdapter(user = { user }, idToken = { "id-token" }, createCode = { asked++.let { "code" } })
                assertEquals(WatchSignInReply(WatchSignInReply.SIGNED_OUT), adapter.watchSignIn())
            }
            assertEquals(0, asked)
        }

    @Test
    fun `no token or no code is a failure the watch can try again after`() =
        runTest {
            val noToken = PhoneAccountAdapter(user = { google }, idToken = { null }, createCode = { "code" })
            val noCode = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { null })

            assertEquals(WatchSignInReply.FAILED, noToken.watchSignIn().status)
            assertEquals(WatchSignInReply.FAILED, noCode.watchSignIn().status)
            assertNull(noCode.watchSignIn().code)
        }

    @Test
    fun `the watch shows the account's email, else its name`() {
        assertEquals("me@example.org", PhoneAccountRules.name(google))
        assertEquals("Me", PhoneAccountRules.name(google.copy(email = " ")))
        assertNull(PhoneAccountRules.name(google.copy(email = null, displayName = null)))
        assertTrue(PhoneAccountRules.name(null) == null)
    }
}
