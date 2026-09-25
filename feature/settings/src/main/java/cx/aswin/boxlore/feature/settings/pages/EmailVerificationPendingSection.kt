package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
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
        // 1. Header with Account Saved confirmation chip and explanation
        EmailVerificationHeader()

        Spacer(Modifier.height(14.dp))

        // 2. Sent-to address card
        if (displayEmail.isNotBlank()) {
            EmailVerificationAddressCard(email = displayEmail)
            Spacer(Modifier.height(12.dp))
        }

        // 3. 3-step action guide
        EmailVerificationStepsCard()

        Spacer(Modifier.height(12.dp))

        // 4. Spam/Junk folder warning card
        EmailVerificationSpamWarningCard()

        Spacer(Modifier.height(16.dp))

        // 5. Primary CTA: Open Gmail or default email client
        OpenMailAppButton(
            displayEmail = displayEmail,
            context = state.context,
        )

        Spacer(Modifier.height(10.dp))

        // 6. Secondary CTA: Manual check
        CheckVerificationButton(
            isChecking = state.isCheckingVerification,
            onCheck = {
                state.checkVerificationStatus(
                    silentOnFailure = false,
                    onSuccess = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            },
        )

        // 7. Error feedback if verification check failed
        if (state.errorMessage != null) {
            AuthErrorBanner(message = state.errorMessage!!)
            Spacer(Modifier.height(8.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }

        // 8. Footer: Resend cooldown + Typo escape hatch
        EmailVerificationFooter(
            resendCooldownSeconds = state.resendCooldownSeconds,
            isLoading = state.isEmailLoading,
            onResend = state::resendVerificationEmail,
            onSignOutAndReset = state::signOutAndResetToSignUp,
        )
    }
}

@Composable
private fun AccountSavedConfirmationChip() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                text = "Account & Password Saved ✓",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
    }
}

@Composable
private fun EmailVerificationHeader() {
    Column(modifier = Modifier.fillMaxWidth()) {
        AccountSavedConfirmationChip()

        Spacer(Modifier.height(12.dp))

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
                    text = "To activate cross-device sync and verify you own this email address, we sent a quick confirmation link.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun EmailVerificationStepsCard() {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Quick steps to finish:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VerificationStepRow(number = "1", text = "Open your mail app.")
            VerificationStepRow(number = "2", text = "Tap the confirmation link in the email.")
            VerificationStepRow(number = "3", text = "Return to boxlore — your account will activate automatically!")
        }
    }
}

@Composable
private fun VerificationStepRow(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 17.sp,
        )
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

@Composable
private fun OpenMailAppButton(
    displayEmail: String,
    context: Context,
) {
    val isGmail = displayEmail.trim().endsWith("@gmail.com", ignoreCase = true) ||
        displayEmail.trim().endsWith("@googlemail.com", ignoreCase = true)
    Button(
        onClick = { openGmailOrEmailApp(context, displayEmail) },
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
}

@Composable
private fun CheckVerificationButton(
    isChecking: Boolean,
    onCheck: () -> Unit,
) {
    FilledTonalButton(
        onClick = onCheck,
        enabled = !isChecking,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        if (isChecking) {
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
}
