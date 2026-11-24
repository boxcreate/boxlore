package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/**
 * Where the listener's shows are now: their boxlore account, a boxlore backup, or another podcast app.
 * Echo waves from its stage above the question; the three answers and where to find an OPML export
 * sit at the bottom.
 */
@Composable
internal fun SelectorContent(
    onJson: () -> Unit,
    onOpml: () -> Unit,
    onSyncAccount: () -> Unit = {},
) {
    ImportPage(
        echo = ImportEchoMoment.Pick,
        content = {
            ImportTitle(stringResource(R.string.import_pick_title))
            ImportBody(stringResource(R.string.import_pick_body))
        },
        actions = {
            // The three answers read as one connected group, like Echo's answers just before.
            Column(Modifier.fillMaxWidth().testTag("import_options"), verticalArrangement = Arrangement.spacedBy(ImportGroupGap)) {
                ImportOptionCard(
                    icon = Icons.Rounded.CloudSync,
                    title = stringResource(R.string.import_option_account),
                    subtitle = stringResource(R.string.import_option_account_body),
                    onClick = onSyncAccount,
                    shape = importGroupShape(0, 3),
                    modifier = Modifier.testTag("import_option_account"),
                )
                ImportOptionCard(
                    icon = Icons.Rounded.SettingsBackupRestore,
                    title = stringResource(R.string.import_option_backup),
                    subtitle = stringResource(R.string.import_option_backup_body),
                    onClick = onJson,
                    shape = importGroupShape(1, 3),
                    modifier = Modifier.testTag("import_option_backup"),
                )
                ImportOptionCard(
                    icon = Icons.Rounded.ImportExport,
                    title = stringResource(R.string.import_option_opml),
                    subtitle = stringResource(R.string.import_option_opml_body),
                    onClick = onOpml,
                    shape = importGroupShape(2, 3),
                    modifier = Modifier.testTag("import_option_opml"),
                )
            }
            Text(
                text = stringResource(R.string.import_opml_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        },
    )
}

@Composable
internal fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ImportCorner,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A grouped answer's corners: round on the group's outer edge, tight inside (Echo's answer values). */
internal fun importGroupShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) ImportGroupOuter else ImportGroupInner
    val bottom = if (index == count - 1) ImportGroupOuter else ImportGroupInner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

private val ImportGroupOuter = 24.dp
private val ImportGroupInner = 6.dp
private val ImportGroupGap = 3.dp
