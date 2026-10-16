package cx.aswin.boxlore.feature.settings.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R

/**
 * Signed out: "Your library, on every device" beside Echo, the three things that sync, then how to sign
 * in. Before a method is picked there are three cards (Google, an email sign-in link, email and
 * password); picking an email card opens its form in their place, with a way back to the cards (also
 * on Back). The pick and anything typed survive rotation and resizing ([AccountAuthState] is saved).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SignedOutContent(state: AccountAuthState) {
    val density = LocalDensity.current
    val actionButtonRequester = remember { BringIntoViewRequester() }
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(imeBottom, state.isAnyInputFocused) {
        if (imeBottom > 0 && state.isAnyInputFocused) runCatching { actionButtonRequester.bringIntoView() }
    }
    BackHandler(enabled = state.methodChosen) { state.showMethods() }

    AccountHeader(stringResource(R.string.account_headline), subtitle = null, syncing = false)
    AccountSyncTiles(state = null)
    Text(stringResource(R.string.account_sign_in_label), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (!state.methodChosen) {
        AccountMethodCards(state)
    } else {
        Surface(Modifier.fillMaxWidth().testTag("account_email_form"), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.usePasswordAuth) {
                    EmailLinkInputSection(state.toEmailInputState(), state.toEmailInputActions(actionButtonRequester), actionButtonRequester)
                } else {
                    val focusManager = LocalFocusManager.current
                    PasswordAuthSection(
                        state = state.toPasswordInputState(),
                        actions = state.toPasswordInputActions(actionButtonRequester, onNextField = { focusManager.moveFocus(FocusDirection.Down) }),
                        selectedMode = state.activeAuthMode,
                        actionButtonRequester = actionButtonRequester,
                    )
                }
            }
        }
    }

    PrivacyPolicyNotice(
        prefix = stringResource(R.string.account_privacy_prefix),
        suffix = ".",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun AccountMethodCards(state: AccountAuthState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountMethodCard(
            title = stringResource(if (state.isGoogleLoading) R.string.account_google_connecting else R.string.account_google),
            detail = stringResource(R.string.account_google_detail),
            emphasized = true,
            enabled = !state.isAnyLoading,
            onClick = state::handleGoogleSignIn,
            tag = "account_method_google",
        ) {
            Box(Modifier.size(36.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                if (state.isGoogleLoading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Image(painterResource(cx.aswin.boxlore.core.designsystem.R.drawable.ic_google_logo), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        AccountMethodCard(stringResource(R.string.account_link), stringResource(R.string.account_link_detail), emphasized = false, enabled = !state.isAnyLoading, onClick = { state.chooseEmailMethod(password = false) }, tag = "account_method_link") {
            AccountMethodIcon(Icons.Rounded.Email)
        }
        AccountMethodCard(stringResource(R.string.account_password), stringResource(R.string.account_password_detail), emphasized = false, enabled = !state.isAnyLoading, onClick = { state.chooseEmailMethod(password = true) }, tag = "account_method_password") {
            AccountMethodIcon(Icons.Rounded.Key)
        }
        state.errorMessage?.let { AuthErrorBanner(message = it) }
    }
}

@Composable
private fun AccountMethodCard(
    title: String,
    detail: String,
    emphasized: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    leading: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(tag),
        shape = RoundedCornerShape(24.dp),
        color = if (emphasized) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (emphasized) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = if (emphasized) colors.onPrimaryContainer else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AccountMethodIcon(icon: ImageVector) {
    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ColumnScope.EmailLinkInputSection(
    state: EmailInputState,
    actions: EmailInputActions,
    actionButtonRequester: BringIntoViewRequester,
) {
    Text(stringResource(R.string.account_link), style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        label = { Text(stringResource(R.string.account_email)) },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.onSendLink() }),
        modifier = Modifier.fillMaxWidth().testTag("account_email_field").onFocusChanged { if (it.isFocused) actions.onInputFocused() },
    )
    state.errorMessage?.let { AuthErrorBanner(message = it) }
    AccountPrimaryButton(
        label = stringResource(R.string.account_send_link),
        loading = state.isLoading,
        enabled = !state.isLoading && state.email.isNotBlank(),
        onClick = actions.onSendLink,
        modifier = Modifier.bringIntoViewRequester(actionButtonRequester).testTag("account_send_link"),
    )
    AccountOtherWays(actions.onOtherWays)
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
    label: String,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = state.onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = state.onToggleVisible) {
                Icon(
                    imageVector = if (state.isVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (state.isVisible) R.string.account_hide_password else R.string.account_show_password),
                )
            }
        },
        visualTransformation = if (state.isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onInputFocused() },
    )
}

@Composable
private fun ColumnScope.PasswordAuthSection(
    state: PasswordInputState,
    actions: PasswordInputActions,
    selectedMode: AuthMode,
    actionButtonRequester: BringIntoViewRequester,
) {
    AccountAuthModeSelector(selected = selectedMode, onSelect = actions.onSelectMode)
    OutlinedTextField(
        value = state.email,
        onValueChange = actions.onEmailChange,
        label = { Text(stringResource(R.string.account_email)) },
        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { actions.onNextField() }),
        modifier = Modifier.fillMaxWidth().testTag("account_email_field").onFocusChanged { if (it.isFocused) actions.onInputFocused() },
    )
    PasswordTextField(
        state = PasswordFieldState(state.password, state.passwordVisible, actions.onPasswordChange, actions.onTogglePasswordVisible),
        onInputFocused = actions.onInputFocused,
        label = stringResource(R.string.account_password_label),
        imeAction = if (state.isSignUp) ImeAction.Next else ImeAction.Done,
        onImeAction = if (state.isSignUp) actions.onNextField else actions.onSubmit,
    )
    if (state.isSignUp) {
        Text(
            stringResource(R.string.account_password_rule),
            Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PasswordTextField(
            state = PasswordFieldState(state.confirmPassword, state.confirmPasswordVisible, actions.onConfirmPasswordChange, actions.onToggleConfirmPasswordVisible),
            onInputFocused = actions.onInputFocused,
            label = stringResource(R.string.account_confirm_password),
            imeAction = ImeAction.Done,
            onImeAction = actions.onSubmit,
        )
    } else {
        TextButton(actions.onForgotPassword, Modifier.align(Alignment.End).heightIn(min = 48.dp), enabled = !state.isLoading) {
            Text(stringResource(R.string.account_forgot_password))
        }
    }
    state.errorMessage?.let { AuthErrorBanner(message = it) }
    AccountPrimaryButton(
        label = stringResource(if (state.isSignUp) R.string.account_create else R.string.account_sign_in),
        loading = state.isLoading,
        enabled = !state.isLoading && state.email.isNotBlank() && state.password.isNotBlank() && (!state.isSignUp || state.confirmPassword.isNotBlank()),
        onClick = actions.onSubmit,
        modifier = Modifier.bringIntoViewRequester(actionButtonRequester).testTag("account_password_submit"),
    )
    AccountOtherWays(actions.onOtherWays)
}

@Composable
private fun ColumnScope.AccountOtherWays(onClick: () -> Unit) {
    TextButton(onClick, Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("account_other_ways")) {
        Text(stringResource(R.string.account_other_ways))
    }
}

/** The page's main action: full width, pill-shaped, a spinner while it works. */
@Composable
internal fun AccountPrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, enabled: Boolean = true) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled, shape = CircleShape) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.medium)
        }
    }
}
