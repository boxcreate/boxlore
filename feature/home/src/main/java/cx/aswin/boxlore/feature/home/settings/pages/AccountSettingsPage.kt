package cx.aswin.boxlore.feature.home.settings.pages

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
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
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.AuthRepository
import cx.aswin.boxlore.feature.home.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsContent
import cx.aswin.boxlore.feature.home.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private const val GOOGLE_SERVER_CLIENT_ID =
    "74591511411-l1jc8fftg8u7hcv354ooi644omsuksei.apps.googleusercontent.com"

private const val GOOGLE_SIGN_IN_TIMEOUT_MS = 15_000L

private enum class AuthMode {
    SIGN_IN,
    CREATE_ACCOUNT,
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal fun AccountSettingsPage(
    authRepository: AuthRepository?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by (
        authRepository?.currentUser?.collectAsState()
            ?: remember { mutableStateOf<BoxLoreUser?>(null) }
    )

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "Account",
        onBack = onBack,
    ) {
        val user = currentUser
        if (user != null) {
            SignedInContent(
                user = user,
                onSignOut = {
                    authRepository?.signOut()
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                },
                onDeleteAccountClick = { showDeleteConfirmation = true },
            )
        } else {
            SignedOutContent(
                authRepository = authRepository,
            )
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Account", fontWeight = GoogleSansWeight.bold) },
            text = {
                Text(
                    "Are you sure you want to delete your boxlore account? " +
                        "This permanently removes your cloud profile and cross-device sync data. " +
                        "Your local downloads and podcast catalog on this device will not be erased.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        scope.launch {
                            val result = authRepository?.deleteAccount()
                            if (result?.isSuccess == true) {
                                Toast.makeText(context, "Account deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    result?.exceptionOrNull()?.localizedMessage ?: "Failed to delete account",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.SignedInContent(
    user: BoxLoreUser,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(68.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = (user.displayName ?: user.email)?.take(1)?.uppercase() ?: "B"
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = user.displayName ?: user.email ?: "boxlore listener",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                textAlign = TextAlign.Center,
            )

            val email = user.email
            if (user.displayName != null && email != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Cloud sync active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = GoogleSansWeight.medium,
                    )
                }
            }
        }
    }

    SettingsGroup(
        title = "Cloud Synchronization",
        footer = "Your subscriptions, queue, and playback progress stay backed up and synchronized across your devices.",
    ) {
        SettingsContent {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Library Sync Ready",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = GoogleSansWeight.bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Connected and synchronizing changes across all your devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SettingsGroup(title = "Account Management") {
        SettingsActionRow(
            title = "Sign Out",
            supportingText = "Disconnect this device from your cloud account",
            icon = Icons.AutoMirrored.Rounded.Logout,
            onClick = onSignOut,
        )
        SettingsDivider()
        SettingsActionRow(
            title = "Delete Account",
            supportingText = "Permanently remove your cloud profile and cross-device data",
            icon = Icons.Rounded.DeleteForever,
            destructive = true,
            onClick = onDeleteAccountClick,
        )
    }
}

@Composable
private fun ColumnScope.SignedOutContent(
    authRepository: AuthRepository?,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isGoogleLoading by remember { mutableStateOf(false) }
    var isEmailLoading by remember { mutableStateOf(false) }
    val isAnyLoading = isGoogleLoading || isEmailLoading

    var magicLinkSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 1. Hero Card
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Cloud Sync & Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to keep your subscriptions, queue, and playback progress synchronized across all your devices.",
                style = MaterialTheme.typography.bodyMedium,
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
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val signInResult = authRepository?.signInWithGoogle(googleIdTokenCredential.idToken)
                                if (signInResult?.isSuccess == true) {
                                    Toast.makeText(context, "Signed in with Google", Toast.LENGTH_SHORT).show()
                                } else {
                                    errorMessage = signInResult?.exceptionOrNull()?.localizedMessage
                                        ?: "Google sign-in failed"
                                }
                            } else {
                                errorMessage = "Unexpected credential received"
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        errorMessage = "Sign-in timed out. Please try again."
                    } catch (_: GetCredentialCancellationException) {
                        // User cancelled Google chooser; not an error.
                    } catch (e: GetCredentialException) {
                        errorMessage = e.localizedMessage ?: "Google sign-in failed"
                    } catch (e: Exception) {
                        errorMessage = e.localizedMessage ?: "Error during sign in"
                    } finally {
                        isGoogleLoading = false
                    }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
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
                    GoogleGLogo(modifier = Modifier.size(20.dp))
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
        title = if (authMode == AuthMode.SIGN_IN) "Sign In with Email" else "Create boxlore Account",
        footer = if (authMode == AuthMode.SIGN_IN) {
            "Sign in with your email and password, or request a passwordless magic link."
        } else {
            "Create a new boxlore account to back up your podcast library to the cloud."
        },
    ) {
        SettingsContent {
            ConnectedOptionSelector(
                options = listOf(
                    AuthMode.SIGN_IN to "Sign In",
                    AuthMode.CREATE_ACCOUNT to "Create Account",
                ),
                selected = authMode,
                onSelect = {
                    authMode = it
                    errorMessage = null
                    magicLinkSent = false
                },
            )

            Spacer(Modifier.height(16.dp))

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
                modifier = Modifier.fillMaxWidth(),
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
                                val result = if (authMode == AuthMode.CREATE_ACCOUNT) {
                                    authRepository?.signUpWithEmailPassword(email.trim(), password)
                                } else {
                                    authRepository?.signInWithEmailPassword(email.trim(), password)
                                }
                                isEmailLoading = false
                                if (result?.isSuccess == true) {
                                    Toast.makeText(
                                        context,
                                        if (authMode == AuthMode.CREATE_ACCOUNT) "Account created!" else "Signed in!",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    errorMessage = result?.exceptionOrNull()?.localizedMessage
                                        ?: "Authentication failed"
                                }
                            }
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            if (authMode == AuthMode.SIGN_IN) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (email.isBlank()) {
                                errorMessage = "Enter your email address above to receive a magic link"
                                return@TextButton
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
                                        ?: "Failed to send magic link"
                                }
                            }
                        },
                        enabled = !isAnyLoading,
                    ) {
                        Text(
                            text = "Email sign-in link",
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
                Spacer(Modifier.height(14.dp))
            }

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both email and password"
                        return@Button
                    }
                    focusManager.clearFocus()
                    isEmailLoading = true
                    errorMessage = null
                    scope.launch {
                        val result = if (authMode == AuthMode.CREATE_ACCOUNT) {
                            authRepository?.signUpWithEmailPassword(email.trim(), password)
                        } else {
                            authRepository?.signInWithEmailPassword(email.trim(), password)
                        }
                        isEmailLoading = false
                        if (result?.isSuccess == true) {
                            Toast.makeText(
                                context,
                                if (authMode == AuthMode.CREATE_ACCOUNT) "Account created!" else "Signed in!",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            errorMessage = result?.exceptionOrNull()?.localizedMessage
                                ?: "Authentication failed"
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
                        text = if (authMode == AuthMode.CREATE_ACCOUNT) "Create Account" else "Sign In",
                        fontWeight = GoogleSansWeight.bold,
                    )
                }
            }

            AnimatedVisibility(
                visible = magicLinkSent,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Magic sign-in link sent! Check your inbox on this device and tap the link to complete sign in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = GoogleSansWeight.medium,
                        )
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

@Composable
private fun GoogleGLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val size = size.minDimension
        val strokeWidth = size * 0.22f
        val center = Offset(size / 2f, size / 2f)
        val radius = (size - strokeWidth) / 2f

        // Red top arc
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 190f,
            sweepAngle = 135f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size - strokeWidth, size - strokeWidth),
        )
        // Yellow arc
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 130f,
            sweepAngle = 65f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size - strokeWidth, size - strokeWidth),
        )
        // Green bottom arc
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 35f,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size - strokeWidth, size - strokeWidth),
        )
        // Blue right arc
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = -25f,
            sweepAngle = 65f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(size - strokeWidth, size - strokeWidth),
        )
        // Blue horizontal crossbar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(center.x - size * 0.05f, center.y),
            end = Offset(center.x + radius + strokeWidth / 2f, center.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}
