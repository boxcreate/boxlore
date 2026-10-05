package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.logic.ToolbarWarning
import cx.aswin.boxlore.feature.info.logic.toolbarWarningActionText
import cx.aswin.boxlore.feature.info.logic.toolbarWarningMessage
import cx.aswin.boxlore.feature.info.logic.toolbarWarningTitle

@Composable
internal fun ToolbarWarningBanner(
    warning: ToolbarWarning,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    val isNotice = warning == ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY
    val containerColor = if (isNotice) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val contentColor = if (isNotice) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    val accentColor = if (isNotice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    AnimatedVisibility(
        visible = warning != ToolbarWarning.NONE,
        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
    ) {
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(16.dp),
            colors =
            androidx.compose.material3.CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                ToolbarWarningHeader(warning, onDismiss, contentColor, accentColor)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = toolbarWarningMessage(warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )

                ToolbarWarningAction(warning, onAction, accentColor)
            }
        }
    }
}

@Composable
private fun ToolbarWarningHeader(
    warning: ToolbarWarning,
    onDismiss: () -> Unit,
    contentColor: Color,
    accentColor: Color,
) {
    val isNotice = warning == ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isNotice) Icons.Rounded.Info else Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = toolbarWarningTitle(warning),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                color = contentColor,
            )
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
private fun ToolbarWarningAction(warning: ToolbarWarning, onAction: () -> Unit, accentColor: Color) {
    val isNotice = warning == ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY
    val actionText = toolbarWarningActionText(warning)

    if (actionText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onAction,
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = if (isNotice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = actionText,
                    fontWeight = GoogleSansWeight.bold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
