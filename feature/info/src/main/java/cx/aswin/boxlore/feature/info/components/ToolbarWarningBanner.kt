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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.R
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
        // The same rounded shape as the page's other cards.
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            contentColor = contentColor,
            modifier =
            Modifier
                .fillMaxWidth()
                .boxloreCardWidth(BoxloreCardFamily.Utility)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag(TOOLBAR_WARNING_TAG),
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
                    text = toolbarWarningMessage(warning)?.let { stringResource(it) }.orEmpty(),
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
                text = stringResource(toolbarWarningTitle(warning)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                color = contentColor,
            )
        }
        // A full 48dp target, drawn where the small close icon always sat.
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.offset(x = 12.dp).size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.podcast_info_warning_dismiss),
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolbarWarningAction(warning: ToolbarWarning, onAction: () -> Unit, accentColor: Color) {
    val isNotice = warning == ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY
    val actionText = toolbarWarningActionText(warning)?.let { stringResource(it) }.orEmpty()

    if (actionText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
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

/** Test tag on the warning banner under the toolbar. */
internal const val TOOLBAR_WARNING_TAG = "podcast_info_toolbar_warning"
