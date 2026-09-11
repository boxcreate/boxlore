package cx.aswin.boxlore.feature.home.settings.pages

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import cx.aswin.boxlore.feature.home.settings.components.SettingsContent
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val GOOGLE_SERVER_CLIENT_ID =
    "74591511411-l1jc8fftg8u7hcv354ooi644omsuksei.apps.googleusercontent.com"

private const val GOOGLE_SIGN_IN_TIMEOUT_MS = 15_000L

private enum class EmailAuthMethod {
    MAGIC_LINK,
    PASSWORD,
}

private enum class PasswordMode {
    SIGN_IN,
    SIGN_UP,
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun ColumnScope.SignedOutContent(
    authRepository: AuthRepository?,
    scrollState: ScrollState,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var emailAuthMethod by remember { mutableStateOf(EmailAuthMethod.MAGIC_LINK) }
    var passwordMode by remember { mutableStateOf(PasswordMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    val isAnyLoading = isGoogleLoading || isEmailLoading

    var magicLinkSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 1. Compact Hero Card
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Cloud Sync & Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sign in to keep your subscriptions, queue, and playback progress synchronized across all your devices.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

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
                        // User cancelled or dismissed the Google account picker dialog.
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Text(
            text = "or continue with email",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }

    // 4. Email Authentication Card
    SettingsGroup(
        title = "Email Sign-In",
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options = listOf(
                    EmailAuthMethod.MAGIC_LINK to "Magic Link",
                    EmailAuthMethod.PASSWORD to "Password",
                ),
                selected = emailAuthMethod,
                onSelect = {
                    emailAuthMethod = it
                    errorMessage = null
                },
            )

            Spacer(Modifier.height(16.dp))

            when (emailAuthMethod) {
                EmailAuthMethod.MAGIC_LINK -> {
                    if (magicLinkSent) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = "Sign-in link sent!",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = GoogleSansWeight.bold,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "We sent a link to $email. Open the email on this device and tap the link to sign in automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
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
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                isEmailLoading = true
                                                errorMessage = null
                                                val result = authRepository?.sendMagicLink(email.trim())
                                                isEmailLoading = false
                                                if (result?.isSuccess == true) {
                                                    Toast.makeText(context, "Link resent!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    errorMessage = result?.exceptionOrNull()?.localizedMessage
                                                        ?: "Failed to resend link"
                                                }
                                            }
                                        },
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
                            text = "Enter your email to receive a passwordless sign-in link.",
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
                                onDone = {
                                    focusManager.clearFocus()
                                    if (!isAnyLoading && email.isNotBlank()) {
                                        isEmailLoading = true
                                        errorMessage = null
                                        scope.launch {
                                            val result = authRepository?.sendMagicLink(email.trim())
                                            isEmailLoading = false
                                            if (result?.isSuccess == true) {
                                                magicLinkSent = true
                                            } else {
                                                errorMessage = result?.exceptionOrNull()?.localizedMessage
                                                    ?: "Failed to send link"
                                            }
                                        }
                                    }
                                },
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(150)
                                            scrollState.animateScrollTo(scrollState.maxValue)
                                        }
                                    }
                                },
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (email.isBlank()) {
                                    errorMessage = "Please enter your email address"
                                    return@Button
                                }
                                focusManager.clearFocus()
                                isEmailLoading = true
                                errorMessage = null
                                scope.launch {
                                    val result = authRepository?.sendMagicLink(email.trim())
                                    isEmailLoading = false
                                    if (result?.isSuccess == true) {
                                        magicLinkSent = true
                                    } else {
                                        errorMessage = result?.exceptionOrNull()?.localizedMessage
                                            ?: "Failed to send link"
                                    }
                                }
                            },
                            enabled = !isAnyLoading && email.isNotBlank(),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            if (isEmailLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(
                                    text = "Send Sign-In Link",
                                    fontWeight = GoogleSansWeight.bold,
                                )
                            }
                        }
                    }
                }

                EmailAuthMethod.PASSWORD -> {
                    ConnectedOptionSelector(
                        options = listOf(
                            PasswordMode.SIGN_IN to "Sign In",
                            PasswordMode.SIGN_UP to "Sign Up",
                        ),
                        selected = passwordMode,
                        onSelect = {
                            passwordMode = it
                            errorMessage = null
                        },
                    )

                    Spacer(Modifier.height(14.dp))

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
                            onNext = {
                                focusManager.moveFocus(FocusDirection.Down)
                                scope.launch {
                                    delay(50)
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        delay(150)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
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
                            onDone = {
                                focusManager.clearFocus()
                                if (!isAnyLoading && email.isNotBlank() && password.isNotBlank()) {
                                    isEmailLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = if (passwordMode == PasswordMode.SIGN_UP) {
                                            authRepository?.signUpWithEmailPassword(email.trim(), password)
                                        } else {
                                            authRepository?.signInWithEmailPassword(email.trim(), password)
                                        }
                                        isEmailLoading = false
                                        if (result?.isSuccess == true) {
                                            Toast.makeText(
                                                context,
                                                if (passwordMode == PasswordMode.SIGN_UP) "Account created!" else "Signed in!",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        } else {
                                            val rawError = result?.exceptionOrNull()?.localizedMessage
                                                ?: "Authentication failed"
                                            if (passwordMode == PasswordMode.SIGN_IN &&
                                                (
                                                    rawError.contains("no user", ignoreCase = true) ||
                                                    rawError.contains("invalid-credential", ignoreCase = true) ||
                                                    rawError.contains("user-not-found", ignoreCase = true)
                                                )
                                            ) {
                                                errorMessage = "Incorrect email or password. If you don't have an account yet, switch to 'Sign Up'."
                                            } else if (passwordMode == PasswordMode.SIGN_UP &&
                                                (
                                                    rawError.contains("already in use", ignoreCase = true) ||
                                                    rawError.contains("email-already-in-use", ignoreCase = true)
                                                )
                                            ) {
                                                errorMessage = "An account already exists with this email. Switch to 'Sign In'."
                                            } else {
                                                errorMessage = rawError
                                            }
                                        }
                                    }
                                }
                            },
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    scope.launch {
                                        delay(150)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            },
                    )

                    if (passwordMode == PasswordMode.SIGN_IN) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    passwordMode = PasswordMode.SIGN_UP
                                    errorMessage = null
                                },
                                enabled = !isAnyLoading,
                            ) {
                                Text(
                                    text = "Need an account? Sign up",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = GoogleSansWeight.medium,
                                )
                            }

                            TextButton(
                                onClick = {
                                    if (email.isBlank()) {
                                        errorMessage = "Enter your email address above to reset password"
                                        return@TextButton
                                    }
                                    focusManager.clearFocus()
                                    scope.launch {
                                        val result = authRepository?.sendPasswordReset(email.trim())
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = {
                                    passwordMode = PasswordMode.SIGN_IN
                                    errorMessage = null
                                },
                                enabled = !isAnyLoading,
                            ) {
                                Text(
                                    text = "Already have an account? Sign in",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = GoogleSansWeight.medium,
                                )
                            }

                            Text(
                                text = "Min. 6 characters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both email and password"
                                return@Button
                            }
                            if (passwordMode == PasswordMode.SIGN_UP && password.length < 6) {
                                errorMessage = "Password must be at least 6 characters"
                                return@Button
                            }
                            focusManager.clearFocus()
                            isEmailLoading = true
                            errorMessage = null
                            scope.launch {
                                val result = if (passwordMode == PasswordMode.SIGN_UP) {
                                    authRepository?.signUpWithEmailPassword(email.trim(), password)
                                } else {
                                    authRepository?.signInWithEmailPassword(email.trim(), password)
                                }
                                isEmailLoading = false
                                if (result?.isSuccess == true) {
                                    Toast.makeText(
                                        context,
                                        if (passwordMode == PasswordMode.SIGN_UP) "Account created!" else "Signed in!",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    val rawError = result?.exceptionOrNull()?.localizedMessage
                                        ?: "Authentication failed"
                                    if (passwordMode == PasswordMode.SIGN_IN &&
                                        (
                                            rawError.contains("no user", ignoreCase = true) ||
                                            rawError.contains("invalid-credential", ignoreCase = true) ||
                                            rawError.contains("user-not-found", ignoreCase = true)
                                        )
                                    ) {
                                        errorMessage = "Incorrect email or password. If you don't have an account yet, switch to 'Sign Up'."
                                    } else if (passwordMode == PasswordMode.SIGN_UP &&
                                        (
                                            rawError.contains("already in use", ignoreCase = true) ||
                                            rawError.contains("email-already-in-use", ignoreCase = true)
                                        )
                                    ) {
                                        errorMessage = "An account already exists with this email. Switch to 'Sign In'."
                                    } else {
                                        errorMessage = rawError
                                    }
                                }
                            }
                        },
                        enabled = !isAnyLoading && email.isNotBlank() && password.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        if (isEmailLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = if (passwordMode == PasswordMode.SIGN_UP) "Sign Up" else "Sign In",
                                fontWeight = GoogleSansWeight.bold,
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
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
        }
    }
}
