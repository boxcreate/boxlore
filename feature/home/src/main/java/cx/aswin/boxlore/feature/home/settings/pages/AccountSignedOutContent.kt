package cx.aswin.boxlore.feature.home.settings.pages

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.network.AuthRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val GOOGLE_SERVER_CLIENT_ID =
    "74591511411-l1jc8fftg8u7hcv354ooi644omsuksei.apps.googleusercontent.com"

private const val GOOGLE_SIGN_IN_TIMEOUT_MS = 15_000L

private enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SignedOutContent(
    authRepository: AuthRepository?,
    scrollState: ScrollState? = null,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val actionButtonRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(density)

    var activeAuthMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var usePasswordAuth by remember { mutableStateOf(false) }
    var isAnyInputFocused by remember { mutableStateOf(false) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    val isAnyLoading = isGoogleLoading || isEmailLoading

    var magicLinkSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imeBottom, isAnyInputFocused) {
        if (imeBottom > 0 && isAnyInputFocused) {
            actionButtonRequester.bringIntoView()
        }
    }

    fun cleanError(raw: String?, isSignUp: Boolean): String {
        if (raw == null) return "Authentication failed"
        val lower = raw.lowercase()
        return when {
            !isSignUp && (lower.contains("no user") || lower.contains("invalid-credential") || lower.contains("user-not-found")) ->
                "Incorrect email or password. Don't have an account? Switch to 'Sign Up'."
            isSignUp && (lower.contains("already in use") || lower.contains("email-already-in-use")) ->
                "An account already exists with this email. Switch to 'Sign In'."
            lower.contains("weak-password") ->
                "Password must be at least 6 characters."
            lower.contains("invalid-email") ->
                "Please enter a valid email address."
            else -> raw
        }
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
                errorMessage = cleanError(result?.exceptionOrNull()?.localizedMessage, isSignUp = activeAuthMode == AuthMode.SIGN_UP)
            }
        }
    }

    fun submitPasswordAuth() {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        val isSignUp = activeAuthMode == AuthMode.SIGN_UP

        if (trimmedEmail.isBlank()) {
            errorMessage = "Please enter your email address"
            return
        }
        if (trimmedPassword.isBlank()) {
            errorMessage = "Please enter your password"
            return
        }
        if (isSignUp && trimmedPassword.length < 6) {
            errorMessage = "Password must be at least 6 characters"
            return
        }

        focusManager.clearFocus()
        isEmailLoading = true
        errorMessage = null

        scope.launch {
            val result = if (isSignUp) {
                authRepository?.signUpWithEmailPassword(trimmedEmail, trimmedPassword)
            } else {
                authRepository?.signInWithEmailPassword(trimmedEmail, trimmedPassword)
            }
            isEmailLoading = false
            if (result?.isSuccess == true) {
                Toast.makeText(
                    context,
                    if (isSignUp) "Account created!" else "Signed in!",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                errorMessage = cleanError(result?.exceptionOrNull()?.localizedMessage, isSignUp = isSignUp)
            }
        }
    }

    fun openGmailApp() {
        try {
            val gmailIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (gmailIntent != null) {
                context.startActivity(gmailIntent)
            } else {
                val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (emailIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(emailIntent)
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")))
                }
            }
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com")))
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open email app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. Ultra-Light Header (Single-line explanation, zero heavy icons/para)
    Text(
        text = "Sign in to sync your library, queue, and playback across devices.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )

    // 2. Continue with Google Button
    val googleShape = MaterialTheme.shapes.extraLarge
    Card(
        shape = googleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .expressiveClickable(
                shape = googleShape,
                enabled = !isAnyLoading,
            ) {
                if (isAnyLoading) return@expressiveClickable
                focusManager.clearFocus()
                isGoogleLoading = true
                errorMessage = null
                scope.launch {
                    try {
                        withTimeout(GOOGLE_SIGN_IN_TIMEOUT_MS) {
                            val credentialManager = CredentialManager.create(activity ?: context)
                            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                                serverClientId = GOOGLE_SERVER_CLIENT_ID,
                            ).build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInWithGoogleOption)
                                .build()

                            val result = credentialManager.getCredential(
                                context = activity ?: context,
                                request = request,
                            )
                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                val signInResult = authRepository?.signInWithGoogle(googleIdToken)
                                if (signInResult?.isSuccess == true) {
                                    Toast.makeText(context, "Signed in with Google!", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = signInResult?.exceptionOrNull()?.localizedMessage
                                        ?: "Google sign-in failed"
                                }
                            } else {
                                errorMessage = "Unexpected credential received"
                            }
                        }
                    } catch (_: GetCredentialCancellationException) {
                        // User cancelled Google picker dialog.
                    } catch (e: GetCredentialException) {
                        errorMessage = e.localizedMessage ?: "Google sign-in error"
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Google sign-in timed out"
                    } finally {
                        isGoogleLoading = false
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isGoogleLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Connecting to Google...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = GoogleSansWeight.medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter = painterResource(cx.aswin.boxlore.core.designsystem.R.drawable.ic_google_logo),
                        contentDescription = "Google",
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = GoogleSansWeight.semiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }

    // 3. Divider
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

    // 4. Primary Auth Card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            // Prominent 2-option selector: Sign In | Sign Up (50% width each, zero ellipsis!)
            ConnectedOptionSelector(
                options = listOf(
                    AuthMode.SIGN_IN to "Sign In",
                    AuthMode.SIGN_UP to "Sign Up",
                ),
                selected = activeAuthMode,
                onSelect = { mode ->
                    activeAuthMode = mode
                    errorMessage = null
                    magicLinkSent = false
                },
            )

            Spacer(Modifier.height(18.dp))

            if (!usePasswordAuth) {
                // Email Link Flow (DEFAULT for both Sign In and Sign Up)
                if (magicLinkSent) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (activeAuthMode == AuthMode.SIGN_UP) "Sign-up link sent!" else "Sign-in link sent!",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = GoogleSansWeight.bold,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "We sent an instant link to $email. Open the email on this device and tap the link to complete.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )

                            Spacer(Modifier.height(12.dp))

                            // Prominent Spam Alert Callout
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Important: The email will 99% land in your Spam or Junk folder! Please check there.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = GoogleSansWeight.bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Open Gmail Button
                            Button(
                                onClick = { openGmailApp() },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Open Gmail",
                                    fontWeight = GoogleSansWeight.bold,
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = {
                                        magicLinkSent = false
                                        email = ""
                                    },
                                ) {
                                    Text(
                                        text = "Use different email",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                TextButton(
                                    onClick = { submitEmailLink() },
                                    enabled = !isAnyLoading,
                                ) {
                                    Text(
                                        text = "Resend link",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = GoogleSansWeight.bold,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = if (activeAuthMode == AuthMode.SIGN_UP) {
                            "Enter your email to create an account. We'll send an instant verification link."
                        } else {
                            "Enter your email to receive a passwordless sign-in link."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        label = { Text("Email address") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { submitEmailLink() },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isAnyInputFocused = true
                                    scope.launch { actionButtonRequester.bringIntoView() }
                                }
                            },
                    )

                    if (errorMessage != null) {
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
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Primary Button: Send Sign-In Link or Send Sign-Up Link
                    Button(
                        onClick = { submitEmailLink() },
                        enabled = !isAnyLoading && email.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .bringIntoViewRequester(actionButtonRequester),
                    ) {
                        if (isEmailLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = if (activeAuthMode == AuthMode.SIGN_UP) "Send Sign-Up Link" else "Send Sign-In Link",
                                fontWeight = GoogleSansWeight.bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Prominent Outlined Option: Use Password Instead
                    OutlinedButton(
                        onClick = {
                            usePasswordAuth = true
                            errorMessage = null
                        },
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (activeAuthMode == AuthMode.SIGN_UP) "Sign up with password instead" else "Sign in with password instead",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = GoogleSansWeight.medium,
                        )
                    }
                }
            } else {
                // Password Flow (Prominent option for both Sign In and Sign Up)
                Text(
                    text = if (activeAuthMode == AuthMode.SIGN_UP) {
                        "Choose a password (minimum 6 characters) to create your account."
                    } else {
                        "Enter your email and password to access your account."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        errorMessage = null
                    },
                    label = { Text("Email address") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                isAnyInputFocused = true
                                scope.launch { actionButtonRequester.bringIntoView() }
                            }
                        },
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { submitPasswordAuth() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                isAnyInputFocused = true
                                scope.launch { actionButtonRequester.bringIntoView() }
                            }
                        },
                )

                if (activeAuthMode == AuthMode.SIGN_IN) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                val trimmedEmail = email.trim()
                                if (trimmedEmail.isBlank()) {
                                    errorMessage = "Enter your email address above to reset password"
                                    return@TextButton
                                }
                                focusManager.clearFocus()
                                scope.launch {
                                    val result = authRepository?.sendPasswordReset(trimmedEmail)
                                    if (result?.isSuccess == true) {
                                        Toast.makeText(
                                            context,
                                            "Password reset email sent",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        errorMessage = result?.exceptionOrNull()?.localizedMessage
                                            ?: "Failed to send reset email"
                                    }
                                }
                            },
                            enabled = !isAnyLoading,
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, start = 4.dp, bottom = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "Must be at least 6 characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (errorMessage != null) {
                    Spacer(Modifier.height(6.dp))
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
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Primary Button: Sign In or Sign Up
                Button(
                    onClick = { submitPasswordAuth() },
                    enabled = !isAnyLoading && email.isNotBlank() && password.isNotBlank(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .bringIntoViewRequester(actionButtonRequester),
                ) {
                    if (isEmailLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = if (activeAuthMode == AuthMode.SIGN_UP) "Sign Up" else "Sign In",
                            fontWeight = GoogleSansWeight.bold,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Prominent Outlined Option: Email me a link instead
                OutlinedButton(
                    onClick = {
                        usePasswordAuth = false
                        errorMessage = null
                    },
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Email,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Email me a link instead",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = GoogleSansWeight.medium,
                    )
                }
            }
        }
    }

    // 5. Privacy & Data Callout (At the bottom of the page)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "This account is used purely for cloud sync identification and is never linked to the app usage (tracking) data collected. Your email is only logged by Firebase (Google) for auth verification and is not stored in our servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
