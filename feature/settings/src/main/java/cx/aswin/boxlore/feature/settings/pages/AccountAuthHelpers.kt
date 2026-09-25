package cx.aswin.boxlore.feature.settings.pages

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import cx.aswin.boxlore.core.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal const val GOOGLE_SERVER_CLIENT_ID =
    "74591511411-l1jc8fftg8u7hcv354ooi644omsuksei.apps.googleusercontent.com"

internal const val GOOGLE_SIGN_IN_TIMEOUT_MS = 15_000L

internal enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
}

internal data class EmailInputState(
    val email: String,
    val isSignUp: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class EmailInputActions(
    val onEmailChange: (String) -> Unit,
    val onSendLink: () -> Unit,
    val onSwitchToPassword: () -> Unit,
    val onInputFocused: () -> Unit,
)

internal data class PasswordInputState(
    val email: String,
    val password: String,
    val confirmPassword: String = "",
    val passwordVisible: Boolean,
    val confirmPasswordVisible: Boolean = false,
    val isSignUp: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class PasswordInputActions(
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onConfirmPasswordChange: (String) -> Unit = {},
    val onTogglePasswordVisible: () -> Unit,
    val onToggleConfirmPasswordVisible: () -> Unit = {},
    val onForgotPassword: () -> Unit,
    val onSubmit: () -> Unit,
    val onSwitchToEmailLink: () -> Unit,
    val onInputFocused: () -> Unit,
    val onNextField: () -> Unit,
)

internal sealed interface GoogleAuthOutcome {
    data object Success : GoogleAuthOutcome
    data object Cancelled : GoogleAuthOutcome
    data class Failure(val message: String) : GoogleAuthOutcome
}

internal fun extractGoogleIdToken(credential: Credential): String? {
    if (credential !is CustomCredential) return null
    if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
    return GoogleIdTokenCredential.createFrom(credential.data).idToken
}

internal suspend fun requestGoogleCredential(
    context: Context,
): GetCredentialResponse = withTimeout(GOOGLE_SIGN_IN_TIMEOUT_MS) {
    val credentialManager = CredentialManager.create(context)
    val option = GetSignInWithGoogleOption.Builder(
        serverClientId = GOOGLE_SERVER_CLIENT_ID,
    ).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    credentialManager.getCredential(context = context, request = request)
}

internal suspend fun performGoogleSignIn(
    context: Context,
    authRepository: AuthRepository?,
): GoogleAuthOutcome {
    val response = try {
        requestGoogleCredential(context)
    } catch (_: GetCredentialCancellationException) {
        return GoogleAuthOutcome.Cancelled
    } catch (e: GetCredentialException) {
        return GoogleAuthOutcome.Failure(e.localizedMessage ?: "Google sign-in error")
    } catch (e: Exception) {
        return GoogleAuthOutcome.Failure(e.localizedMessage ?: "Google sign-in timed out")
    }

    val idToken = extractGoogleIdToken(response.credential)
        ?: return GoogleAuthOutcome.Failure("Unexpected credential received")

    val result = authRepository?.signInWithGoogle(idToken)
    if (result?.isSuccess == true) {
        return GoogleAuthOutcome.Success
    }
    return GoogleAuthOutcome.Failure(
        result?.exceptionOrNull()?.localizedMessage ?: "Google sign-in failed",
    )
}

internal data class SavedAccountAuthState(
    val activeAuthMode: AuthMode = AuthMode.SIGN_IN,
    val usePasswordAuth: Boolean = false,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val magicLinkSent: Boolean = false,
    val errorMessage: String? = null,
    val isAwaitingVerification: Boolean = false,
    val resendCooldownSeconds: Int = 0,
)

internal class AccountAuthState(
    var authRepository: AuthRepository?,
    var context: Context,
    var activity: Activity?,
    var focusManager: FocusManager,
    var scope: CoroutineScope,
    initialState: SavedAccountAuthState = SavedAccountAuthState(),
) {
    var activeAuthMode by mutableStateOf(initialState.activeAuthMode)
    var usePasswordAuth by mutableStateOf(initialState.usePasswordAuth)
    var isAnyInputFocused by mutableStateOf(false)

    var email by mutableStateOf(initialState.email)
    var password by mutableStateOf(initialState.password)
    var confirmPassword by mutableStateOf(initialState.confirmPassword)
    var passwordVisible by mutableStateOf(initialState.passwordVisible)
    var confirmPasswordVisible by mutableStateOf(initialState.confirmPasswordVisible)

    var isGoogleLoading by mutableStateOf(false)
    var isEmailLoading by mutableStateOf(false)
    val isAnyLoading: Boolean get() = isGoogleLoading || isEmailLoading

    var magicLinkSent by mutableStateOf(initialState.magicLinkSent)
    var errorMessage by mutableStateOf<String?>(initialState.errorMessage)

    var isAwaitingVerification by mutableStateOf(
        initialState.isAwaitingVerification ||
            (
                AccountVerificationStorage.getPref(context) &&
                (authRepository?.currentUser?.value == null || authRepository?.currentUser?.value?.isEmailVerified == false)
            ),
    )
    var resendCooldownSeconds by mutableStateOf(initialState.resendCooldownSeconds)
    var isCheckingVerification by mutableStateOf(false)
    private var cooldownJob: kotlinx.coroutines.Job? = null

    init {
        if (isAwaitingVerification) {
            if (email.isBlank()) {
                email = authRepository?.currentUser?.value?.email.orEmpty()
            }
            if (resendCooldownSeconds > 0) {
                startResendCooldownTimer(resendCooldownSeconds)
            }
        }
    }

    fun startResendCooldownTimer(seconds: Int = 30) {
        cooldownJob?.cancel()
        resendCooldownSeconds = seconds
        cooldownJob = scope.launch {
            while (resendCooldownSeconds > 0) {
                kotlinx.coroutines.delay(1000L)
                resendCooldownSeconds--
            }
        }
    }

    fun selectAuthMode(mode: AuthMode) {
        activeAuthMode = mode
        if (mode == AuthMode.SIGN_UP) {
            usePasswordAuth = true
        }
        isAnyInputFocused = false
        errorMessage = null
        magicLinkSent = false
        isAwaitingVerification = false
        AccountVerificationStorage.setPref(context, false)
        confirmPassword = ""
    }

    fun resetToNewEmail() {
        cooldownJob?.cancel()
        resendCooldownSeconds = 0
        isAwaitingVerification = false
        AccountVerificationStorage.setPref(context, false)
        magicLinkSent = false
        email = ""
        password = ""
        confirmPassword = ""
        errorMessage = null
        scope.launch {
            val repo = authRepository ?: return@launch
            val user = repo.currentUser.value
            if (user != null && !user.isEmailVerified) {
                val deleteResult = repo.deleteAccount()
                if (deleteResult.isFailure) {
                    repo.signOut()
                }
            }
        }
    }

    fun submitEmailLink() {
        val trimmedEmail = email.trim()
        email = trimmedEmail
        if (trimmedEmail.isBlank()) {
            errorMessage = "Please enter your email address"
            return
        }
        if (!isValidEmail(trimmedEmail)) {
            errorMessage = ERROR_INVALID_EMAIL
            return
        }
        focusManager.clearFocus()
        isAnyInputFocused = false
        isEmailLoading = true
        errorMessage = null
        scope.launch {
            val result = authRepository?.sendMagicLink(trimmedEmail)
            isEmailLoading = false
            if (result?.isSuccess == true) {
                magicLinkSent = true
            } else {
                errorMessage = cleanAccountError(result?.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun submitPasswordAuth() {
        val trimmedEmail = email.trim()
        email = trimmedEmail
        val isSignUp = activeAuthMode == AuthMode.SIGN_UP

        val validationError = validatePasswordInputs(trimmedEmail, password, isSignUp, confirmPassword)
        if (validationError != null) {
            errorMessage = validationError
            return
        }

        val pass = password
        focusManager.clearFocus()
        isAnyInputFocused = false
        isEmailLoading = true
        errorMessage = null

        scope.launch {
            val repo = authRepository
            val result = executePasswordAuth(repo, trimmedEmail, pass, isSignUp)
            if (result?.isSuccess == true) {
                handlePasswordAuthSuccess(repo, isSignUp)
            } else {
                handlePasswordAuthFailure(repo, result, trimmedEmail, pass, isSignUp)
            }
        }
    }

    private suspend fun handlePasswordAuthSuccess(
        repo: AuthRepository?,
        isSignUp: Boolean,
    ) {
        password = ""
        confirmPassword = ""
        if (isSignUp) {
            isAwaitingVerification = true
            AccountVerificationStorage.setPref(context, true)
            val verifyResult = repo?.sendEmailVerification()
            isEmailLoading = false
            if (verifyResult?.isSuccess == true) {
                startResendCooldownTimer(30)
            } else {
                errorMessage = cleanAccountError(verifyResult?.exceptionOrNull()?.localizedMessage)
            }
        } else {
            isEmailLoading = false
            isAwaitingVerification = false
            AccountVerificationStorage.setPref(context, false)
            showAccountToast(context, "Signed in!")
        }
    }

    private suspend fun tryUnverifiedExistingAccount(
        repo: AuthRepository,
        trimmedEmail: String,
        pass: String,
    ): Boolean {
        val signInAttempt = repo.signInWithEmailPassword(trimmedEmail, pass)
        if (signInAttempt.isSuccess) {
            val user = repo.currentUser.value
            if (user != null && !user.isEmailVerified) {
                password = ""
                confirmPassword = ""
                isAwaitingVerification = true
                AccountVerificationStorage.setPref(context, true)
                val verifyResult = repo.sendEmailVerification()
                if (verifyResult.isSuccess) {
                    startResendCooldownTimer(30)
                } else {
                    errorMessage = cleanAccountError(verifyResult.exceptionOrNull()?.localizedMessage)
                }
                isEmailLoading = false
                return true
            }
        }
        return false
    }

    private suspend fun handlePasswordAuthFailure(
        repo: AuthRepository?,
        result: Result<*>?,
        trimmedEmail: String,
        pass: String,
        isSignUp: Boolean,
    ) {
        val errorMsg = result?.exceptionOrNull()?.localizedMessage.orEmpty()
        val isCollision = isEmailCollisionError(errorMsg)

        val canAttemptRecovery = isSignUp && isCollision && repo != null
        if (canAttemptRecovery && tryUnverifiedExistingAccount(repo, trimmedEmail, pass)) {
            return
        }

        isEmailLoading = false
        errorMessage = cleanAccountError(result?.exceptionOrNull()?.localizedMessage)
    }

    fun checkVerificationStatus(
        silentOnFailure: Boolean = false,
        onSuccess: () -> Unit = {},
    ) {
        if (isCheckingVerification) return
        isCheckingVerification = true
        if (!silentOnFailure) {
            errorMessage = null
        }
        scope.launch {
            try {
                val result = authRepository?.reloadUser()
                val user = result?.getOrNull()
                if (user?.isEmailVerified == true) {
                    isAwaitingVerification = false
                    AccountVerificationStorage.setPref(context, false)
                    cooldownJob?.cancel()
                    resendCooldownSeconds = 0
                    errorMessage = null
                    showAccountToast(context, "Email verified! Welcome to boxlore.")
                    onSuccess()
                } else if (!silentOnFailure) {
                    val error = result?.exceptionOrNull()
                    if (error != null) {
                        errorMessage = cleanAccountError(error.localizedMessage)
                    } else {
                        errorMessage = "We haven't received verification yet. Please tap the link in your email and try again."
                    }
                }
            } finally {
                isCheckingVerification = false
            }
        }
    }

    fun resendVerificationEmail() {
        if (resendCooldownSeconds > 0 || isEmailLoading) return
        isEmailLoading = true
        errorMessage = null
        scope.launch {
            val result = authRepository?.sendEmailVerification()
            isEmailLoading = false
            if (result?.isSuccess == true) {
                startResendCooldownTimer(30)
                showAccountToast(context, "Verification email resent")
            } else {
                errorMessage = cleanAccountError(result?.exceptionOrNull()?.localizedMessage)
            }
        }
    }

    fun signOutAndResetToSignUp() {
        cooldownJob?.cancel()
        resendCooldownSeconds = 0
        isAwaitingVerification = false
        AccountVerificationStorage.setPref(context, false)
        activeAuthMode = AuthMode.SIGN_UP
        usePasswordAuth = true
        if (email.isBlank()) {
            email = authRepository?.currentUser?.value?.email.orEmpty()
        }
        password = ""
        confirmPassword = ""
        errorMessage = null
        authRepository?.signOut()
    }

    fun handleGoogleSignIn() {
        if (isAnyLoading) return
        focusManager.clearFocus()
        isGoogleLoading = true
        errorMessage = null
        scope.launch {
            val targetContext = activity ?: context
            when (val outcome = performGoogleSignIn(targetContext, authRepository)) {
                GoogleAuthOutcome.Success -> {
                    isAwaitingVerification = false
                    AccountVerificationStorage.setPref(context, false)
                    showAccountToast(context, "Signed in with Google!")
                }
                GoogleAuthOutcome.Cancelled -> Unit
                is GoogleAuthOutcome.Failure -> {
                    errorMessage = outcome.message
                }
            }
            isGoogleLoading = false
        }
    }

    fun handleForgotPassword() {
        val trimmedEmail = email.trim()
        email = trimmedEmail
        if (trimmedEmail.isBlank()) {
            errorMessage = "Enter your email address above to reset password"
            return
        }
        if (!isValidEmail(trimmedEmail)) {
            errorMessage = ERROR_INVALID_EMAIL
            return
        }
        focusManager.clearFocus()
        scope.launch {
            val result = authRepository?.sendPasswordReset(trimmedEmail)
            if (result?.isSuccess == true) {
                showAccountToast(context, "Password reset email sent")
            } else {
                errorMessage = result?.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email"
            }
        }
    }

    companion object {
        fun saver(
            authRepository: AuthRepository?,
            context: Context,
            activity: Activity?,
            focusManager: FocusManager,
            scope: CoroutineScope,
        ): Saver<AccountAuthState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.activeAuthMode.name,
                    state.usePasswordAuth,
                    state.email,
                    state.passwordVisible,
                    state.confirmPasswordVisible,
                    state.magicLinkSent,
                    state.errorMessage,
                    state.isAwaitingVerification,
                    state.resendCooldownSeconds,
                )
            },
            restore = { list ->
                AccountAuthState(
                    authRepository = authRepository,
                    context = context,
                    activity = activity,
                    focusManager = focusManager,
                    scope = scope,
                    initialState = restoreSavedAccountAuthState(list),
                )
            },
        )
    }
}

private fun restoreSavedAccountAuthState(list: List<*>): SavedAccountAuthState {
    val activeAuthMode = (list.getOrNull(0) as? String)?.let {
        runCatching { AuthMode.valueOf(it) }.getOrNull()
    } ?: AuthMode.SIGN_IN
    val usePasswordAuth = list.getOrNull(1) as? Boolean ?: false
    val email = (list.getOrNull(2) as? String).orEmpty()
    val isLegacyNineItem = list.size >= 9 && list.getOrNull(3) is String

    return if (isLegacyNineItem) {
        SavedAccountAuthState(
            activeAuthMode = activeAuthMode,
            usePasswordAuth = usePasswordAuth,
            email = email,
            password = "",
            confirmPassword = "",
            passwordVisible = list.getOrNull(5) as? Boolean ?: false,
            confirmPasswordVisible = list.getOrNull(6) as? Boolean ?: false,
            magicLinkSent = list.getOrNull(7) as? Boolean ?: false,
            errorMessage = list.getOrNull(8) as? String,
            isAwaitingVerification = false,
            resendCooldownSeconds = 0,
        )
    } else {
        SavedAccountAuthState(
            activeAuthMode = activeAuthMode,
            usePasswordAuth = usePasswordAuth,
            email = email,
            password = "",
            confirmPassword = "",
            passwordVisible = list.getOrNull(3) as? Boolean ?: false,
            confirmPasswordVisible = list.getOrNull(4) as? Boolean ?: false,
            magicLinkSent = list.getOrNull(5) as? Boolean ?: false,
            errorMessage = list.getOrNull(6) as? String,
            isAwaitingVerification = list.getOrNull(7) as? Boolean ?: false,
            resendCooldownSeconds = (list.getOrNull(8) as? Number)?.toInt() ?: 0,
        )
    }
}

private suspend fun executePasswordAuth(
    authRepository: AuthRepository?,
    email: String,
    password: String,
    isSignUp: Boolean,
): Result<*>? = if (isSignUp) {
    authRepository?.signUpWithEmailPassword(email, password)
} else {
    authRepository?.signInWithEmailPassword(email, password)
}

internal fun AccountAuthState.toEmailInputState() = EmailInputState(
    email = email,
    isSignUp = activeAuthMode == AuthMode.SIGN_UP,
    isLoading = isEmailLoading,
    errorMessage = errorMessage,
)

internal fun AccountAuthState.toEmailInputActions(actionButtonRequester: BringIntoViewRequester) = EmailInputActions(
    onEmailChange = {
        email = it
        errorMessage = null
    },
    onSendLink = ::submitEmailLink,
    onSwitchToPassword = {
        usePasswordAuth = true
        errorMessage = null
    },
    onInputFocused = {
        isAnyInputFocused = true
        scope.launch { runCatching { actionButtonRequester.bringIntoView() } }
    },
)

internal fun AccountAuthState.toPasswordInputState() = PasswordInputState(
    email = email,
    password = password,
    confirmPassword = confirmPassword,
    passwordVisible = passwordVisible,
    confirmPasswordVisible = confirmPasswordVisible,
    isSignUp = activeAuthMode == AuthMode.SIGN_UP,
    isLoading = isEmailLoading,
    errorMessage = errorMessage,
)

internal fun AccountAuthState.toPasswordInputActions(
    actionButtonRequester: BringIntoViewRequester,
    onNextField: () -> Unit,
) = PasswordInputActions(
    onEmailChange = {
        email = it
        errorMessage = null
    },
    onPasswordChange = {
        password = it
        errorMessage = null
    },
    onConfirmPasswordChange = {
        confirmPassword = it
        errorMessage = null
    },
    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
    onToggleConfirmPasswordVisible = { confirmPasswordVisible = !confirmPasswordVisible },
    onForgotPassword = ::handleForgotPassword,
    onSubmit = ::submitPasswordAuth,
    onSwitchToEmailLink = {
        usePasswordAuth = false
        errorMessage = null
    },
    onInputFocused = {
        isAnyInputFocused = true
        scope.launch { runCatching { actionButtonRequester.bringIntoView() } }
    },
    onNextField = onNextField,
)

@Composable
internal fun rememberAccountAuthState(
    authRepository: AuthRepository?,
    context: Context = LocalContext.current,
    activity: Activity? = remember(context) { context.findActivity() },
    focusManager: FocusManager = LocalFocusManager.current,
    scope: CoroutineScope = rememberCoroutineScope(),
): AccountAuthState {
    val state = rememberSaveable(
        saver = AccountAuthState.saver(authRepository, context, activity, focusManager, scope),
    ) {
        AccountAuthState(authRepository, context, activity, focusManager, scope)
    }
    state.authRepository = authRepository
    state.context = context
    state.activity = activity
    state.focusManager = focusManager
    state.scope = scope
    LaunchedEffect(state.isAwaitingVerification) {
        if (state.isAwaitingVerification && state.email.isBlank()) {
            state.email = authRepository?.currentUser?.value?.email.orEmpty()
        }
    }
    return state
}
