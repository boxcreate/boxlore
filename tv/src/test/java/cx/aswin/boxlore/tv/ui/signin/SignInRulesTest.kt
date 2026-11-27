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
        assertEquals(CodePanel.PhoneCheck, SignInRules.panel(TvSignIn.PhoneCheckNeeded))
        for (problem in listOf(TvSignIn.NoConnection, TvSignIn.SignInClosed, TvSignIn.TooManyCodes)) assertEquals(CodePanel.Problem, SignInRules.panel(problem))
        // Each problem says its own reason, not the connection's.
        assertEquals(3, listOf(TvSignIn.NoConnection, TvSignIn.SignInClosed, TvSignIn.TooManyCodes).map(SignInRules::problem).toSet().size)
        assertEquals("K7QX-4M2P", SignInRules.grouped("K7QX4M2P"))
    }
}
