package cx.aswin.boxlore.tv.ui.signin

import cx.aswin.boxlore.tv.cloud.TvSignIn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SignInRulesTest {
    @Test
    fun `the sign-in screen shows every state but signed in, and the code in two groups`() {
        assertNull(SignInRules.panel(TvSignIn.SignedIn("me@example.org")), "signed in, the app takes the screen")
        assertEquals(CodePanel.Loading, SignInRules.panel(TvSignIn.SignedOut))
        assertEquals(CodePanel.Loading, SignInRules.panel(TvSignIn.Starting))
        assertEquals(CodePanel.Code, SignInRules.panel(TvSignIn.ShowingCode("K7QX4M2P")))
        assertEquals(CodePanel.RanOut, SignInRules.panel(TvSignIn.CodeRanOut))
        assertEquals(CodePanel.NoConnection, SignInRules.panel(TvSignIn.NoConnection))
        assertEquals("K7QX-4M2P", SignInRules.grouped("K7QX4M2P"))
    }
}
