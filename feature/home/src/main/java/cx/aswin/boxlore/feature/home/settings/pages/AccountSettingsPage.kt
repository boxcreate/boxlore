package cx.aswin.boxlore.feature.home.settings.pages

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.BoxLoreUser
import cx.aswin.boxlore.core.network.AuthRepository
import cx.aswin.boxlore.feature.home.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.home.settings.components.SettingsContent
import cx.aswin.boxlore.feature.home.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.home.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.home.settings.components.SettingsScaffold
import kotlinx.coroutines.launch

@Composable
internal fun AccountSettingsPage(
    authRepository: AuthRepository?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val currentUser by (
        authRepository?.currentUser?.collectAsState()
            ?: remember { mutableStateOf<BoxLoreUser?>(null) }
    )

    var showDeleteConfirmation by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = "Account",
        onBack = onBack,
        scrollState = scrollState,
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
