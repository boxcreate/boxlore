package cx.aswin.boxlore.feature.home.settings.pages

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.network.AuthRepository
import cx.aswin.boxlore.core.network.RecentLoginRequiredException
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

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val RECENT_LOGIN_KEYWORDS = listOf(
    "recent-login",
    "recent login",
    "recent_login",
    "recentlogin",
    "recent authentication",
    "requires-recent-login",
    "credential_too_old",
)

private val ACCOUNT_ERROR_MAPPINGS = listOf(
    listOf("user-not-found", "no user") to
        "No account found with this email. Try signing up instead.",
    listOf("wrong-password", "invalid-credential") to
        "Incorrect password or credentials. Please try again.",
    listOf("email-already-in-use", "already registered") to
        "This email is already registered. Try signing in instead.",
    listOf("weak-password") to
        "Password is too weak. Please use at least 6 characters.",
    listOf("invalid-email") to
        "Please enter a valid email address.",
    RECENT_LOGIN_KEYWORDS to
        "For security, please sign out and sign in again before deleting your account.",
    listOf("network") to
        "Network error. Check your connection and try again.",
)

internal fun cleanAccountError(raw: String?): String {
    if (raw == null) return "An unexpected error occurred"
    for ((keywords, message) in ACCOUNT_ERROR_MAPPINGS) {
        if (keywords.any { raw.contains(it, ignoreCase = true) }) {
            return message
        }
    }
    return raw
}

internal fun Throwable?.isRecentLoginRequired(): Boolean {
    if (this == null) return false
    var current: Throwable? = this
    while (current != null) {
        if (current is RecentLoginRequiredException) return true
        if (current.javaClass.simpleName.contains("RecentLoginRequired", ignoreCase = true)) return true
        val msg = current.message.orEmpty()
        if (RECENT_LOGIN_KEYWORDS.any { msg.contains(it, ignoreCase = true) }) return true
        current = current.cause
    }
    return false
}

private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()

internal fun isValidEmail(email: String): Boolean {
    if (email.isBlank()) return false
    return runCatching {
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }.getOrElse {
        EMAIL_REGEX.matches(email)
    }
}

internal fun openGmailOrEmailApp(context: Context) {
    try {
        val gmailIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
        if (gmailIntent != null) {
            gmailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(gmailIntent)
        } else {
            val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_EMAIL)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (emailIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(emailIntent)
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }
        }
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open email app", Toast.LENGTH_SHORT).show()
        }
    }
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

