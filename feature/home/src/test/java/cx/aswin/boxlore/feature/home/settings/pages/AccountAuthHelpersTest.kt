package cx.aswin.boxlore.feature.home.settings.pages

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountAuthHelpersTest {

    @Test
    fun cleanAccountError_nullInput_returnsDefaultMessage() {
        assertEquals("An unexpected error occurred", cleanAccountError(null))
    }

    @Test
    fun cleanAccountError_userNotFound_returnsFriendlySignUpPrompt() {
        val result = cleanAccountError("com.google.firebase.auth.FirebaseAuthInvalidUserException: user-not-found")
        assertEquals("No account found with this email. Try signing up instead.", result)
    }

    @Test
    fun cleanAccountError_wrongPassword_returnsIncorrectCredentials() {
        val result = cleanAccountError("wrong-password")
        assertEquals("Incorrect password or credentials. Please try again.", result)
    }

    @Test
    fun cleanAccountError_emailAlreadyInUse_returnsSignInPrompt() {
        val result = cleanAccountError("The email-already-in-use by another account")
        assertEquals("This email is already registered. Try signing in instead.", result)
    }

    @Test
    fun cleanAccountError_weakPassword_returnsPasswordRequirement() {
        val result = cleanAccountError("weak-password")
        assertEquals("Password is too weak. Please use at least 6 characters.", result)
    }

    @Test
    fun cleanAccountError_invalidEmail_returnsValidEmailPrompt() {
        val result = cleanAccountError("invalid-email format")
        assertEquals("Please enter a valid email address.", result)
    }

    @Test
    fun cleanAccountError_networkIssue_returnsNetworkMessage() {
        val result = cleanAccountError("network connection failed")
        assertEquals("Network error. Check your connection and try again.", result)
    }

    @Test
    fun cleanAccountError_unknownError_preservesMessage() {
        val message = "Custom error from identity provider"
        assertEquals(message, cleanAccountError(message))
    }

    @Test
    fun validatePasswordInputs_emptyEmail_returnsEmailPrompt() {
        assertEquals("Please enter your email address", validatePasswordInputs("", "123456", isSignUp = false))
        assertEquals("Please enter your email address", validatePasswordInputs("   ", "123456", isSignUp = true))
    }

    @Test
    fun validatePasswordInputs_emptyPassword_returnsPasswordPrompt() {
        assertEquals("Please enter your password", validatePasswordInputs("test@boxlore.example", "", isSignUp = false))
        assertEquals("Please enter your password", validatePasswordInputs("test@boxlore.example", "   ", isSignUp = true))
    }

    @Test
    fun validatePasswordInputs_shortPasswordSignUp_returnsMinLengthPrompt() {
        assertEquals("Password must be at least 6 characters", validatePasswordInputs("test@boxlore.example", "12345", isSignUp = true))
    }

    @Test
    fun validatePasswordInputs_validInputs_returnsNull() {
        org.junit.Assert.assertNull(validatePasswordInputs("test@boxlore.example", "123456", isSignUp = true))
        org.junit.Assert.assertNull(validatePasswordInputs("test@boxlore.example", "pass", isSignUp = false))
    }
}
