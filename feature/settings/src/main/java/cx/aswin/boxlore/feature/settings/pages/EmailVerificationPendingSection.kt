package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

@Composable
internal fun EmailVerificationPendingSection(
    state: AccountAuthState,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val displayEmail = state.email.ifBlank {
        state.authRepository?.currentUser?.value?.email.orEmpty()
    }

    // Auto-check when returning to boxlore from the user's email client
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.checkVerificationStatus(
            silentOnFailure = true,
            onSuccess = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            },
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Header
        EmailVerificationHeader()

        Spacer(Modifier.height(14.dp))

        // 2. Sent-to address card
        if (displayEmail.isNotBlank()) {
            EmailVerificationAddressCard(email = displayEmail)
            Spacer(Modifier.height(12.dp))
        }

        // 3. Spam/Junk folder warning card
        EmailVerificationSpamWarningCard()

        Spacer(Modifier.height(16.dp))

        // 4. Primary CTA: Open Gmail or default email client
        val isGmail = displayEmail.trim().endsWith("@gmail.com", ignoreCase = true) ||
            displayEmail.trim().endsWith("@googlemail.com", ignoreCase = true)
        Button(
            onClick = { openGmailOrEmailApp(state.context, displayEmail) },
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

        Spacer(Modifier.height(10.dp))

        // 5. Secondary CTA: Manual check
        FilledTonalButton(
            onClick = {
                state.checkVerificationStatus(
                    silentOnFailure = false,
                    onSuccess = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            },
            enabled = !state.isCheckingVerification,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.isCheckingVerification) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Checking verification...",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.medium,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "I've Verified My Email",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        }

        // 6. Error feedback if verification check failed
        if (state.errorMessage != null) {
            AuthErrorBanner(message = state.errorMessage!!)
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }

        // 7. Footer: Resend cooldown + Typo escape hatch
        EmailVerificationFooter(
            resendCooldownSeconds = state.resendCooldownSeconds,
            isLoading = state.isEmailLoading,
            onResend = state::resendVerificationEmail,
            onSignOutAndReset = state::signOutAndResetToSignUp,
        )
    }
}

@Composable
private fun EmailVerificationHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Verify your email",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = GoogleSansWeight.bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "One quick step to activate cloud sync",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmailVerificationAddressCard(email: String) {
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
                text = "Sent to:",
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
private fun EmailVerificationSpamWarningCard() {
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
                    text = "Important: Verification emails almost always land in Spam on first receipt. If not in your inbox, please check there!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun EmailVerificationFooter(
    resendCooldownSeconds: Int,
    isLoading: Boolean,
    onResend: () -> Unit,
    onSignOutAndReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val resendText = if (resendCooldownSeconds > 0) {
            "Resend in ${resendCooldownSeconds}s"
        } else {
            "Resend email"
        }
        val isResendEnabled = resendCooldownSeconds <= 0 && !isLoading
        TextButton(
            onClick = onResend,
            enabled = isResendEnabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        ) {
            Text(
                text = resendText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isResendEnabled) GoogleSansWeight.bold else GoogleSansWeight.regular,
            )
        }
        TextButton(
            onClick = onSignOutAndReset,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text(
                text = "Wrong email? Sign out",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
