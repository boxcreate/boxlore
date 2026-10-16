package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast

private val FreshListCorner = 24.dp

/**
 * After an OPML import: which shows start fresh, with old episodes marked played. One row picks all or
 * none; every show is listed with its artwork. The question scrolls with the list so short windows keep
 * room for the shows, and the two choices stay at the bottom.
 */
@Composable
internal fun AskCompletedContent(
    state: OpmlImportState.AskCompleted,
    onSelectionChanged: (Set<String>) -> Unit,
    onConfirmCompleted: () -> Unit,
    onSkipCompleted: () -> Unit,
) {
    val podcasts = remember(state.importedPodcasts) { LazyListKeyPolicy.deduplicateById(state.importedPodcasts) { it.id } }
    val ids = remember(podcasts) { podcasts.mapTo(HashSet()) { it.id } }
    val selected = state.selectedIds.count { it in ids }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("import_fresh_list"),
            contentPadding = PaddingValues(top = 56.dp, bottom = 8.dp),
        ) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.import_fresh_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = GoogleSansWeight.bold,
                    )
                    Text(stringResource(R.string.import_fresh_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "all") {
                FreshAllRow(
                    total = podcasts.size,
                    selected = selected,
                    onToggle = { onSelectionChanged(if (selected == podcasts.size) emptySet() else podcasts.map { it.id }.toSet()) },
                    last = podcasts.isEmpty(),
                )
            }
            itemsIndexed(podcasts, key = { _, podcast -> podcast.id }) { index, podcast ->
                val checked = podcast.id in state.selectedIds
                FreshShowRow(
                    podcast = podcast,
                    checked = checked,
                    onToggle = { onSelectionChanged(if (checked) state.selectedIds - podcast.id else state.selectedIds + podcast.id) },
                    last = index == podcasts.lastIndex,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ImportPrimaryButton(
                pluralStringResource(R.plurals.import_fresh_confirm, selected, selected),
                onConfirmCompleted,
                Modifier.testTag("import_fresh_confirm"),
                enabled = selected > 0,
            )
            ImportQuietButton(stringResource(R.string.import_fresh_skip), onSkipCompleted, Modifier.testTag("import_fresh_skip"))
        }
    }
}

@Composable
private fun FreshAllRow(total: Int, selected: Int, onToggle: () -> Unit, last: Boolean) {
    val toggle = when (selected) {
        0 -> ToggleableState.Off
        total -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Column(Modifier.fillMaxWidth().clip(freshRowShape(first = true, last = last)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .triStateToggleable(state = toggle, role = Role.Checkbox, onClick = onToggle)
                .testTag("import_fresh_all")
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.import_fresh_all, total, total),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.semiBold,
            )
            Text(stringResource(R.string.import_fresh_selected, selected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TriStateCheckbox(state = toggle, onClick = null)
        }
        if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun FreshShowRow(podcast: Podcast, checked: Boolean, onToggle: () -> Unit, last: Boolean) {
    Column(Modifier.fillMaxWidth().clip(freshRowShape(first = false, last = last)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    proxyWidth = 150,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(podcast.title, style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.semiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (podcast.artist.isNotBlank()) {
                    Text(podcast.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Checkbox(checked = checked, onCheckedChange = null)
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 78.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private fun freshRowShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) FreshListCorner else 0.dp,
    topEnd = if (first) FreshListCorner else 0.dp,
    bottomStart = if (last) FreshListCorner else 0.dp,
    bottomEnd = if (last) FreshListCorner else 0.dp,
)
