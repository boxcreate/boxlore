package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.auth.AuthRepository
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SignedOutContent(
    authRepository: AuthRepository?,
    state: AccountAuthState = rememberAccountAuthState(
        authRepository = authRepository,
    ),
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val actionButtonRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(state.isAwaitingVerification) {
        if (state.isAwaitingVerification) {
            focusManager.clearFocus()
            state.isAnyInputFocused = false
        }
    }

    LaunchedEffect(imeBottom, state.isAnyInputFocused) {
        if (imeBottom > 0 && state.isAnyInputFocused && !state.isAwaitingVerification) {
            runCatching { actionButtonRequester.bringIntoView() }
        }
    }

    AnimatedVisibility(
        visible = !state.isAwaitingVerification,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Ultra-Light Header
            Text(
                text = "Sign in to sync your library, queue, and playback across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )

            Spacer(Modifier.height(10.dp))

            // 2. Continue with Google Button
            GoogleSignInButton(
                isLoading = state.isGoogleLoading,
                enabled = !state.isAnyLoading,
                onClick = state::handleGoogleSignIn,
            )

            Spacer(Modifier.height(8.dp))

            PrivacyPolicyNotice(
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Spacer(Modifier.height(6.dp))

            // 3. Divider
            AuthDivider()

            Spacer(Modifier.height(6.dp))
        }
    }

    // 4. Stable Primary Auth Card Container
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
            AnimatedVisibility(
                visible = !state.isAwaitingVerification,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ConnectedOptionSelector(
                        options = listOf(
                            AuthMode.SIGN_IN to "Sign In",
                            AuthMode.SIGN_UP to "Sign Up",
                        ),
                        selected = state.activeAuthMode,
                        onSelect = state::selectAuthMode,
                    )

                    Spacer(Modifier.height(18.dp))
                }
            }

            PrimaryAuthCardBody(
                state = state,
                actionButtonRequester = actionButtonRequester,
                focusManager = focusManager,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // 5. Privacy & Data Callout
    AccountPrivacyCard()
}

@Composable
private fun PrimaryAuthCardBody(
    state: AccountAuthState,
    actionButtonRequester: BringIntoViewRequester,
    focusManager: FocusManager,
) {
    when {
        state.isAwaitingVerification -> {
            EmailVerificationPendingSection(
                state = state,
            )
        }
        state.magicLinkSent && !state.usePasswordAuth -> {
            EmailLinkSentSection(
                email = state.email,
                isSignUp = false,
                isAnyLoading = state.isAnyLoading,
                onOpenEmail = { openGmailOrEmailApp(state.context, state.email) },
                onUseDifferentEmail = state::resetToNewEmail,
                onResendLink = state::submitEmailLink,
            )
        }
        !state.usePasswordAuth && state.activeAuthMode == AuthMode.SIGN_IN -> {
            EmailLinkInputSection(
                state = state.toEmailInputState(),
                actions = state.toEmailInputActions(actionButtonRequester),
                actionButtonRequester = actionButtonRequester,
            )
        }
        else -> {
            PasswordAuthSection(
                state = state.toPasswordInputState(),
                actions = state.toPasswordInputActions(
                    actionButtonRequester = actionButtonRequester,
                    onNextField = { focusManager.moveFocus(FocusDirection.Down) },
                ),
                actionButtonRequester = actionButtonRequester,
            )
        }
    }
}

@Composable
private fun GoogleSignInButton(
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
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
}

@Composable
private fun EmailLinkSentHeader(isSignUp: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSignUp) "Sign-up link sent!" else "Sign-in link sent!",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = GoogleSansWeight.bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Tap the link on this device to finish",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmailLinkSentAddressCard(email: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sent to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmailLinkSentSpamCard() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Check your Spam or Junk folder",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Important: The email will 99% land in your Spam or Junk folder! Please check there if you don't see it in your inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun EmailLinkSentFooter(
    isAnyLoading: Boolean,
    onUseDifferentEmail: () -> Unit,
    onResendLink: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onUseDifferentEmail) {
            Text(
                text = "Use different email",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onResendLink,
            enabled = !isAnyLoading,
        ) {
            Text(
                text = "Resend link",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmailLinkSentSection(
    email: String,
    isSignUp: Boolean,
    isAnyLoading: Boolean,
    onOpenEmail: () -> Unit,
    onUseDifferentEmail: () -> Unit,
    onResendLink: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        EmailLinkSentHeader(isSignUp = isSignUp)

        Spacer(Modifier.height(14.dp))

        EmailLinkSentAddressCard(email = email)

        Spacer(Modifier.height(12.dp))

        EmailLinkSentSpamCard()

        Spacer(Modifier.height(16.dp))

        val isGmail = email.trim().endsWith("@gmail.com", ignoreCase = true) ||
            email.trim().endsWith("@googlemail.com", ignoreCase = true)
        Button(
            onClick = onOpenEmail,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isGmail) "Open Gmail" else "Open Email App",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.bold,
            )
        }

        Spacer(Modifier.height(6.dp))

        EmailLinkSentFooter(
            isAnyLoading = isAnyLoading,
            onUseDifferentEmail = onUseDifferentEmail,
            onResendLink = onResendLink,
        )
    }
}

