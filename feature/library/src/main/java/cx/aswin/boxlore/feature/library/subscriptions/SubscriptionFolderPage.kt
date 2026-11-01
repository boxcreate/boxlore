package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxlorePosterColumns
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.feature.library.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.library.logic.SubscriptionManualOrderLogic
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

internal data class FolderPageActions(
    val onBack: () -> Unit = {},
    val onPodcastClick: (String) -> Unit = {},
    val onPodcastLongClick: (Podcast) -> Unit = {},
    val onEditFolder: () -> Unit = {},
    val onAddShows: () -> Unit = {},
    val onMoreOptions: () -> Unit = {},
    val onOrderClick: () -> Unit = {},
    val onSaveReorder: (List<String>) -> Unit = {},
    val onCancelReorder: () -> Unit = {},
)

internal data class FolderPageConfig(
    val isReorderMode: Boolean = false,
    /** How shows inside folders are ordered, shown on the order chip. */
    val orderLabel: String = "",
    /** Room for the mini player and navigation drawn over the page. */
    val bottomPadding: Dp = 0.dp,
)

/**
 * A folder opened from the Shows tab, drawn over Subscriptions (no new route). Header, then
 * either the genre it fills from or Add shows, then its shows; press and hold a show for its
 * menu. Reorder mode drags covers and saves with the bar at the bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SubscriptionFolderPage(
    folder: SubscriptionFolder,
    podcasts: List<Podcast>,
    config: FolderPageConfig,
    actions: FolderPageActions,
    modifier: Modifier = Modifier,
) {
    TrackScreenView("subscription_folder", folder.id)
    val lastSeenEpisodes = LocalLastSeenEpisodes.current
    val newCount = remember(podcasts, lastSeenEpisodes) {
        podcasts.count { it.isLatestEpisodeNew(lastSeenEpisodes[it.id]) }
    }
    var localPodcasts by remember(podcasts, config.isReorderMode) { mutableStateOf(podcasts) }
    val gridState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(gridState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyGridState
        val toId = to.key as? String ?: return@rememberReorderableLazyGridState
        val podById = localPodcasts.associateBy { it.id }
        if (fromId !in podById || toId !in podById) return@rememberReorderableLazyGridState
        localPodcasts = SubscriptionManualOrderLogic.move(localPodcasts.map { it.id }, fromId, toId).mapNotNull(podById::get)
    }
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                FolderPageTopBar(
                    showFolderActions = !config.isReorderMode,
                    onBack = if (config.isReorderMode) actions.onCancelReorder else actions.onBack,
                    onEdit = actions.onEditFolder,
                    onMore = actions.onMoreOptions,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(
                        boxlorePosterColumns(LocalBoxloreWindowLayout.current, boxloreContentWidthDp(), compactColumns = 3, gapDp = 12f),
                    ),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = config.bottomPadding + 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item(key = "folder_page_header", span = fullSpan) {
                        FolderPageHeader(folder = folder, showCount = podcasts.size, newCount = newCount)
                    }
                    if (!config.isReorderMode) {
                        item(key = "folder_page_actions", span = fullSpan) {
                            FolderPageActionsRow(
                                orderLabel = config.orderLabel.takeIf { podcasts.isNotEmpty() },
                                onOrderClick = actions.onOrderClick,
                                // A genre folder fills itself, so only a folder you fill offers Add shows.
                                onAddShows = actions.onAddShows.takeUnless { folder.isGenreLinked },
                            )
                        }
                    }
                    if (podcasts.isEmpty()) {
                        item(key = "folder_page_empty", span = fullSpan) {
                            EmptyFolderPageContent(folder = folder)
                        }
                    }
                    items(localPodcasts, key = { it.id }) { podcast ->
                        if (config.isReorderMode) {
                            ReorderableItem(reorderableState, key = podcast.id, modifier = Modifier.animateItem()) { isDragging ->
                                FolderPagePodcastItem(
                                    podcast = podcast,
                                    lastSeenId = lastSeenEpisodes[podcast.id],
                                    isDragging = isDragging,
                                    dragModifier = Modifier.longPressDraggableHandle(),
                                )
                            }
                        } else {
                            FolderPagePodcastItem(
                                podcast = podcast,
                                lastSeenId = lastSeenEpisodes[podcast.id],
                                onClick = { actions.onPodcastClick(podcast.id) },
                                onLongClick = { actions.onPodcastLongClick(podcast) },
                            )
                        }
                    }
                }
            }
            if (config.isReorderMode) {
                SubscriptionReorderBar(
                    reorderMode = ReorderMode.FolderShows(folder.id, folder.name),
                    onSave = { actions.onSaveReorder(localPodcasts.map { it.id }) },
                    onCancel = actions.onCancelReorder,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = config.bottomPadding + 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderPageTopBar(
    showFolderActions: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.weight(1f))
        if (showFolderActions) {
            IconButton(onClick = onEdit) {
                Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit folder")
            }
            IconButton(onClick = onMore) {
                Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More options")
            }
        }
    }
}

@Composable
private fun FolderPageHeader(
    folder: SubscriptionFolder,
    showCount: Int,
    newCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = GenreIcons.folderIconOrFallback(folder.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.semiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (folder.isGenreLinked) {
                    AutoFolderMark(size = 16.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = folderPageSummary(showCount, newCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The order of the covers (opens Sort and organize) and, for a folder you fill, Add shows. */
@Composable
private fun FolderPageActionsRow(orderLabel: String?, onOrderClick: () -> Unit, onAddShows: (() -> Unit)?) {
    if (orderLabel == null && onAddShows == null) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (orderLabel != null) {
            SubscriptionActionChip(
                label = orderLabel,
                onClick = onOrderClick,
                icon = Icons.AutoMirrored.Rounded.Sort,
                trailingIcon = Icons.Rounded.ExpandMore,
            )
        }
        if (onAddShows != null) {
            SubscriptionActionChip(label = "Add shows", onClick = onAddShows, icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun FolderPagePodcastItem(
    podcast: Podcast,
    lastSeenId: String?,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(dragModifier),
    ) {
        SubscriptionGridCard(
            podcast = podcast,
            lastSeenId = lastSeenId,
            onClick = onClick,
            onLongClick = onLongClick,
            isDragging = isDragging,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}

@Composable
private fun EmptyFolderPageContent(folder: SubscriptionFolder) {
    val genre = folder.linkedGenre?.trim()?.takeIf { it.isNotEmpty() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (genre != null) "No $genre shows yet" else "No shows in this folder",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (genre != null) "$genre shows you follow will appear here." else "Use Add shows to fill it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
