package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import cx.aswin.boxlore.feature.settings.pages.TvApproval
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TvSignInAdapterTest {
    private val verified = BoxLoreUser(uid = "u", email = "me@example.org", displayName = "Me", isEmailVerified = true, providerId = "google.com")

    @Test
    fun `a TV code is approved under this phone's own account`() =
        runTest {
            val sent = mutableListOf<Pair<String, String>>()
            val adapter = TvSignInAdapter(user = { verified }, idToken = { "id-token" }, send = { auth, code -> sent.add(auth to code).let { TvSignInAdapter.Answer(200, null) } })

            assertEquals(TvApproval.Approved, adapter.approve("K7QX4M2P"))
            assertEquals(listOf("Bearer id-token" to "K7QX4M2P"), sent)
        }

    @Test
    fun `a wrong or spent code, a refused sign-in and no answer read differently`() =
        runTest {
            fun answering(status: Int?, error: String? = null) =
                TvSignInAdapter(user = { verified }, idToken = { "id-token" }, send = { _, _ -> status?.let { TvSignInAdapter.Answer(it, error) } })

            assertEquals(TvApproval.WrongCode, answering(404, "invalid_code").approve("K7QX4M2P"))
            assertEquals(TvApproval.WrongCode, answering(400).approve("K7QX4M2P"))
            assertEquals(TvApproval.SignInAgain, answering(401, "account_deleted").approve("K7QX4M2P"))
            assertEquals(TvApproval.SignInAgain, answering(401, "sign_in_required").approve("K7QX4M2P"))
            assertEquals(TvApproval.TooManyTries, answering(429, "too_many_attempts").approve("K7QX4M2P"))
            assertEquals(TvApproval.Closed, answering(403, "watch_sign_in_closed").approve("K7QX4M2P"))
            assertEquals(TvApproval.NoConnection, answering(null).approve("K7QX4M2P"))
        }

    // Regression: a sign-in the web had shown, and a moment boxlore couldn't check the sign-in, both read "Couldn't reach boxlore".
    @Test
    fun `a sign-in the web showed and a brief outage say what they are`() {
        assertEquals("a fresh sign-in on this phone lends", TvApproval.SignInAgain, TvSignInAdapter.approval(403, "web_sign_in_not_accepted"))
        assertEquals(TvApproval.Unavailable, TvSignInAdapter.approval(503, "sync_unavailable"))
        assertEquals(TvApproval.Unavailable, TvSignInAdapter.approval(503, "sign_in_unavailable"))
        assertEquals(TvApproval.Unavailable, TvSignInAdapter.approval(502, null))
        assertEquals("the daily cap for a phone without App Check", TvApproval.TooManyTries, TvSignInAdapter.approval(429, "too_many_attempts", sentAttested = true))
    }

    // Regression: a phone that had to pass its check read "Verify your email", or "Couldn't reach boxlore" for a 403.
    @Test
    fun `a phone that must pass its check first is told so`() {
        assertEquals(TvApproval.CheckNeeded, TvSignInAdapter.approval(401, ApiErrorCodes.VERIFICATION_REQUIRED))
        assertEquals(TvApproval.CheckNeeded, TvSignInAdapter.approval(401, ApiErrorCodes.ATTESTATION_REQUIRED))
        assertEquals(TvApproval.CheckNeeded, TvSignInAdapter.approval(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED))
    }

    // Regression: a phone that had passed its check, refused for want of App Check, was told to finish a check that couldn't help.
    @Test
    fun `a checked phone the API still won't take says it can't sign a TV in`() =
        runTest {
            val adapter = TvSignInAdapter(user = { verified }, idToken = { "id-token" }, send = { _, _ -> TvSignInAdapter.Answer(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED, sentAttested = true) })

            assertEquals(TvApproval.CannotLend, adapter.approve("K7QX4M2P"))
            assertEquals(TvApproval.CheckNeeded, TvSignInAdapter.approval(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED, sentAttested = false))
        }

    @Test
    fun `an account that cannot sync yet never sends a code`() =
        runTest {
            var sent = false
            val unverified = verified.copy(isEmailVerified = false, providerId = "password")
            val adapter = TvSignInAdapter(user = { unverified }, idToken = { "id-token" }, send = { _, _ ->
                sent = true
                TvSignInAdapter.Answer(200, null)
            })

            assertEquals(TvApproval.SignInNeeded, adapter.approve("K7QX4M2P"))
            assertTrue(!sent)
            assertEquals(TvApproval.SignInNeeded, TvSignInAdapter(user = { null }, idToken = { null }, send = { _, _ -> TvSignInAdapter.Answer(200, null) }).approve("K7QX4M2P"))
        }
}
