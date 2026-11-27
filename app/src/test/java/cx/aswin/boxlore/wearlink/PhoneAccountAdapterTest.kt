package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wearlink.PhoneAccountAdapter.CodeAnswer
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
            val adapter = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { authorization -> asked.add(authorization).let { CodeAnswer.Code("code-1") } })

            assertEquals(WatchSignInReply(WatchSignInReply.OK, code = "code-1", account = "me@example.org"), adapter.watchSignIn())
            assertEquals(listOf("Bearer id-token"), asked)
        }

    @Test
    fun `a phone with no account, or one that cannot sync yet, says it is signed out and asks the API nothing`() =
        runTest {
            var asked = 0
            val unverified = BoxLoreUser(uid = "u", email = "me@example.org", displayName = null, isEmailVerified = false, providerId = "password")

            for (user in listOf(null, unverified)) {
                val adapter = PhoneAccountAdapter(user = { user }, idToken = { "id-token" }, createCode = { asked++.let { CodeAnswer.Code("code") } })
                assertEquals(WatchSignInReply(WatchSignInReply.SIGNED_OUT), adapter.watchSignIn())
            }
            assertEquals(0, asked)
        }

    @Test
    fun `no token or no code is a failure the watch can try again after`() =
        runTest {
            val noToken = PhoneAccountAdapter(user = { google }, idToken = { null }, createCode = { CodeAnswer.Code("code") })
            val noCode = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { CodeAnswer.NoAnswer })
            val unavailable = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { CodeAnswer.Refused(503, "sync_unavailable") })

            assertEquals(WatchSignInReply.FAILED, noToken.watchSignIn().status)
            assertEquals(WatchSignInReply.FAILED, noCode.watchSignIn().status)
            assertNull(noCode.watchSignIn().code)
            assertEquals(WatchSignInReply.FAILED, unavailable.watchSignIn().status)
        }

    // Regression: a phone that had to pass boxlore's check first told the watch only that it failed.
    @Test
    fun `a phone that must pass its check before lending says so`() =
        runTest {
            for (refusal in listOf(CodeAnswer.Refused(401, ApiErrorCodes.VERIFICATION_REQUIRED), CodeAnswer.Refused(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED))) {
                val adapter = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { refusal })
                assertEquals(WatchSignInReply(WatchSignInReply.CHECK_NEEDED), adapter.watchSignIn())
            }
            // The same code at another status is not about the check.
            val other = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { CodeAnswer.Refused(403, ApiErrorCodes.VERIFICATION_REQUIRED) })
            assertEquals(WatchSignInReply.FAILED, other.watchSignIn().status)
        }

    // Regression: the lending limit and a checked phone the API still refused both reached the watch as a plain failure.
    @Test
    fun `a phone that lent too often, or can't lend even after its check, says so`() =
        runTest {
            fun refusing(answer: CodeAnswer.Refused) = PhoneAccountAdapter(user = { google }, idToken = { "id-token" }, createCode = { answer })

            assertEquals(WatchSignInReply(WatchSignInReply.TRY_LATER), refusing(CodeAnswer.Refused(429, "too_many_attempts")).watchSignIn())
            assertEquals(WatchSignInReply(WatchSignInReply.CANNOT_LEND), refusing(CodeAnswer.Refused(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED, sentAttested = true)).watchSignIn())
            assertEquals(WatchSignInReply(WatchSignInReply.CHECK_NEEDED), refusing(CodeAnswer.Refused(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED, sentAttested = false)).watchSignIn())
        }

    @Test
    fun `the watch shows the account's email, else its name`() {
        assertEquals("me@example.org", PhoneAccountRules.name(google))
        assertEquals("Me", PhoneAccountRules.name(google.copy(email = " ")))
        assertNull(PhoneAccountRules.name(google.copy(email = null, displayName = null)))
        assertTrue(PhoneAccountRules.name(null) == null)
    }
}