internal fun validatePasswordInputs(
    email: String,
    password: String,
    isSignUp: Boolean,
    confirmPassword: String = "",
): String? = when {
    email.isBlank() -> "Please enter your email address"
    !isValidEmail(email) -> "Please enter a valid email address"
    password.isBlank() -> "Please enter your password"
    isSignUp && password.length < 6 -> "Password must be at least 6 characters"
    isSignUp && confirmPassword.isBlank() -> "Please confirm your password"
    isSignUp && password != confirmPassword -> "Passwords do not match"
    else -> null
}

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

    fun selectAuthMode(mode: AuthMode) {
        activeAuthMode = mode
        errorMessage = null
        magicLinkSent = false
        confirmPassword = ""
    }

    fun resetToNewEmail() {
        magicLinkSent = false
        email = ""
        password = ""
        confirmPassword = ""
    }

    fun submitEmailLink() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            errorMessage = "Please enter your email address"
            return
        }
        if (!isValidEmail(trimmedEmail)) {
            errorMessage = "Please enter a valid email address"
            return
        }
        focusManager.clearFocus()
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
        val isSignUp = activeAuthMode == AuthMode.SIGN_UP

        val validationError = validatePasswordInputs(trimmedEmail, password, isSignUp, confirmPassword)
        if (validationError != null) {
            errorMessage = validationError
            return
        }

        focusManager.clearFocus()
        isEmailLoading = true
        errorMessage = null

        scope.launch {
            val result = executePasswordAuth(trimmedEmail, password, isSignUp)
            isEmailLoading = false
            handlePasswordAuthResult(result, isSignUp)
        }
    }

    private suspend fun executePasswordAuth(
        email: String,
        password: String,
        isSignUp: Boolean,
    ): Result<*>? = if (isSignUp) {
        authRepository?.signUpWithEmailPassword(email, password)
    } else {
        authRepository?.signInWithEmailPassword(email, password)
    }

    private fun handlePasswordAuthResult(
        result: Result<*>?,
        isSignUp: Boolean,
    ) {
        if (result?.isSuccess == true) {
            val successMessage = if (isSignUp) "Account created!" else "Signed in!"
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
        } else {
            errorMessage = cleanAccountError(result?.exceptionOrNull()?.localizedMessage)
        }
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
                    Toast.makeText(context, "Signed in with Google!", Toast.LENGTH_SHORT).show()
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
        if (trimmedEmail.isBlank()) {
            errorMessage = "Enter your email address above to reset password"
            return
        }
        if (!isValidEmail(trimmedEmail)) {
            errorMessage = "Please enter a valid email address"
            return
        }
        focusManager.clearFocus()
        scope.launch {
            val result = authRepository?.sendPasswordReset(trimmedEmail)
            if (result?.isSuccess == true) {
                Toast.makeText(context, "Password reset email sent", Toast.LENGTH_SHORT).show()
            } else {
                errorMessage = result?.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email"
            }
        }
    }

    fun toEmailInputState() = EmailInputState(
        email = email,
        isSignUp = activeAuthMode == AuthMode.SIGN_UP,
        isLoading = isEmailLoading,
        errorMessage = errorMessage,
    )

    fun toEmailInputActions(actionButtonRequester: BringIntoViewRequester) = EmailInputActions(
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
            scope.launch { actionButtonRequester.bringIntoView() }
        },
    )

    fun toPasswordInputState() = PasswordInputState(
        email = email,
        password = password,
        confirmPassword = confirmPassword,
        passwordVisible = passwordVisible,
        confirmPasswordVisible = confirmPasswordVisible,
        isSignUp = activeAuthMode == AuthMode.SIGN_UP,
        isLoading = isEmailLoading,
        errorMessage = errorMessage,
    )

    fun toPasswordInputActions(
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
            scope.launch { actionButtonRequester.bringIntoView() }
        },
        onNextField = onNextField,
    )

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
                    state.password,
                    state.confirmPassword,
                    state.passwordVisible,
                    state.confirmPasswordVisible,
                    state.magicLinkSent,
                    state.errorMessage,
                )
            },
            restore = { list ->
                val activeAuthMode = (list.getOrNull(0) as? String)?.let {
                    runCatching { AuthMode.valueOf(it) }.getOrNull()
                } ?: AuthMode.SIGN_IN
                val usePasswordAuth = list.getOrNull(1) as? Boolean ?: false
                val email = (list.getOrNull(2) as? String).orEmpty()
                val password = (list.getOrNull(3) as? String).orEmpty()
                val confirmPassword = (list.getOrNull(4) as? String).orEmpty()
                val passwordVisible = list.getOrNull(5) as? Boolean ?: false
                val confirmPasswordVisible = list.getOrNull(6) as? Boolean ?: false
                val magicLinkSent = list.getOrNull(7) as? Boolean ?: false
                val errorMessage = list.getOrNull(8) as? String
                AccountAuthState(
                    authRepository = authRepository,
                    context = context,
                    activity = activity,
                    focusManager = focusManager,
                    scope = scope,
                    initialState = SavedAccountAuthState(
                        activeAuthMode = activeAuthMode,
                        usePasswordAuth = usePasswordAuth,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword,
                        passwordVisible = passwordVisible,
                        confirmPasswordVisible = confirmPasswordVisible,
                        magicLinkSent = magicLinkSent,
                        errorMessage = errorMessage,
                    ),
                )
            },
        )
    }
}

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
    return state
}

@Composable
internal fun AuthDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "or",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
internal fun AuthErrorBanner(message: String) {
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun AccountPrivacyCard(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Privacy & Data Protection",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Your account is used strictly to sync your library, queue, and playback history across devices. It is completely isolated and never linked to app usage or analytics data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Authentication is handled directly by Firebase (Google). Your email address is never stored on our servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}
