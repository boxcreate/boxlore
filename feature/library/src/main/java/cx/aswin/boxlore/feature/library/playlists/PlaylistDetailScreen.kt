package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.PlaylistCover
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import androidx.compose.foundation.lazy.rememberLazyListState
import cx.aswin.boxlore.feature.library.LibraryLargeTitle
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.rememberLibraryHeaderScrollBehavior
import cx.aswin.boxlore.feature.library.rememberLibraryHeaderState
import kotlinx.coroutines.launch

/** A playlist page's wording. */
internal object PlaylistDetailCopy {
    val PLAY: String @Composable get() = stringResource(R.string.library_play)
    val COMING_UP: String @Composable get() = stringResource(R.string.library_playlist_coming_up)
    val SHOWS: String @Composable get() = stringResource(R.string.library_tab_shows)
    val ADD_SHOWS: String @Composable get() = stringResource(R.string.library_add_shows)
    val NOTHING_TO_PLAY: String @Composable get() = stringResource(R.string.library_playlist_nothing_to_play)
    val EMPTY_TITLE: String @Composable get() = stringResource(R.string.library_playlist_empty_title)
    val EMPTY_BODY: String @Composable get() = stringResource(R.string.library_playlist_empty_body)
    val DELETE_PLAYLIST: String @Composable get() = stringResource(R.string.library_playlist_delete)
    val UP_NEXT_REPLACED: UiText = UiText.res(R.string.library_playlist_up_next_replaced)

    /** [show]'s title, or "Unknown show" when this phone has no details for it (a show restored from a backup). */
    @Composable
    fun title(show: PlaylistShow): String = show.title.ifBlank { stringResource(R.string.library_playlist_unknown_show) }

    @Composable
    fun remove(show: PlaylistShow): String = stringResource(R.string.library_playlist_remove_show, title(show))

    fun removed(show: PlaylistShow): UiText =
        if (show.title.isBlank()) UiText.res(R.string.library_playlist_show_removed) else UiText.res(R.string.library_playlist_removed_show, show.title)

    /** Why a show's episodes don't play, or null when they do. */
    @Composable
    fun skipped(show: PlaylistShow): String? = when {
        !show.isFollowed -> stringResource(R.string.library_playlist_not_following_skipped)
        show.isHidden -> stringResource(R.string.library_playlist_hidden_skipped)
        else -> null
    }
}