@Composable
private fun EmailLinkInputSection(
    state: EmailInputState,
    actions: EmailInputActions,
    actionButtonRequester: BringIntoViewRequester,
) {
    Text(
        text = if (state.isSignUp) {
            "Enter your email to create an account. We'll send an instant verification link."
        } else {
            "Enter your email to receive a passwordless sign-in link."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        label = { Text("Email address") },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = { actions.onSendLink() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    actions.onInputFocused()
                }
            },
    )

    if (state.errorMessage != null) {
        AuthErrorBanner(message = state.errorMessage)
    }

    Spacer(Modifier.height(14.dp))

    PrivacyPolicyNotice()

    Spacer(Modifier.height(12.dp))

    Button(
        onClick = actions.onSendLink,
        enabled = !state.isLoading && state.email.isNotBlank(),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .bringIntoViewRequester(actionButtonRequester),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = if (state.isSignUp) "Send Sign-Up Link" else "Send Sign-In Link",
                fontWeight = GoogleSansWeight.bold,
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    OutlinedButton(
        onClick = actions.onSwitchToPassword,
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
            text = if (state.isSignUp) "Sign up with password instead" else "Sign in with password instead",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = GoogleSansWeight.medium,
        )
    }
}

private data class PasswordFieldState(
    val value: String,
    val isVisible: Boolean,
    val onValueChange: (String) -> Unit,
    val onToggleVisible: () -> Unit,
)

@Composable
private fun PasswordTextField(
    state: PasswordFieldState,
    onInputFocused: () -> Unit,
    label: String = "Password",
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = state.onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = state.onToggleVisible) {
                Icon(
                    imageVector = if (state.isVisible) {
                        Icons.Rounded.VisibilityOff
                    } else {
                        Icons.Rounded.Visibility
                    },
                    contentDescription = if (state.isVisible) "Hide password" else "Show password",
                )
            }
        },
        visualTransformation = if (state.isVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onAny = { onImeAction() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onInputFocused()
                }
            },
    )
}

@Composable
private fun EmailTextField(
    email: String,
    onEmailChange: (String) -> Unit,
    onNext: () -> Unit,
    onInputFocused: () -> Unit,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { Text("Email address") },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onNext() },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onInputFocused()
                }
            },
    )
}

@Composable
private fun PasswordHelperRow(
    isSignUp: Boolean,
    isLoading: Boolean,
    onForgotPassword: () -> Unit,
) {
    if (!isSignUp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onForgotPassword,
                enabled = !isLoading,
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
}

@Composable
private fun PasswordSubmitButton(
    isSignUp: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    onSubmit: () -> Unit,
    actionButtonRequester: BringIntoViewRequester,
) {
    Button(
        onClick = onSubmit,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .bringIntoViewRequester(actionButtonRequester),
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
}

@Composable
private fun PasswordAuthSection(
    state: PasswordInputState,
    actions: PasswordInputActions,
    actionButtonRequester: BringIntoViewRequester,
) {
    Text(
        text = if (state.isSignUp) {
            "Choose a password (minimum 6 characters) to create your account."
        } else {
            "Enter your email and password to access your account."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(12.dp))

    EmailTextField(
        email = state.email,
        onEmailChange = actions.onEmailChange,
        onNext = actions.onNextField,
        onInputFocused = actions.onInputFocused,
    )

    Spacer(Modifier.height(12.dp))

    PasswordTextField(
        state = PasswordFieldState(
            value = state.password,
            isVisible = state.passwordVisible,
            onValueChange = actions.onPasswordChange,
            onToggleVisible = actions.onTogglePasswordVisible,
        ),
        onInputFocused = actions.onInputFocused,
        label = "Password",
        imeAction = if (state.isSignUp) ImeAction.Next else ImeAction.Done,
        onImeAction = if (state.isSignUp) actions.onNextField else actions.onSubmit,
    )

    if (state.isSignUp) {
        Spacer(Modifier.height(12.dp))
        PasswordTextField(
            state = PasswordFieldState(
                value = state.confirmPassword,
                isVisible = state.confirmPasswordVisible,
                onValueChange = actions.onConfirmPasswordChange,
                onToggleVisible = actions.onToggleConfirmPasswordVisible,
            ),
            onInputFocused = actions.onInputFocused,
            label = "Confirm password",
            imeAction = ImeAction.Done,
            onImeAction = actions.onSubmit,
        )
    }

    PasswordHelperRow(
        isSignUp = state.isSignUp,
        isLoading = state.isLoading,
        onForgotPassword = actions.onForgotPassword,
    )

    if (state.errorMessage != null) {
        AuthErrorBanner(message = state.errorMessage)
    }

    if (state.isSignUp) {
        Spacer(Modifier.height(14.dp))
        PrivacyPolicyNotice()
    }

    Spacer(Modifier.height(12.dp))

    val isSubmitEnabled = !state.isLoading &&
        state.email.isNotBlank() &&
        state.password.isNotBlank() &&
        (!state.isSignUp || state.confirmPassword.isNotBlank())
    PasswordSubmitButton(
        isSignUp = state.isSignUp,
        isLoading = state.isLoading,
        enabled = isSubmitEnabled,
        onSubmit = actions.onSubmit,
        actionButtonRequester = actionButtonRequester,
    )

    if (!state.isSignUp) {
        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = actions.onSwitchToEmailLink,
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
                text = "Email me a sign-in link instead",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = GoogleSansWeight.medium,
            )
        }
    }
}
