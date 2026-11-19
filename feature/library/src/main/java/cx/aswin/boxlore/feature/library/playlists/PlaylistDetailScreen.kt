package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistEpisode
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import cx.aswin.boxlore.feature.library.LibraryPlaybackLayout
import cx.aswin.boxlore.feature.library.PlayAllFab
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A playlist page's wording. */
internal object PlaylistDetailCopy {
    const val EMPTY_TITLE = "No episodes yet"
    const val EMPTY_BODY = "Long-press episodes on a show page, or tap Add to playlist on an episode page."
    const val PLAYED = "Played"
    const val GO_TO_EPISODE = "Go to episode"
    const val REMOVE = "Remove from playlist"
    const val DELETE_PLAYLIST = "Delete playlist"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val UNDO = "Undo"
    fun reorder(title: String) = "Reorder $title"
    fun moreFor(title: String) = "More options for $title"
    fun removed(playlistName: String) = "Removed from $playlistName"
}

/** What a playlist page does; [PlaylistDetailScreen] implements it with [PlaylistsViewModel]. */
internal interface PlaylistDetailActions : PlaylistsActions {
    /** Plays from [startItemId] (null: the first unplayed episode) in [order] (the order on screen). */
    fun play(startItemId: Long?, order: List<Long>)
    fun remove(entry: PlaylistEpisode)
    fun saveOrder(order: List<Long>)
    fun openEpisode(episode: Episode)
}

/** One playlist on `library/playlists/{playlistId}`. */
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    isPlayerActive: Boolean = false,
) {
    TrackScreenView("playlist_detail")
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val state = rememberPlaylistPagesState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val name = (detail as? PlaylistDetailState.Ready)?.playlist?.name.orEmpty()
    val latestName by rememberUpdatedState(name)
    val actions = remember(viewModel, onEpisodeClick, onBack) {
        object : PlaylistDetailActions {
            override fun open(playlistId: String) = Unit
            override fun create(name: String) = Unit
            override fun rename(playlistId: String, name: String) = viewModel.rename(playlistId, name)
            override fun delete(playlistId: String) = viewModel.delete(playlistId)
            override fun play(startItemId: Long?, order: List<Long>) = viewModel.play(startItemId, order)
            override fun saveOrder(order: List<Long>) = viewModel.reorder(order)
            override fun openEpisode(episode: Episode) = onEpisodeClick(episode)
            override fun remove(entry: PlaylistEpisode) = viewModel.remove(entry) { removed ->
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(PlaylistDetailCopy.removed(latestName), PlaylistDetailCopy.UNDO, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(removed)
                }
            }
        }
    }
    LaunchedEffect(detail) { if (detail == PlaylistDetailState.Missing) onBack() }
    PlaylistDetailContent(detail, state, actions, onBack, snackbarHostState, isPlayerActive)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun PlaylistDetailContent(
    detail: PlaylistDetailState,
    state: PlaylistPagesState,
    actions: PlaylistDetailActions,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isPlayerActive: Boolean = false,
) {
    val ready = detail as? PlaylistDetailState.Ready
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle = lerp(
        start = MaterialTheme.typography.headlineLarge.copy(fontWeight = GoogleSansWeight.bold),
        stop = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold),
        fraction = scrollBehavior.state.collapsedFraction,
    )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                title = { Text(ready?.playlist?.name.orEmpty(), style = titleStyle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = PlaylistsCopy.BACK) }
                },
                actions = {
                    ready?.playlist?.let { playlist ->
                        PlaylistMenu(playlist.name, { state.openRename(playlist.id, playlist.name) }, { state.openDelete(playlist.id) }, PlaylistDetailCopy.DELETE_PLAYLIST)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                ready == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ready.episodes.isEmpty() -> Box(Modifier.boxloreCardWidth(BoxloreCardFamily.Utility)) {
                    ExpressiveSolarSystemEmptyState(
                        title = PlaylistDetailCopy.EMPTY_TITLE,
                        description = PlaylistDetailCopy.EMPTY_BODY,
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        actionText = null,
                    )
                }
                else -> PlaylistEpisodes(ready, state, actions, bottomPadding = if (isPlayerActive) 230.dp else 160.dp, isPlayerActive = isPlayerActive)
            }
        }
    }
    PlaylistEditorDialogs(state, listOfNotNull(ready?.playlist), actions)
}

