package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.feature.library.SubscriptionSort

/**
 * State bundle for [SubscriptionSortSheet].
 */
internal data class SubscriptionSortConfig(
    val currentSort: SubscriptionSort,
    val folderSort: FolderInterSort,
    val intraFolderSort: FolderIntraSort,
    val autoOrganizeFolders: Boolean,
    /** Folders that fill from a genre, for the auto-organize row's summary. */
    val genreFolderCount: Int = 0,
)

/**
 * Actions bundle for [SubscriptionSortSheet].
 */
internal data class SubscriptionSortActions(
    val onSortChange: (SubscriptionSort) -> Unit,
    val onFolderSortChange: (FolderInterSort) -> Unit,
    val onIntraFolderSortChange: (FolderIntraSort) -> Unit,
    /** Opens the auto-organize page, where it is turned on or off and its look is set. */
    val onOpenAutoOrganize: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Sort and organize: how your shows, the folders and the covers inside folders are ordered, as
 * chips like Explore's, then a row that opens the auto-organize page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionSortSheet(
    config: SubscriptionSortConfig,
    actions: SubscriptionSortActions,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("subscription_sort")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            SubscriptionSortSheetContent(
                config = config,
                actions = actions,
                modifier = Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding(),
            )
        }
    }
}

@Composable
internal fun SubscriptionSortSheetContent(
    config: SubscriptionSortConfig,
    actions: SubscriptionSortActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Sort and organize",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.onDismiss) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SortSection(title = "Your shows", description = "Shows that aren't in a folder") {
            SubscriptionSortOptions.forEach { (sort, label) ->
                SortChip(label = label, selected = config.currentSort == sort, onClick = { actions.onSortChange(sort) })
            }
        }
        SortSection(title = "Order of folders", description = "Where each folder sits on the Shows tab") {
            FolderInterSort.entries.forEach { option ->
                SortChip(label = option.label, selected = config.folderSort == option, onClick = { actions.onFolderSortChange(option) })
            }
        }
        SortSection(title = "Shows inside folders", description = "The order of covers in each folder") {
            FolderIntraSort.entries.forEach { option ->
                SortChip(label = option.label, selected = config.intraFolderSort == option, onClick = { actions.onIntraFolderSortChange(option) })
            }
        }
        Column(modifier = Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AutoOrganizeRow(
                autoOrganize = config.autoOrganizeFolders,
                genreFolderCount = config.genreFolderCount,
                onClick = actions.onOpenAutoOrganize,
            )
            DragRepositionFootnote()
        }
    }
}

private val SubscriptionSortOptions = listOf(
    SubscriptionSort.SmartRank to "Smart",
    SubscriptionSort.RecentlyUpdated to "Recently updated",
    SubscriptionSort.Alphabetical to "A–Z",
    SubscriptionSort.MostListened to "Most listened",
    SubscriptionSort.Manual to "Manual",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortSection(title: String, description: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips()
        }
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    SubscriptionChip(
        label = label,
        selected = selected,
        onClick = onClick,
        icon = if (selected) Icons.Rounded.Check else null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun AutoOrganizeRow(
    autoOrganize: Boolean,
    genreFolderCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Auto-organize", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = autoOrganizeSummary(autoOrganize, genreFolderCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DragRepositionFootnote() {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.TouchApp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "To move shows or folders by hand, press and hold one and choose Reorder.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
