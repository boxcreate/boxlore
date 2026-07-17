package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes

@Composable
fun PrivacyConsentDialog(
    onConsentDecided: (crashReporting: Boolean, usageAnalytics: Boolean) -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column {
                // State
                var crashReporting by remember { mutableStateOf(false) }
                var usageAnalytics by remember { mutableStateOf(false) }
                var privacyPolicyAccepted by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                ) {
                    // Header
                    PrivacyHeader()
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Options First (Redesign)
                    ConsentOptions(
                        crashReporting = crashReporting,
                        onCrashChange = { crashReporting = it },
                        usageAnalytics = usageAnalytics,
                        onUsageChange = { usageAnalytics = it },
                        privacyPolicyAccepted = privacyPolicyAccepted,
                        onPolicyChange = { privacyPolicyAccepted = it }
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Info/Legal Text Last
                    PrivacyInfo()
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Footer
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp,
                    shadowElevation = 4.dp
                ) {
                     Column(modifier = Modifier.padding(24.dp)) {
                         ConsentActions(
                             isEnabled = privacyPolicyAccepted,
                             onAccept = { onConsentDecided(crashReporting, usageAnalytics) }
                         )
                     }
                }
            }
        }
    }
}

@Composable
private fun PrivacyHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(), // Ensure full width for centering
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = ExpressiveShapes.Burst,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {}
            BoxLoreLogo(
                modifier = Modifier.scale(0.8f),
                textColor = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Data & Privacy",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Customize your data sharing settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PrivacyInfo() {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Why we collect data",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The data shared helps us improve the product by understanding user behaviour. No user-specific targeted personalization will be done, and no data collected will ever be user-identifiable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        DataCollectedExpander()
    }
}

@Composable
private fun DataCollectedExpander() {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { isExpanded = !isExpanded }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), MaterialTheme.shapes.medium)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "View Data Collected",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                DataPoint("Crash Reports", "Stack traces, device state, and crash logs to identify stability issues.")
                Spacer(modifier = Modifier.height(8.dp))
                DataPoint("Usage Statistics", "Events such as play, subscribe, search, screen views, and playback state.")
            }
        }
    }
}

@Composable
private fun ConsentOptions(
    crashReporting: Boolean,
    onCrashChange: (Boolean) -> Unit,
    usageAnalytics: Boolean,
    onUsageChange: (Boolean) -> Unit,
    privacyPolicyAccepted: Boolean,
    onPolicyChange: (Boolean) -> Unit
) {
    val allChecked = crashReporting && usageAnalytics && privacyPolicyAccepted
    
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Master Toggle Card
        Surface(
            color = if (allChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (allChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val newState = !allChecked
                    onCrashChange(newState)
                    onUsageChange(newState)
                    onPolicyChange(newState)
                }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = allChecked,
                    onCheckedChange = null, // Handled by Surface click
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Agree to All",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Enables Crash Reporting, Usage Analytics, and accepts Privacy Policy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allChecked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        Text("Individual Settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        // Individual Cards
        ToggleCard(
            title = "Crash Reports (Optional)",
            description = "Share stack traces when the app crashes so we can fix bugs.",
            checked = crashReporting,
            onCheckedChange = onCrashChange
        )
        
        ToggleCard(
            title = "Usage Statistics (Optional)",
            description = "Share anonymous interaction data to help us identify popular features.",
            checked = usageAnalytics,
            onCheckedChange = onUsageChange
        )
        
        // Privacy Policy Card
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, if (!privacyPolicyAccepted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPolicyChange(!privacyPolicyAccepted) }
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = privacyPolicyAccepted, onCheckedChange = null)
                Spacer(modifier = Modifier.width(12.dp))
                val uriHandler = LocalUriHandler.current
                Column {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                     Text(
                        text = "I have read and agree to the Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Read Policy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp).clickable {
                             try { uriHandler.openUri("https://aswin.cx/boxlore/privacy") } catch(_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConsentActions(
    isEnabled: Boolean,
    onAccept: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onAccept,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Accept & Continue")
        }
    }
}

@Composable
private fun DataPoint(title: String, desc: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