@Composable
private fun PlaylistEpisodes(ready: PlaylistDetailState.Ready, state: PlaylistPagesState, actions: PlaylistDetailActions, bottomPadding: Dp, isPlayerActive: Boolean,) {
    val storedIds = ready.episodes.map { it.itemId }
    LaunchedEffect(storedIds) { state.settle(storedIds) }
    val shown = PlaylistPlaybackLogic.applyOrder(ready.episodes, state.pendingOrder)
    val shownIds = shown.map { it.itemId }
    val latestStored by rememberUpdatedState(storedIds)
    val latestActions by rememberUpdatedState(actions)
    // A drag the window change or Back interrupted is saved as it stands, never dropped.
    DisposableEffect(state) {
        onDispose { state.pendingOrder?.takeIf { it != latestStored }?.let(latestActions::saveOrder) }
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? Long
        val toId = to.key as? Long
        if (fromId != null && toId != null) state.move(latestStored, fromId, toId)
    }
    // Saves a drag when it ends, including one a window change cut short.
    LaunchedEffect(reorderState.isAnyItemDragging) {
        if (!reorderState.isAnyItemDragging) state.pendingOrder?.takeIf { it != latestStored }?.let(latestActions::saveOrder)
    }
    LibraryPlaybackLayout(
        modifier = Modifier.fillMaxSize(),
        content = {
            LazyColumn(
                state = listState,
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize().testTag(PLAYLIST_EPISODES_TAG),
                contentPadding = PaddingValues(top = 4.dp, bottom = boxloreContentBottomPadding(bottomPadding)),
            ) {
                item(key = "count", contentType = "count") {
                    Text(
                        PlaylistCopy.episodeCount(ready.episodes.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                itemsIndexed(shown, key = { _, entry -> entry.itemId }, contentType = { _, _ -> "episode" }) { index, entry ->
                    ReorderableItem(reorderState, key = entry.itemId) { isDragging ->
                        PlaylistEpisodeRow(
                            entry = entry,
                            played = entry.episode.id in ready.completedIds,
                            isDragging = isDragging,
                            dragHandle = Modifier.draggableHandle(),
                            onClick = { actions.play(entry.itemId, shownIds) },
                            onOpen = { actions.openEpisode(entry.episode) },
                            onRemove = { actions.remove(entry) },
                            onMove = { offset ->
                                val order = PlaylistPlaybackLogic.nudge(shownIds, index, offset)
                                if (order != shownIds) {
                                    state.pendingOrder = order
                                    actions.saveOrder(order)
                                }
                            },
                            canMoveUp = index > 0,
                            canMoveDown = index < shown.lastIndex,
                        )
                    }
                }
            }
        },
        playbackAction = {
            PlayAllFab(onClick = { actions.play(null, shownIds) }, isPlayerActive = isPlayerActive)
        },
    )
}

@Composable
@Suppress("LongParameterList")
private fun PlaylistEpisodeRow(
    entry: PlaylistEpisode,
    played: Boolean,
    isDragging: Boolean,
    dragHandle: Modifier,
    onClick: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    val episode = entry.episode
    val moves = buildList {
        if (canMoveUp) {
            add(
                CustomAccessibilityAction(PlaylistDetailCopy.MOVE_UP) {
                    onMove(-1)
                    true
                },
            )
        }
        if (canMoveDown) {
            add(
                CustomAccessibilityAction(PlaylistDetailCopy.MOVE_DOWN) {
                    onMove(1)
                    true
                },
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface)
            .expressiveClickable(onClick = onClick)
            .semantics { customActions = moves }
            .heightIn(min = 72.dp)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OptimizedImage(
            url = episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl,
            fallbackUrls = listOf(episode.podcastImageUrl),
            proxyWidth = 160,
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).alpha(if (played) PLAYED_ALPHA else 1f),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).alpha(if (played) PLAYED_ALPHA else 1f)) {
            Text(episode.title, style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(episode.podcastTitle.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (played) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(PlaylistDetailCopy.PLAYED, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Box(dragHandle.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DragHandle, contentDescription = PlaylistDetailCopy.reorder(episode.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EpisodeMenu(episode.title, onOpen, onRemove)
    }
}

@Composable
private fun EpisodeMenu(title: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = PlaylistDetailCopy.moreFor(title)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(PlaylistDetailCopy.GO_TO_EPISODE) }, onClick = {
                expanded = false
                onOpen()
            })
            DropdownMenuItem(text = { Text(PlaylistDetailCopy.REMOVE) }, onClick = {
                expanded = false
                onRemove()
            })
        }
    }
}

private const val PLAYED_ALPHA = 0.6f

/** The playlist page's episode list, for tests. */
internal const val PLAYLIST_EPISODES_TAG = "playlist_episodes"
