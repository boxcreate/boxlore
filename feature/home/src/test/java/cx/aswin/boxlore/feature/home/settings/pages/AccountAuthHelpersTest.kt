package cx.aswin.boxlore.feature.home.settings.pages

import cx.aswin.boxlore.feature.home.settings.toSettingsDestination
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
    fun cleanAccountError_recentLogin_returnsRecentLoginPrompt() {
        val result = cleanAccountError("com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException: This operation is sensitive and requires recent authentication.")
        assertEquals("For security, please sign out and sign in again before deleting your account.", result)
    }

    @Test
    fun isRecentLoginRequired_matchesExceptionAndMessages() {
        org.junit.Assert.assertTrue(cx.aswin.boxlore.core.network.RecentLoginRequiredException().isRecentLoginRequired())
        org.junit.Assert.assertTrue(RuntimeException("requires-recent-login").isRecentLoginRequired())
        org.junit.Assert.assertTrue(RuntimeException("ERROR_REQUIRES_RECENT_LOGIN").isRecentLoginRequired())
        org.junit.Assert.assertTrue(RuntimeException("CREDENTIAL_TOO_OLD_LOGIN_AGAIN").isRecentLoginRequired())
        org.junit.Assert.assertFalse(RuntimeException("network error").isRecentLoginRequired())
        org.junit.Assert.assertFalse((null as Throwable?).isRecentLoginRequired())
    }

    @Test
    fun isRecentLoginRequired_matchesWrappedCause() {
        val wrapped = java.lang.RuntimeException("Operation failed", cx.aswin.boxlore.core.network.RecentLoginRequiredException())
        org.junit.Assert.assertTrue(wrapped.isRecentLoginRequired())

        val deeplyWrapped = java.lang.IllegalStateException(
            java.util.concurrent.ExecutionException("outer", java.lang.RuntimeException("requires-recent-login")),
        )
        org.junit.Assert.assertTrue(deeplyWrapped.isRecentLoginRequired())
    }

    @Test
    fun isValidEmail_validatesCorrectly() {
        org.junit.Assert.assertTrue(isValidEmail("test@boxlore.example"))
        org.junit.Assert.assertTrue(isValidEmail("user.name+tag@sub.domain.org"))
        org.junit.Assert.assertFalse(isValidEmail(""))
        org.junit.Assert.assertFalse(isValidEmail("   "))
        org.junit.Assert.assertFalse(isValidEmail("notanemail"))
        org.junit.Assert.assertFalse(isValidEmail("@missinguser.com"))
    }

    @Test
    fun validatePasswordInputs_emptyEmail_returnsEmailPrompt() {
        assertEquals("Please enter your email address", validatePasswordInputs("", "123456", isSignUp = false))
        assertEquals("Please enter your email address", validatePasswordInputs("   ", "123456", isSignUp = true))
    }

    @Test
    fun validatePasswordInputs_invalidEmail_returnsValidEmailPrompt() {
        assertEquals("Please enter a valid email address", validatePasswordInputs("not-an-email", "123456", isSignUp = false))
        assertEquals("Please enter a valid email address", validatePasswordInputs("missing@domain", "123456", isSignUp = true))
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
    fun validatePasswordInputs_passphraseWithSpaces_preservedAndValid() {
        val passphrase = " correct horse battery staple "
        org.junit.Assert.assertNull(
            validatePasswordInputs(
                email = "test@boxlore.example",
                password = passphrase,
                isSignUp = false,
            ),
        )
        org.junit.Assert.assertNull(
            validatePasswordInputs(
                email = "test@boxlore.example",
                password = passphrase,
                isSignUp = true,
                confirmPassword = passphrase,
            ),
        )
    }

    @Test
    fun validatePasswordInputs_signUpConfirmPasswordValidation() {
        assertEquals(
            "Please confirm your password",
            validatePasswordInputs("test@boxlore.example", "123456", isSignUp = true, confirmPassword = ""),
        )
        assertEquals(
            "Passwords do not match",
            validatePasswordInputs("test@boxlore.example", "123456", isSignUp = true, confirmPassword = "654321"),
        )
        org.junit.Assert.assertNull(
            validatePasswordInputs("test@boxlore.example", "123456", isSignUp = true, confirmPassword = "123456"),
        )
    }

    @Test
    fun validatePasswordInputs_validInputs_returnsNull() {
        org.junit.Assert.assertNull(validatePasswordInputs("test@boxlore.example", "123456", isSignUp = true, confirmPassword = "123456"))
        org.junit.Assert.assertNull(validatePasswordInputs("test@boxlore.example", "pass", isSignUp = false))
    }

    @Test
    fun toSettingsDestination_account_returnsAccountDestination() {
        assertEquals(
            cx.aswin.boxlore.feature.home.settings.ProfileSettingsDestination.Account,
            "account".toSettingsDestination(),
        )
        assertEquals(
            cx.aswin.boxlore.feature.home.settings.ProfileSettingsDestination.Account,
            " ACCOUNT ".toSettingsDestination(),
        )
    }

    @Test
    fun accountAuthState_saver_preservesAllCriticalFields() {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        val originalState = AccountAuthState(
            authRepository = null,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = scope,
        )
        originalState.activeAuthMode = AuthMode.SIGN_UP
        originalState.usePasswordAuth = true
        originalState.email = "listener@boxlore.example"
        originalState.password = " secret pass "
        originalState.confirmPassword = " secret pass "
        originalState.passwordVisible = true
        originalState.confirmPasswordVisible = true
        originalState.magicLinkSent = true
        originalState.errorMessage = "Previous attempt error"

        val saver = AccountAuthState.saver(
            authRepository = null,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = scope,
        )

        val saverScope = androidx.compose.runtime.saveable.SaverScope { true }
        val saved = with(saver) { saverScope.save(originalState) }
        org.junit.Assert.assertNotNull(saved)

        val restored = saver.restore(saved!!) as AccountAuthState
        assertEquals(AuthMode.SIGN_UP, restored.activeAuthMode)
        org.junit.Assert.assertTrue(restored.usePasswordAuth)
        assertEquals("listener@boxlore.example", restored.email)
        assertEquals(" secret pass ", restored.password)
        assertEquals(" secret pass ", restored.confirmPassword)
        org.junit.Assert.assertTrue(restored.passwordVisible)
        org.junit.Assert.assertTrue(restored.confirmPasswordVisible)
        org.junit.Assert.assertTrue(restored.magicLinkSent)
        assertEquals("Previous attempt error", restored.errorMessage)
    }

    @Test
    fun accountAuthState_saver_withPartialOrCorruptedList_fallsBackToDefaultsSafely() {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        val saver = AccountAuthState.saver(
            authRepository = null,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = scope,
        )

        val emptyList = emptyList<Any?>()
        val restoredFromEmpty = saver.restore(emptyList) as AccountAuthState
        assertEquals(AuthMode.SIGN_IN, restoredFromEmpty.activeAuthMode)
        org.junit.Assert.assertFalse(restoredFromEmpty.usePasswordAuth)
        assertEquals("", restoredFromEmpty.email)
        assertEquals("", restoredFromEmpty.password)
        assertEquals("", restoredFromEmpty.confirmPassword)

        val corruptedList = listOf<Any?>("UNKNOWN_MODE", "not-a-bool", 12345)
        val restoredFromCorrupted = saver.restore(corruptedList) as AccountAuthState
        assertEquals(AuthMode.SIGN_IN, restoredFromCorrupted.activeAuthMode)
        org.junit.Assert.assertFalse(restoredFromCorrupted.usePasswordAuth)
        assertEquals("", restoredFromCorrupted.email)
    }
}
