package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder

/** The look auto-organize gives genre folders: [fixedSize] null lets boxlore choose by size. */
internal data class AutoOrganizeLook(
    val fixedSize: FolderDisplaySize? = null,
    val tileShowsCovers: Boolean = true,
)

internal data class AutoOrganizePageActions(
    val onClose: () -> Unit,
    val onLookChange: (AutoOrganizeLook) -> Unit,
    val onTurnOn: () -> Unit,
    /** Opens the turn-off message. */
    val onTurnOff: () -> Unit,
    val onOpenFolder: (String) -> Unit,
    val onApplyToAll: () -> Unit,
)

/**
 * Auto-organize's own page, from Sort and organize. Off: what it would make and a look, then Turn
 * on. On: a switch, the genre folders it keeps filled, and the look for them, which can be
 * applied to the folders it already made.
 */
@Composable
internal fun AutoOrganizePage(
    enabled: Boolean,
    podcasts: List<Podcast>,
    folders: List<SubscriptionFolder>,
    look: AutoOrganizeLook,
    actions: AutoOrganizePageActions,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("auto_organize_folders")
    val genreFolders = genreFolders(folders)
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            FolderFlowTopBar(title = "Auto-organize", onBack = actions.onClose, showClose = false)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = if (enabled) bottomPadding + 24.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val onFixedLookChange = { size: FolderDisplaySize? -> actions.onLookChange(look.copy(fixedSize = size)) }
                val onCoversChange = { covers: Boolean -> actions.onLookChange(look.copy(tileShowsCovers = covers)) }
                if (enabled) {
                    AutoOrganizeStatusCard(genreFolderCount = genreFolders.size, onTurnOff = actions.onTurnOff)
                    if (genreFolders.isNotEmpty()) {
                        Text(text = "Genre folders", style = MaterialTheme.typography.titleSmall)
                        GenreFolderList(genreFolders, actions.onOpenFolder)
                    }
                    AutoOrganizeLookChoice(look.fixedSize, onFixedLookChange, look.tileShowsCovers, onCoversChange)
                    if (genreFolders.isNotEmpty()) {
                        OutlinedButton(onClick = actions.onApplyToAll) {
                            Text(text = applyToAllLabel(genreFolders.size))
                        }
                        Text(
                            text = "New genre folders get this look. Apply it to change the ones you have too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = actions.onTurnOff) { Text(text = "Turn off auto-organize") }
                } else {
                    AutoOrganizeSetupContent(podcasts, folders, look.fixedSize, onFixedLookChange, look.tileShowsCovers, onCoversChange)
                }
            }
            if (!enabled) {
                FolderFlowBottomBar(primaryLabel = "Turn on auto-organize", primaryEnabled = true, onPrimary = actions.onTurnOn)
            }
        }
    }
}

internal fun applyToAllLabel(count: Int): String = if (count == 1) "Apply to 1 genre folder" else "Apply to all $count genre folders"

@Composable
private fun AutoOrganizeStatusCard(genreFolderCount: Int, onTurnOff: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Auto-organize is on", style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                Text(
                    text = "${genreFolderCountLabel(genreFolderCount)}, kept up to date as you follow shows",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = true,
                onCheckedChange = { if (!it) onTurnOff() },
                modifier = Modifier.semantics { contentDescription = "Auto-organize" },
            )
        }
    }
}

private fun genreFolderCountLabel(count: Int): String = if (count == 1) "1 genre folder" else "$count genre folders"

@Composable
private fun GenreFolderList(folders: List<SubscriptionFolder>, onOpenFolder: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            folders.forEach { folder ->
                Surface(onClick = { onOpenFolder(folder.id) }, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = GenreIcons.folderIconOrFallback(folder.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        )
                        Text(
                            text = "${showCountLabel(folder.podcastCount)} · ${folderLookName(folder.displaySize).lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
