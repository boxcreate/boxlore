package cx.aswin.boxlore.feature.library.playlists

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import kotlinx.coroutines.launch

/** A playlist page's wording. */
internal object PlaylistDetailCopy {
    const val NEWEST = "Newest first"
    const val OLDEST = "Oldest first"
    const val PLAY = "Play"
    const val COMING_UP = "Coming up"
    const val SHOWS = "Shows"
    const val ADD_SHOWS = "Add shows"
    const val NOTHING_TO_PLAY = "Nothing to play right now. New episodes from these shows will appear here."
    const val NOT_FOLLOWING = "Not following, skipped"
    const val HIDDEN = "Hidden, skipped"
    const val EMPTY_TITLE = "No shows yet"
    const val EMPTY_BODY = "Add shows you follow here, or use Add to playlist on a show’s page."
    const val DELETE_PLAYLIST = "Delete playlist"
    const val UNDO = "Undo"
    const val UP_NEXT_REPLACED = "Up next replaced"
    const val UNKNOWN_SHOW = "Unknown show"
    const val SHOW_REMOVED = "Show removed"

    /** [show]'s title, or [UNKNOWN_SHOW] when this phone has no details for it (a show restored from a backup). */
    fun title(show: PlaylistShow) = show.title.ifBlank { UNKNOWN_SHOW }
    fun remove(show: PlaylistShow) = "Remove ${title(show)}"
    fun removed(show: PlaylistShow) = if (show.title.isBlank()) SHOW_REMOVED else "Removed ${show.title}"

    /** Why a show's episodes don't play, or null when they do. */
    fun skipped(show: PlaylistShow): String? = when {
        !show.isFollowed -> NOT_FOLLOWING
        show.isHidden -> HIDDEN
        else -> null
    }
}

/** What a playlist page does; [PlaylistDetailScreen] implements it with [PlaylistsViewModel]. */
internal interface PlaylistDetailActions : PlaylistsActions {
    fun play()
    fun setOrder(order: PlaylistOrder)
    fun addShows(podcastIds: List<String>)
    fun removeShow(show: PlaylistShow)
}

/** One playlist on `library/playlists/{playlistId}`. */
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    isPlayerActive: Boolean = false,
) {
    TrackScreenView("playlist_detail")
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val followed by viewModel.followedShows.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val state = rememberPlaylistPagesState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun showUndo(message: String, onUndo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            if (snackbarHostState.showSnackbar(message, PlaylistDetailCopy.UNDO, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    val actions = remember(viewModel, onBack) {
        object : PlaylistDetailActions {
            override fun open(playlistId: String) = Unit
            override fun create(name: String, podcastIds: List<String>) = Unit
            override fun rename(playlistId: String, name: String) = viewModel.rename(playlistId, name)
            override fun delete(playlistId: String) = viewModel.delete(playlistId)
            override fun setOrder(order: PlaylistOrder) = viewModel.setOrder(order)
            override fun addShows(podcastIds: List<String>) = viewModel.addShows(podcastIds)
            override fun play() = viewModel.play(onReplaced = { previous -> showUndo(PlaylistDetailCopy.UP_NEXT_REPLACED) { viewModel.undoPlay(previous) } })
            override fun removeShow(show: PlaylistShow) = viewModel.removeShow(show) { removed ->
                showUndo(PlaylistDetailCopy.removed(removed)) { viewModel.undoRemove(removed) }
            }
        }
    }
    LaunchedEffect(detail) { if (detail == PlaylistDetailState.Missing) onBack() }
    // Back on the page (after playing, or from another screen): Coming up matches what Play would queue.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshComingUp()
        onPauseOrDispose {}
    }
    PlaylistDetailContent(detail, state, actions, onBack, PlaylistPickSource(followed, folders), snackbarHostState, isPlayerActive)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
internal fun PlaylistDetailContent(
    detail: PlaylistDetailState,
    state: PlaylistPagesState,
    actions: PlaylistDetailActions,
    onBack: () -> Unit,
    pickSource: PlaylistPickSource = PlaylistPickSource(),
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
        // Above the mini player and floating navigation, as the list's own bottom padding is.
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = playlistChromeBottom(isPlayerActive))) },
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
                ready.shows.isEmpty() -> Box(Modifier.boxloreCardWidth(BoxloreCardFamily.Utility)) {
                    ExpressiveSolarSystemEmptyState(
                        title = PlaylistDetailCopy.EMPTY_TITLE,
                        description = PlaylistDetailCopy.EMPTY_BODY,
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        actionText = PlaylistDetailCopy.ADD_SHOWS,
                        onExploreClick = { state.openAddShows(ready.playlist.id) },
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize().testTag(PLAYLIST_DETAIL_TAG),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = playlistChromeBottom(isPlayerActive)),
                ) {
                    item(key = "header", contentType = "header") { PlaylistHeader(ready, actions) }
                    comingUp(ready.comingUp)
                    shows(ready, onAdd = { state.openAddShows(ready.playlist.id) }, onRemove = actions::removeShow)
                }
            }
        }
    }
    PlaylistEditorDialogs(
        state = state,
        playlists = listOfNotNull(ready?.playlist),
        actions = actions,
        pickSource = pickSource,
        inPlaylist = ready?.shows?.map { it.podcastId }?.toSet().orEmpty(),
        onAddShows = actions::addShows,
    )
}