/** What a playlist page does; [PlaylistDetailScreen] implements it with [PlaylistsViewModel]. */
internal interface PlaylistDetailActions : PlaylistsActions {
    fun play()
    fun setPlayback(mix: PlaylistMix, perShowLimit: Int?)
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
    val context = LocalContext.current
    val resources = LocalResources.current
    fun showUndo(message: UiText, onUndo: () -> Unit) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val undo = resources.getString(R.string.library_undo)
            if (snackbarHostState.showSnackbar(message.resolve(context), undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    val actions = remember(viewModel, onBack) {
        object : PlaylistDetailActions {
            override fun open(playlistId: String) = Unit
            override fun create(request: PlaylistCreateRequest) = Unit
            override fun rename(playlistId: String, name: String) = viewModel.rename(playlistId, name)
            override fun delete(playlistId: String) = viewModel.delete(playlistId)
            override fun setPlayback(mix: PlaylistMix, perShowLimit: Int?) = viewModel.setPlayback(mix, perShowLimit)
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
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val header = rememberLibraryHeaderState()
    val scrollBehavior = rememberLibraryHeaderScrollBehavior { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        // Above the mini player and floating navigation, as the list's own bottom padding is.
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = playlistChromeBottom(isPlayerActive))) },
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                title = {
                    LibraryLargeTitle(
                        title = ready?.playlist?.name.orEmpty(),
                        collapsedFraction = scrollBehavior.state.collapsedFraction,
                        header = header,
                        largeStyle = MaterialTheme.typography.headlineLarge,
                    )
                },
                expandedHeight = header.expandedHeight(),
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
                else -> BoxWithConstraints(Modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize()) {
                    // The list's 16dp sides and the rows' 4dp sides.
                    val columns = playlistShowColumns(maxWidth - 40.dp)
                    val tileSize = playlistShowTileSize(maxWidth - 40.dp, boxloreContentWidthDp())
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag(PLAYLIST_DETAIL_TAG),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = playlistChromeBottom(isPlayerActive)),
                    ) {
                        item(key = "header", contentType = "header") { PlaylistHeader(ready, actions, onOpenSettings = { settingsOpen = true }) }
                        comingUp(ready.comingUp)
                        shows(ready, columns, tileSize, onAdd = { state.openAddShows(ready.playlist.id) }, onRemove = actions::removeShow)
                    }
                }
            }
        }
    }
    if (settingsOpen && ready != null) {
        HowItPlaysSheet(ready.playlist, onChange = actions::setPlayback, onDismiss = { settingsOpen = false })
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

/** The playlist up top: a solid block with its big cover, what it holds and how it plays, then Play and How it plays. */
@Composable
private fun PlaylistHeader(ready: PlaylistDetailState.Ready, actions: PlaylistDetailActions, onOpenSettings: () -> Unit) {
    val playlist = ready.playlist
    val mix = playlistMixCopy(playlist.mix)
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlaylistCover(playlist.coverUrls, size = 112.dp, iconKey = playlist.iconKey, corner = 24.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(PlaylistCopy.showCount(ready.shows.size).asString(), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(mix.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(stringResource(mix.name), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = GoogleSansWeight.medium)
                    }
                    Text(
                        playlist.perShowLimit?.let { pluralStringResource(R.plurals.library_playlist_per_show_summary, it, it) }
                            ?: stringResource(R.string.library_playlist_per_show_summary_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = actions::play, enabled = ready.comingUp.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(PlaylistDetailCopy.PLAY, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 56.dp).testTag(PLAYLIST_HOW_IT_PLAYS_TAG)) {
                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.library_playlist_how_it_plays), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** How it plays, in a sheet over the playlist page; each change saves at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowItPlaysSheet(playlist: Playlist, onChange: (PlaylistMix, Int?) -> Unit, onDismiss: () -> Unit) {
    TrackScreenView("playlist_how_it_plays")
    BoxloreModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // The sheet holds the latest choice itself, so a second change made before the first is saved builds on it.
        var mix by rememberSaveable(playlist.id) { mutableStateOf(playlist.mix) }
        var perShowLimit by rememberSaveable(playlist.id) { mutableStateOf(playlist.perShowLimit) }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            PlaylistPlaybackSettings(mix, perShowLimit, onChange = { newMix, newLimit ->
                mix = newMix
                perShowLimit = newLimit
                onChange(newMix, newLimit)
            })
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
    itemsIndexed(episodes, key = { _, episode -> "episode:${episode.id}" }, contentType = { index, _ -> if (index == 0) "up_next" else "episode" }) { index, episode ->
        if (index == 0) UpNextCard(episode) else ComingUpRow(index + 1, episode)
    }
}

/** The episode Play starts with, as a solid card. */
@Composable
private fun UpNextCard(episode: Episode) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Artwork(episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl, fallback = episode.podcastImageUrl, size = 72.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.library_playlist_up_next), style = MaterialTheme.typography.labelMedium, fontWeight = GoogleSansWeight.semiBold)
                Text(episode.title, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(episodeLine(episode), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A later episode: its place in line, artwork, title, show and length. */
@Composable
private fun ComingUpRow(position: Int, episode: Episode) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            LocaleFormats.number(position.toLong(), currentLocale()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Artwork(episode.imageUrl?.takeIf(String::isNotBlank) ?: episode.podcastImageUrl, fallback = episode.podcastImageUrl)
        Column(Modifier.weight(1f)) {
            Text(episode.title, style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(episodeLine(episode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Show · 45 min", or just the show when the length isn't known. */
@Composable
private fun episodeLine(episode: Episode): String {
    val show = episode.podcastTitle.orEmpty()
    if (episode.duration <= 0) return show
    val length = LocaleFormats.duration(episode.duration.toLong(), currentLocale())
    return if (show.isBlank()) length else "$show · $length"
}

private fun LazyListScope.shows(ready: PlaylistDetailState.Ready, columns: Int, tileSize: Dp, onAdd: () -> Unit, onRemove: (PlaylistShow) -> Unit) {
    item(key = "shows", contentType = "section") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Section(PlaylistDetailCopy.SHOWS) }
            TextButton(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(PlaylistDetailCopy.ADD_SHOWS, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
    // One lazy row of covers per item, so a playlist with many shows only builds the rows on screen.
    items(ready.shows.chunked(columns), key = { row -> "show_row:${row.first().podcastId}" }, contentType = { "show_row" }) { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(ShowTileGap)) {
            row.forEach { show -> key(show.podcastId) { ShowTile(show, tileSize, onRemove = { onRemove(show) }) } }
        }
    }
}

/** A show in the playlist: its cover with a remove button, its title, and why it's skipped when it is. */
@Composable
private fun ShowTile(show: PlaylistShow, size: Dp, onRemove: () -> Unit) {
    val skipped = PlaylistDetailCopy.skipped(show)
    Column(Modifier.width(size), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(size)) {
            Box(Modifier.alpha(if (skipped != null) SKIPPED_ALPHA else 1f)) { Artwork(show.imageUrl, fallback = null, size = size, corner = 20.dp) }
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, contentDescription = PlaylistDetailCopy.remove(show), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(PlaylistDetailCopy.title(show), style = MaterialTheme.typography.bodyMedium, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        skipped?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold, modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Artwork(url: String?, fallback: String?, size: androidx.compose.ui.unit.Dp = 52.dp, corner: androidx.compose.ui.unit.Dp = 14.dp) {
    OptimizedImage(
        url = url,
        fallbackUrls = listOf(fallback),
        proxyWidth = 240,
        contentDescription = null,
        modifier = Modifier.size(size).clip(RoundedCornerShape(corner)),
        contentScale = ContentScale.Crop,
    )
}

private const val SKIPPED_ALPHA = 0.5f
private val ShowTileSize = 100.dp
private val ShowTileGap = 12.dp

/** How many show covers fit across [width] on a playlist's page (at least one). */
internal fun playlistShowColumns(width: Dp): Int = ((width + ShowTileGap) / (ShowTileSize + ShowTileGap)).toInt().coerceAtLeast(1)

/**
 * A show cover's size on a playlist's page: 100dp on phones (windows up to 430dp); on wider
 * windows the covers grow (up to 140dp) to fill the row instead of leaving an empty strip.
 */
internal fun playlistShowTileSize(rowWidth: Dp, windowWidthDp: Float): Dp {
    if (windowWidthDp <= PHONE_MAX_WIDTH_DP) return ShowTileSize
    val columns = playlistShowColumns(rowWidth)
    return ((rowWidth - ShowTileGap * (columns - 1)) / columns).coerceIn(ShowTileSize, ShowTileMaxSize)
}

private const val PHONE_MAX_WIDTH_DP = 430f
private val ShowTileMaxSize = 140.dp

/** The playback chrome under a playlist page (mini player, floating navigation), which its list and snackbars stay above. */
@Composable
internal fun playlistChromeBottom(isPlayerActive: Boolean) = boxloreContentBottomPadding(if (isPlayerActive) 160.dp else 96.dp)

/** The How it plays row, for tests. */
internal const val PLAYLIST_HOW_IT_PLAYS_TAG = "playlist_how_it_plays"

/** The playlist page's list, for tests. */
internal const val PLAYLIST_DETAIL_TAG = "playlist_detail"
