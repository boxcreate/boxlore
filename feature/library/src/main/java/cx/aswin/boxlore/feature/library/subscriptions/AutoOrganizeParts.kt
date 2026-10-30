package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** The auto-organize preview and look choice, shared by New folder (All genres) and its own page. */
@Composable
internal fun AutoOrganizeSetupContent(
    podcasts: List<Podcast>,
    folders: List<SubscriptionFolder>,
    fixedLook: FolderDisplaySize?,
    onFixedLookChange: (FolderDisplaySize?) -> Unit,
    tileShowsCovers: Boolean,
    onTileShowsCoversChange: (Boolean) -> Unit,
) {
    val preview = remember(podcasts, folders) { autoOrganizePreview(podcasts, folders) }
    Text(
        text = "A folder for every genre, kept up to date as you follow shows. Folders you made stay as they are.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(text = "Folders you'll get", style = MaterialTheme.typography.titleSmall)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            preview.newFolders.forEach { row -> PreviewFolderRow(name = row.genre, detail = showCountLabel(row.showCount), isNew = true) }
            if (preview.existingFolders.isNotEmpty()) {
                PreviewFolderRow(
                    name = preview.existingFolders.joinToString { it.existingFolderName.orEmpty() },
                    detail = "Already there",
                    isNew = false,
                )
            }
        }
    }
    if (preview.showsWithoutGenre > 0) {
        Text(
            text = "${showCountLabel(preview.showsWithoutGenre)} with no genre stay in your main grid.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AutoOrganizeLookChoice(
        fixedLook = fixedLook,
        onFixedLookChange = onFixedLookChange,
        tileShowsCovers = tileShowsCovers,
        onTileShowsCoversChange = onTileShowsCoversChange,
    )
}

internal fun showCountLabel(count: Int): String = if (count == 1) "1 show" else "$count shows"

@Composable
private fun PreviewFolderRow(name: String, detail: String, isNew: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isNew) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (isNew) {
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(percent = 50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    text = "New",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Let boxlore choose" or "Use one look for all" (then the four looks and, for tiles, icon or covers). */
@Composable
internal fun AutoOrganizeLookChoice(
    fixedLook: FolderDisplaySize?,
    onFixedLookChange: (FolderDisplaySize?) -> Unit,
    tileShowsCovers: Boolean,
    onTileShowsCoversChange: (Boolean) -> Unit,
) {
    Text(text = "How folders look", style = MaterialTheme.typography.titleSmall)
    LookChoiceCard(
        selected = fixedLook == null,
        title = "Let boxlore choose",
        body = AUTO_ORGANIZE_CHOOSE_DESCRIPTION,
        onClick = { onFixedLookChange(null) },
    )
    LookChoiceCard(
        selected = fixedLook != null,
        title = "Use one look for all",
        body = "Every genre folder is a tile, 1 row, 2 rows or 3 rows.",
        onClick = { if (fixedLook == null) onFixedLookChange(FolderDisplaySize.SHELF) },
    ) {
        if (fixedLook != null) {
            FolderLookRow(selected = fixedLook, onSelect = { onFixedLookChange(it) }, modifier = Modifier.padding(top = 12.dp))
            if (fixedLook == FolderDisplaySize.COMPACT) {
                TileCoverChoice(
                    showsCovers = tileShowsCovers,
                    onChange = onTileShowsCoversChange,
                    iconKey = null,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun LookChoiceCard(
    selected: Boolean,
    title: String,
    body: String,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected
            role = Role.RadioButton
        },
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold)
                    Text(text = body, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(modifier = Modifier.padding(start = 8.dp)) { extra() }
        }
    }
}
