package cx.aswin.boxlore.sync

import cx.aswin.boxlore.core.model.BoxLoreUser
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
            val adapter = TvSignInAdapter(user = { verified }, idToken = { "id-token" }, send = { auth, code -> sent.add(auth to code).let { 200 } })

            assertEquals(TvApproval.Approved, adapter.approve("K7QX4M2P"))
            assertEquals(listOf("Bearer id-token" to "K7QX4M2P"), sent)
        }

    @Test
    fun `a wrong or spent code, a refused sign-in and no answer read differently`() =
        runTest {
            fun answering(status: Int?) = TvSignInAdapter(user = { verified }, idToken = { "id-token" }, send = { _, _ -> status })

            assertEquals(TvApproval.WrongCode, answering(404).approve("K7QX4M2P"))
            assertEquals(TvApproval.SignInNeeded, answering(401).approve("K7QX4M2P"))
            assertEquals(TvApproval.NoConnection, answering(null).approve("K7QX4M2P"))
            assertEquals(TvApproval.NoConnection, answering(503).approve("K7QX4M2P"))
        }

    @Test
    fun `an account that cannot sync yet never sends a code`() =
        runTest {
            var sent = false
            val unverified = verified.copy(isEmailVerified = false, providerId = "password")
            val adapter = TvSignInAdapter(user = { unverified }, idToken = { "id-token" }, send = { _, _ ->
                sent = true
                200
            })

            assertEquals(TvApproval.SignInNeeded, adapter.approve("K7QX4M2P"))
            assertTrue(!sent)
            assertEquals(TvApproval.SignInNeeded, TvSignInAdapter(user = { null }, idToken = { null }, send = { _, _ -> 200 }).approve("K7QX4M2P"))
        }
}
