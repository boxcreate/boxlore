package cx.aswin.boxlore.feature.home.settings.pages

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AccountCircle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.AuthRepository
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold
import kotlinx.coroutines.launch

private const val GOOGLE_SERVER_CLIENT_ID =
    "74591511411-l1jc8fftg8u7hcv354ooi644omsuksei.apps.googleusercontent.com"

private enum class EmailAuthMethod {
    MAGIC_LINK,
    PASSWORD,
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
private fun SignedInContent(
    user: BoxLoreUser,
    onSignOut: () -> Unit,
    onDeleteAccountClick: () -> Unit,
) {
    SettingsGroup(title = "Your Profile") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
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

                Spacer(Modifier.height(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
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

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "ID: ${user.uid.take(16)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }

    SettingsGroup(title = "Cloud Synchronization") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
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
                        text = "Your subscriptions, playlists, and settings stay backed up and accessible across your devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SettingsGroup(title = "Session") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", fontWeight = GoogleSansWeight.medium)
            }
        }
    }

    SettingsGroup(title = "Danger Zone") {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(
                text = "Deleting your account permanently removes your cloud profile. Local data and downloads on this device will not be erased.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onDeleteAccountClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text("Delete Account Permanently", fontWeight = GoogleSansWeight.medium)
            }
        }
    }
}

@Composable
private fun SignedOutContent(
    authRepository: AuthRepository?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMethod by remember { mutableStateOf(EmailAuthMethod.MAGIC_LINK) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSignUp by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var magicLinkSent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Hero Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "boxlore Account",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = GoogleSansWeight.bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Sign in to keep your subscriptions, queue, and listening progress in sync across all your devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 1. Google 1-Tap Sign-In Surface
        val googleShape = MaterialTheme.shapes.large
        Surface(
            shape = googleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .expressiveClickable(shape = googleShape) {
                    if (isLoading) return@expressiveClickable
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(GOOGLE_SERVER_CLIENT_ID)
                                .setAutoSelectEnabled(false)
                                .build()

                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()

                            val result = credentialManager.getCredential(context = context, request = request)
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
                        } catch (e: GetCredentialException) {
                            errorMessage = e.localizedMessage ?: "Google sign-in cancelled or failed"
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Error during sign in"
                        } finally {
                            isLoading = false
                        }
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Continue with Google",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Divider with text
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Email Authentication Surface
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ConnectedOptionSelector(
                    options = listOf(
                        EmailAuthMethod.MAGIC_LINK to "Magic Link",
                        EmailAuthMethod.PASSWORD to "Password",
                    ),
                    selected = selectedMethod,
                    onSelect = {
                        selectedMethod = it
                        errorMessage = null
                    },
                )

                Spacer(Modifier.height(16.dp))

                if (selectedMethod == EmailAuthMethod.MAGIC_LINK) {
                    // Magic Link Form
                    Text(
                        text = "Passwordless login: Enter your email and we'll send a sign-in link. Tapping the link on this device logs you in instantly.",
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
                                if (email.isNotBlank() && !isLoading) {
                                    isLoading = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = authRepository?.sendMagicLink(email.trim())
                                        isLoading = false
                                        if (result?.isSuccess == true) {
                                            magicLinkSent = true
                                        } else {
                                            errorMessage = result?.exceptionOrNull()?.localizedMessage
                                                ?: "Failed to send magic link"
                                        }
                                    }
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (email.isBlank()) {
                                errorMessage = "Please enter your email"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                val result = authRepository?.sendMagicLink(email.trim())
                                isLoading = false
                                if (result?.isSuccess == true) {
                                    magicLinkSent = true
                                } else {
                                    errorMessage = result?.exceptionOrNull()?.localizedMessage
                                        ?: "Failed to send magic link"
                                }
                            }
                        },
                        enabled = !isLoading && email.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Send Magic Link", fontWeight = GoogleSansWeight.bold)
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
                                    text = "Magic link sent! Check your inbox on this device and tap the link to complete sign in.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = GoogleSansWeight.medium,
                                )
                            }
                        }
                    }
                } else {
                    // Password Form
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
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(10.dp))

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
                                    contentDescription = if (passwordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
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
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (!isSignUp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    if (email.isBlank()) {
                                        errorMessage = "Enter your email address above to reset password"
                                        return@TextButton
                                    }
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
                            ) {
                                Text("Forgot password?", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }

                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = "Please enter both email and password"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                val result = if (isSignUp) {
                                    authRepository?.signUpWithEmailPassword(email.trim(), password)
                                } else {
                                    authRepository?.signInWithEmailPassword(email.trim(), password)
                                }
                                isLoading = false
                                if (result?.isSuccess == true) {
                                    Toast.makeText(
                                        context,
                                        if (isSignUp) "Account created!" else "Signed in!",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    errorMessage = result?.exceptionOrNull()?.localizedMessage
                                        ?: "Authentication failed"
                                }
                            }
                        },
                        enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = if (isSignUp) "Create Account" else "Sign In",
                                fontWeight = GoogleSansWeight.bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isSignUp) "Already have an account?" else "Don't have an account?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = {
                            isSignUp = !isSignUp
                            errorMessage = null
                        }) {
                            Text(
                                text = if (isSignUp) "Sign In" else "Create one",
                                fontWeight = GoogleSansWeight.bold,
                                style = MaterialTheme.typography.bodySmall,
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
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}
