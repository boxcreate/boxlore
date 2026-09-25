package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.feature.settings.toSettingsDestination
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
    fun cleanAccountError_tooManyRequests_returnsUserFriendlyMessage() {
        val result = cleanAccountError("Firebase: We have blocked all requests from this device due to unusual activity (too-many-requests)")
        assertEquals("Too many attempts. Please wait a few minutes before trying again.", result)
    }

    @Test
    fun cleanAccountError_userDisabled_returnsDisabledMessage() {
        val result = cleanAccountError("com.google.firebase.auth.FirebaseAuthInvalidUserException: The user account has been disabled by an administrator. (user-disabled)")
        assertEquals("This account has been disabled. Please contact support.", result)
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
        org.junit.Assert.assertTrue(cx.aswin.boxlore.core.auth.RecentLoginRequiredException().isRecentLoginRequired())
        org.junit.Assert.assertTrue(RuntimeException("requires-recent-login").isRecentLoginRequired())
        org.junit.Assert.assertTrue(RuntimeException("ERROR_REQUIRES_RECENT_LOGIN").isRecentLoginRequired())
        org.junit.Assert.assertTrue(RuntimeException("CREDENTIAL_TOO_OLD_LOGIN_AGAIN").isRecentLoginRequired())
        org.junit.Assert.assertFalse(RuntimeException("network error").isRecentLoginRequired())
        org.junit.Assert.assertFalse((null as Throwable?).isRecentLoginRequired())
    }

    @Test
    fun isRecentLoginRequired_matchesWrappedCause() {
        val wrapped = java.lang.RuntimeException("Operation failed", cx.aswin.boxlore.core.auth.RecentLoginRequiredException())
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
            cx.aswin.boxlore.feature.settings.ProfileSettingsDestination.Account,
            "account".toSettingsDestination(),
        )
        assertEquals(
            cx.aswin.boxlore.feature.settings.ProfileSettingsDestination.Account,
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
        originalState.isAwaitingVerification = true
        originalState.resendCooldownSeconds = 25

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
        assertEquals("", restored.password)
        assertEquals("", restored.confirmPassword)
        org.junit.Assert.assertTrue(restored.passwordVisible)
        org.junit.Assert.assertTrue(restored.confirmPasswordVisible)
        org.junit.Assert.assertTrue(restored.magicLinkSent)
        assertEquals("Previous attempt error", restored.errorMessage)
        org.junit.Assert.assertTrue(restored.isAwaitingVerification)
        assertEquals(25, restored.resendCooldownSeconds)
    }

    @Test
    fun accountAuthState_saver_legacyListWithPasswords_discardsPasswords() {
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

        val legacyList = listOf(
            AuthMode.SIGN_IN.name,
            true,
            "legacy@boxlore.example",
            "plaintext-pass",
            "plaintext-confirm",
            true,
            false,
            false,
            null,
        )
        val restored = saver.restore(legacyList) as AccountAuthState
        assertEquals("legacy@boxlore.example", restored.email)
        assertEquals("", restored.password)
        assertEquals("", restored.confirmPassword)
        org.junit.Assert.assertTrue(restored.passwordVisible)
        org.junit.Assert.assertFalse(restored.confirmPasswordVisible)
        org.junit.Assert.assertFalse(restored.isAwaitingVerification)
        assertEquals(0, restored.resendCooldownSeconds)
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
        org.junit.Assert.assertFalse(restoredFromEmpty.isAwaitingVerification)
        assertEquals(0, restoredFromEmpty.resendCooldownSeconds)

        val corruptedList = listOf<Any?>("UNKNOWN_MODE", "not-a-bool", 12345)
        val restoredFromCorrupted = saver.restore(corruptedList) as AccountAuthState
        assertEquals(AuthMode.SIGN_IN, restoredFromCorrupted.activeAuthMode)
        org.junit.Assert.assertFalse(restoredFromCorrupted.usePasswordAuth)
        assertEquals("", restoredFromCorrupted.email)
        org.junit.Assert.assertFalse(restoredFromCorrupted.isAwaitingVerification)
        assertEquals(0, restoredFromCorrupted.resendCooldownSeconds)
    }

    private class TestAuthRepository(
        initialUser: cx.aswin.boxlore.core.model.BoxLoreUser? = null,
    ) : cx.aswin.boxlore.core.auth.AuthRepository {
        val userFlow = kotlinx.coroutines.flow.MutableStateFlow(initialUser)
        override val currentUser: kotlinx.coroutines.flow.StateFlow<cx.aswin.boxlore.core.model.BoxLoreUser?> = userFlow
        override val currentUserId: String? get() = userFlow.value?.uid

        var signUpCallCount = 0
        var signInCallCount = 0
        var sendVerificationCallCount = 0
        var reloadCallCount = 0
        var shouldFailVerification = false
        var shouldFailReload = false
        var isEmailVerifiedOnReload = false

        override suspend fun signInWithGoogle(idToken: String): Result<cx.aswin.boxlore.core.model.BoxLoreUser> = error("unused")
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<cx.aswin.boxlore.core.model.BoxLoreUser> {
            signInCallCount++
            val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid-$email", email = email, displayName = null, isEmailVerified = true)
            userFlow.value = user
            return Result.success(user)
        }
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<cx.aswin.boxlore.core.model.BoxLoreUser> {
            signUpCallCount++
            val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid-$email", email = email, displayName = null, isEmailVerified = false)
            userFlow.value = user
            return Result.success(user)
        }
        override suspend fun sendMagicLink(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun signInWithEmailLink(email: String, emailLink: String): Result<cx.aswin.boxlore.core.model.BoxLoreUser> = error("unused")
        override fun isSignInWithEmailLink(link: String): Boolean = false
        override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun sendEmailVerification(): Result<Unit> {
            sendVerificationCallCount++
            return if (shouldFailVerification) Result.failure(RuntimeException("Send verification failed")) else Result.success(Unit)
        }
        override suspend fun reloadUser(): Result<cx.aswin.boxlore.core.model.BoxLoreUser?> {
            reloadCallCount++
            if (shouldFailReload) return Result.failure(RuntimeException("Reload failed"))
            val updated = userFlow.value?.copy(isEmailVerified = isEmailVerifiedOnReload)
            userFlow.value = updated
            return Result.success(updated)
        }
        override fun signOut() {
            userFlow.value = null
        }
        override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
        override suspend fun getIdToken(forceRefresh: Boolean): String? = "mock-token"
    }

    @Test
    fun accountAuthState_submitPasswordAuth_signUp_triggersEmailVerificationAndAwaitingState() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val fakeRepo = TestAuthRepository()

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.activeAuthMode = AuthMode.SIGN_UP
        state.email = "newuser@boxlore.example"
        state.password = "password123"
        state.confirmPassword = "password123"

        state.submitPasswordAuth()
        testScheduler.runCurrent()

        org.junit.Assert.assertTrue(state.isAwaitingVerification)
        assertEquals(1, fakeRepo.signUpCallCount)
        assertEquals(1, fakeRepo.sendVerificationCallCount)
        org.junit.Assert.assertTrue(state.resendCooldownSeconds > 0)
    }

    @Test
    fun accountAuthState_submitPasswordAuth_signIn_doesNotAwaitVerification() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val fakeRepo = TestAuthRepository()

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.activeAuthMode = AuthMode.SIGN_IN
        state.email = "existing@boxlore.example"
        state.password = "password123"

        state.submitPasswordAuth()
        testScheduler.advanceUntilIdle()

        org.junit.Assert.assertFalse(state.isAwaitingVerification)
        assertEquals(1, fakeRepo.signInCallCount)
        assertEquals(0, fakeRepo.sendVerificationCallCount)
    }

    @Test
    fun accountAuthState_checkVerificationStatus_whenVerified_clearsAwaitingAndCallsOnSuccess() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid", email = "test@boxlore.example", displayName = null, isEmailVerified = false)
        val fakeRepo = TestAuthRepository(initialUser = user)
        fakeRepo.isEmailVerifiedOnReload = true

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.isAwaitingVerification = true
        var successCalled = false

        state.checkVerificationStatus(onSuccess = { successCalled = true })
        testScheduler.advanceUntilIdle()

        org.junit.Assert.assertFalse(state.isAwaitingVerification)
        org.junit.Assert.assertTrue(successCalled)
        assertEquals(0, state.resendCooldownSeconds)
        org.junit.Assert.assertNull(state.errorMessage)
    }

    @Test
    fun accountAuthState_checkVerificationStatus_whenUnverified_setsErrorMessage() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid", email = "test@boxlore.example", displayName = null, isEmailVerified = false)
        val fakeRepo = TestAuthRepository(initialUser = user)
        fakeRepo.isEmailVerifiedOnReload = false

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.isAwaitingVerification = true
        var successCalled = false

        state.checkVerificationStatus(silentOnFailure = false, onSuccess = { successCalled = true })
        testScheduler.advanceUntilIdle()

        org.junit.Assert.assertTrue(state.isAwaitingVerification)
        org.junit.Assert.assertFalse(successCalled)
        assertEquals("We haven't received verification yet. Please tap the link in your email and try again.", state.errorMessage)
    }

    @Test
    fun accountAuthState_signOutAndResetToSignUp_preservesEmailAndResetsMode() {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid", email = "typo@boxlore.example", displayName = null, isEmailVerified = false)
        val fakeRepo = TestAuthRepository(initialUser = user)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = scope,
        )
        state.isAwaitingVerification = true
        state.email = "typo@boxlore.example"
        state.password = "secret"
        state.resendCooldownSeconds = 20

        state.signOutAndResetToSignUp()

        org.junit.Assert.assertFalse(state.isAwaitingVerification)
        assertEquals(AuthMode.SIGN_UP, state.activeAuthMode)
        org.junit.Assert.assertTrue(state.usePasswordAuth)
        assertEquals("typo@boxlore.example", state.email)
        assertEquals("", state.password)
        assertEquals(0, state.resendCooldownSeconds)
        org.junit.Assert.assertNull(fakeRepo.currentUser.value)
    }

    @Test
    fun accountAuthState_resendVerificationEmail_respectsCooldownAndDispatches() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val fakeRepo = TestAuthRepository()

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.resendCooldownSeconds = 15
        state.resendVerificationEmail()
        testScheduler.runCurrent()
        assertEquals(0, fakeRepo.sendVerificationCallCount)

        state.resendCooldownSeconds = 0
        state.resendVerificationEmail()
        testScheduler.runCurrent()
        assertEquals(1, fakeRepo.sendVerificationCallCount)
        org.junit.Assert.assertTrue(state.resendCooldownSeconds > 0)
    }

    @Test
    fun accountAuthState_submitPasswordAuth_signUp_whenVerificationFails_showsErrorAndNoCooldown() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val fakeRepo = TestAuthRepository()
        fakeRepo.shouldFailVerification = true

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.activeAuthMode = AuthMode.SIGN_UP
        state.email = "newuser@boxlore.example"
        state.password = "password123"
        state.confirmPassword = "password123"

        state.submitPasswordAuth()
        testScheduler.runCurrent()

        org.junit.Assert.assertTrue(state.isAwaitingVerification)
        assertEquals(1, fakeRepo.signUpCallCount)
        assertEquals(1, fakeRepo.sendVerificationCallCount)
        assertEquals("Send verification failed", state.errorMessage)
        assertEquals(0, state.resendCooldownSeconds)
    }

    @Test
    fun accountAuthState_submitPasswordAuth_signUp_trimsEmailInState() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val fakeRepo = TestAuthRepository()

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.activeAuthMode = AuthMode.SIGN_UP
        state.email = "  newuser@boxlore.example  "
        state.password = "password123"
        state.confirmPassword = "password123"

        state.submitPasswordAuth()
        testScheduler.runCurrent()

        assertEquals("newuser@boxlore.example", state.email)
    }

    @Test
    fun accountAuthState_signOutAndResetToSignUp_recoversEmailFromCurrentUserIfBlank() {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid", email = "recovered@boxlore.example", displayName = null, isEmailVerified = false)
        val fakeRepo = TestAuthRepository(initialUser = user)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = scope,
        )
        state.isAwaitingVerification = true
        state.email = ""
        state.password = "secret"

        state.signOutAndResetToSignUp()

        org.junit.Assert.assertFalse(state.isAwaitingVerification)
        assertEquals("recovered@boxlore.example", state.email)
        assertEquals(AuthMode.SIGN_UP, state.activeAuthMode)
        org.junit.Assert.assertTrue(state.usePasswordAuth)
        org.junit.Assert.assertNull(fakeRepo.currentUser.value)
    }

    @Test
    fun accountAuthState_checkVerificationStatus_whenReloadFails_displaysCleanError() = kotlinx.coroutines.test.runTest {
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockFocusManager = org.mockito.Mockito.mock(androidx.compose.ui.focus.FocusManager::class.java)
        val user = cx.aswin.boxlore.core.model.BoxLoreUser(uid = "uid", email = "test@boxlore.example", displayName = null, isEmailVerified = false)
        val fakeRepo = TestAuthRepository(initialUser = user)
        fakeRepo.shouldFailReload = true

        val state = AccountAuthState(
            authRepository = fakeRepo,
            context = mockContext,
            activity = null,
            focusManager = mockFocusManager,
            scope = this,
        )
        state.isAwaitingVerification = true

        state.checkVerificationStatus(silentOnFailure = false)
        testScheduler.advanceUntilIdle()

        org.junit.Assert.assertTrue(state.isAwaitingVerification)
        assertEquals("Reload failed", state.errorMessage)
    }
}
