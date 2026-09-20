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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
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

internal fun cleanAccountError(raw: String?): String {
    if (raw == null) return "An unexpected error occurred"
    return when {
        raw.contains("user-not-found", ignoreCase = true) || raw.contains("no user", ignoreCase = true) ->
            "No account found with this email. Try signing up instead."
        raw.contains("wrong-password", ignoreCase = true) || raw.contains("invalid-credential", ignoreCase = true) ->
            "Incorrect password or credentials. Please try again."
        raw.contains("email-already-in-use", ignoreCase = true) || raw.contains("already registered", ignoreCase = true) ->
            "This email is already registered. Try signing in instead."
        raw.contains("weak-password", ignoreCase = true) ->
            "Password is too weak. Please use at least 6 characters."
        raw.contains("invalid-email", ignoreCase = true) ->
            "Please enter a valid email address."
        raw.contains("network", ignoreCase = true) ->
            "Network error. Check your connection and try again."
        else -> raw
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
    val passwordVisible: Boolean,
    val isSignUp: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class PasswordInputActions(
    val onEmailChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onTogglePasswordVisible: () -> Unit,
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
): String? = when {
    email.isBlank() -> "Please enter your email address"
    password.isBlank() -> "Please enter your password"
    isSignUp && password.length < 6 -> "Password must be at least 6 characters"
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

internal class AccountAuthState(
    val authRepository: AuthRepository?,
    val context: Context,
    val activity: Activity?,
    val focusManager: FocusManager,
    val scope: CoroutineScope,
) {
    var activeAuthMode by mutableStateOf(AuthMode.SIGN_IN)
    var usePasswordAuth by mutableStateOf(false)
    var isAnyInputFocused by mutableStateOf(false)

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)

    var isGoogleLoading by mutableStateOf(false)
    var isEmailLoading by mutableStateOf(false)
    val isAnyLoading: Boolean get() = isGoogleLoading || isEmailLoading

    var magicLinkSent by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun selectAuthMode(mode: AuthMode) {
        activeAuthMode = mode
        errorMessage = null
        magicLinkSent = false
    }

    fun resetToNewEmail() {
        magicLinkSent = false
        email = ""
    }

    fun submitEmailLink() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            errorMessage = "Please enter your email address"
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
        val trimmedPassword = password.trim()
        val isSignUp = activeAuthMode == AuthMode.SIGN_UP

        val validationError = validatePasswordInputs(trimmedEmail, trimmedPassword, isSignUp)
        if (validationError != null) {
            errorMessage = validationError
            return
        }

        focusManager.clearFocus()
        isEmailLoading = true
        errorMessage = null

        scope.launch {
            val result = executePasswordAuth(trimmedEmail, trimmedPassword, isSignUp)
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
        passwordVisible = passwordVisible,
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
        onTogglePasswordVisible = { passwordVisible = !passwordVisible },
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