@Composable
private fun PlaylistHeader(ready: PlaylistDetailState.Ready, actions: PlaylistDetailActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(PlaylistCopy.showCount(ready.shows.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val orders = listOf(PlaylistOrder.NEWEST to PlaylistDetailCopy.NEWEST, PlaylistOrder.OLDEST to PlaylistDetailCopy.OLDEST)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            orders.forEachIndexed { index, (order, label) ->
                SegmentedButton(
                    selected = ready.playlist.order == order,
                    onClick = { if (ready.playlist.order != order) actions.setOrder(order) },
                    shape = SegmentedButtonDefaults.itemShape(index, orders.size),
                ) { Text(label) }
            }
        }
        Button(onClick = actions::play, enabled = ready.comingUp.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Text(PlaylistDetailCopy.PLAY, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun LazyListScope.comingUp(episodes: List<Episode>) {
    item(key = "coming_up", contentType = "section") { Section(PlaylistDetailCopy.COMING_UP) }
    if (episodes.isEmpty()) {
        item(key = "nothing", contentType = "note") {
            Text(PlaylistDetailCopy.NOTHING_TO_PLAY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
    }
    items(episodes, key = { "episode:${it.id}" }, contentType = { "episode" }) { episode ->
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Artwork(episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl, fallback = episode.podcastImageUrl)
            Column(Modifier.weight(1f)) {
                Text(episode.title, style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(episode.podcastTitle.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun LazyListScope.shows(ready: PlaylistDetailState.Ready, onAdd: () -> Unit, onRemove: (PlaylistShow) -> Unit) {
    item(key = "shows", contentType = "section") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Section(PlaylistDetailCopy.SHOWS) }
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(PlaylistDetailCopy.ADD_SHOWS, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
    items(ready.shows, key = { "show:${it.podcastId}" }, contentType = { "show" }) { show ->
        val skipped = PlaylistDetailCopy.skipped(show)
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.alpha(if (skipped != null) SKIPPED_ALPHA else 1f)) { Artwork(show.imageUrl, fallback = null) }
            Column(Modifier.weight(1f)) {
                Text(PlaylistDetailCopy.title(show), style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                skipped?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = { onRemove(show) }) {
                Icon(Icons.Rounded.Close, contentDescription = PlaylistDetailCopy.remove(show), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Artwork(url: String?, fallback: String?) {
    OptimizedImage(
        url = url,
        fallbackUrls = listOf(fallback),
        proxyWidth = 160,
        contentDescription = null,
        modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Crop,
    )
}

private const val SKIPPED_ALPHA = 0.5f

/** The playback chrome under a playlist page (mini player, floating navigation), which its list and snackbars stay above. */
@Composable
internal fun playlistChromeBottom(isPlayerActive: Boolean) = boxloreContentBottomPadding(if (isPlayerActive) 160.dp else 96.dp)

/** The playlist page's list, for tests. */
internal const val PLAYLIST_DETAIL_TAG = "playlist_detail"
