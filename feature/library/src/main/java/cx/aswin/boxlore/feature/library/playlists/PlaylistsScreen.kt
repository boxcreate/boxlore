package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.BoxloreMenuState
import cx.aswin.boxlore.core.designsystem.components.BoxloreSnackbarHost
import cx.aswin.boxlore.core.designsystem.components.BoxloreTooltip
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.PlaylistCover
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.boxloreCombinedClickable
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.text.resolve
import cx.aswin.boxlore.feature.library.LibraryBlockSkeleton
import cx.aswin.boxlore.feature.library.LibraryEmptyContent
import cx.aswin.boxlore.feature.library.LibraryEmptyState
import cx.aswin.boxlore.feature.library.LibraryGridSkeleton
import cx.aswin.boxlore.feature.library.LibraryLargeTitle
import cx.aswin.boxlore.feature.library.LibraryLoading
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.libraryGridWidthDp
import cx.aswin.boxlore.feature.library.libraryHeaderScrolledColor
import cx.aswin.boxlore.feature.library.playlistsCardContainer
import cx.aswin.boxlore.feature.library.rememberLibraryHeaderScrollBehavior
import cx.aswin.boxlore.feature.library.rememberLibraryHeaderState
import kotlinx.coroutines.launch

/** The Playlists page's wording, from the module's resources. */
internal object PlaylistsCopy {
    val TITLE: String @Composable get() = stringResource(R.string.library_hub_playlists)
    val EMPTY_TITLE: String @Composable get() = stringResource(R.string.library_playlists_empty_title)
    val EMPTY_BODY: String @Composable get() = stringResource(R.string.library_playlists_empty_body)
    val RENAME: String @Composable get() = stringResource(R.string.library_rename)
    val DELETE: String @Composable get() = stringResource(R.string.library_delete)
    val BACK: String @Composable get() = stringResource(R.string.library_back)

    @Composable
    fun moreFor(name: String): String = stringResource(R.string.library_more_options_for, name)

    /** A card's second line: its show count (how it plays is on the playlist's page). */
    @Composable
    fun summary(playlist: Playlist): String = PlaylistCopy.showCount(playlist.showCount).asString()
}

/** What the Playlists page and its dialogs do; [PlaylistsScreen] implements it with [PlaylistsViewModel]. */
internal interface PlaylistsActions {
    fun open(playlistId: String)
    fun create(request: PlaylistCreateRequest)
    fun rename(playlistId: String, name: String)
    fun delete(playlistId: String)

    /** Plays a playlist straight from its card. */
    fun play(playlistId: String) = Unit
}

/** A new playlist as the flow made it; [origin] is where its shows came from (analytics). */
internal data class PlaylistCreateRequest(
    val name: String,
    val podcastIds: List<String>,
    val mix: PlaylistMix,
    val perShowLimit: Int?,
    val iconKey: String?,
    val origin: String,
)

/** The shows a playlist can be made from: followed shows and Library folders. */
internal data class PlaylistPickSource(val followed: List<Podcast> = emptyList(), val folders: List<SubscriptionFolder> = emptyList())

/** Library › Playlists on `library/playlists`. */
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    isPlayerActive: Boolean = false,
) {
    TrackScreenView("playlists")
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val followed by viewModel.followedShows.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val state = rememberPlaylistPagesState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    fun say(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            if (snackbarHostState.showSnackbar(message, actionLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onAction()
        }
    }
    val actions = remember(viewModel, onOpenPlaylist) {
        object : PlaylistsActions {
            override fun open(playlistId: String) = onOpenPlaylist(playlistId)
            override fun play(playlistId: String) = viewModel.play(
                target = playlistId,
                onReplaced = { previous ->
                    say(PlaylistDetailCopy.UP_NEXT_REPLACED.resolve(context), resources.getString(R.string.library_undo)) { viewModel.undoPlay(previous) }
                },
                onNothingToPlay = { say(resources.getString(R.string.library_playlist_nothing_to_play)) },
            )
            override fun create(request: PlaylistCreateRequest) =
                viewModel.create(request.name, request.podcastIds, request.mix, request.perShowLimit, request.iconKey, request.origin) { onOpenPlaylist(it.id) }
            override fun rename(playlistId: String, name: String) = viewModel.rename(playlistId, name)
            override fun delete(playlistId: String) = viewModel.delete(playlistId)
        }
    }
    PlaylistsContent(
        playlists,
        state,
        actions,
        onBack,
        PlaylistPickSource(followed, folders),
        bottomPadding = if (isPlayerActive) 160.dp else 96.dp,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistsContent(
    playlists: List<Playlist>?,
    state: PlaylistPagesState,
    actions: PlaylistsActions,
    onBack: () -> Unit,
    pickSource: PlaylistPickSource = PlaylistPickSource(),
    bottomPadding: androidx.compose.ui.unit.Dp = 96.dp,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val gridState = rememberLazyGridState()
    val wideGrid = isPlaylistWideGrid((libraryGridWidthDp() - PlaylistGridSidePadding).dp)
    val header = rememberLibraryHeaderState()
    val listAtTop = { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    val scrollBehavior = rememberLibraryHeaderScrollBehavior(listAtTop)
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                LargeTopAppBar(
                    modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Grid),
                    title = { LibraryLargeTitle(PlaylistsCopy.TITLE, scrollBehavior.state.collapsedFraction, header) },
                    expandedHeight = header.expandedHeight(),
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = PlaylistsCopy.BACK) }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = libraryHeaderScrolledColor(listAtTop),
                    ),
                )
            },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                when {
                    playlists == null -> LibraryLoading {
                        LibraryGridSkeleton(columns = PLAYLIST_SKELETON_COLUMNS, top = { LibraryBlockSkeleton(height = 56.dp, corner = 28.dp) })
                    }
                    playlists.isEmpty() -> LibraryEmptyState(
                        content = LibraryEmptyContent(
                            title = PlaylistsCopy.EMPTY_TITLE,
                            body = PlaylistsCopy.EMPTY_BODY,
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            shape = ExpressiveShapes.Cookie4,
                            actionText = stringResource(PlaylistCopy.NEW_PLAYLIST),
                        ),
                        containerColor = MaterialTheme.colorScheme.playlistsCardContainer(),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onAction = state::openCreate,
                        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                        bottomPadding = boxloreContentBottomPadding(bottomPadding),
                    )
                    else -> LazyVerticalGrid(
                        columns = PlaylistGridCells,
                        state = gridState,
                        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Grid).fillMaxSize().testTag(PLAYLISTS_GRID_TAG),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = boxloreContentBottomPadding(bottomPadding)),
                        horizontalArrangement = Arrangement.spacedBy(PlaylistGridSpacing),
                        verticalArrangement = Arrangement.spacedBy(PlaylistGridSpacing),
                    ) {
                        // Wide grids show New playlist as the first card, the size of a playlist; narrower ones keep the bar.
                    if (wideGrid) {
                        item(key = "new_playlist") { NewPlaylistCell(onClick = state::openCreate) }
                    } else {
                        item(key = "new_playlist", span = { GridItemSpan(maxLineSpan) }) { NewPlaylistButton(onClick = state::openCreate) }
                    }
                        items(playlists, key = { it.id }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { actions.open(playlist.id) },
                                onPlay = { actions.play(playlist.id) },
                                onRename = { state.openRename(playlist.id, playlist.name) },
                                onDelete = { state.openDelete(playlist.id) },
                            )
                        }
                    }
                }
            }
        }
        // boxlore's notice, just above the bar and mini player.
        BoxloreSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    PlaylistEditorDialogs(state, playlists.orEmpty(), actions, pickSource)
}

/** A playlist as a small card, three across on a phone: its cover with a Play button on it, then its name and show count. */
@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onPlay: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    // Keyed by playlist, so the open menu stays open when the screen is recreated.
    val menu = rememberBoxloreMenuState(key = playlist.id)
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        // Tap opens the playlist; long-press (or right-click) opens its menu, like folders.
        modifier = Modifier.fillMaxWidth().clip(shape).boxloreCombinedClickable(
            onLongClickLabel = PlaylistsCopy.moreFor(playlist.name),
            onLongClick = menu::open,
            onClick = onClick,
        ),
    ) {
        Column(Modifier.padding(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
                PlaylistCover(playlist.coverUrls, size = maxWidth, iconKey = playlist.iconKey, corner = 14.dp)
                FilledIconButton(
                    onClick = onPlay,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(34.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.library_playlist_play_named, playlist.name), modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = CardTextHeight).padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        PlaylistsCopy.summary(playlist),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlaylistMenu(playlist.name, onRename, onDelete, compact = true, state = menu)
            }
        }
    }
}

/**
 * New playlist as the grid's first card on wide grids: the size of a playlist card, with a medallion
 * in the playlists' cookie shape over the label.
 */
@Composable
private fun NewPlaylistCell(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag(NEW_PLAYLIST_TILE_TAG),
    ) {
        Column(Modifier.padding(6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(56.dp).clip(ExpressiveShapes.Cookie4).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(
                stringResource(PlaylistCopy.NEW_PLAYLIST),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = CardTextHeight).padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

/** New playlist, as one compact tonal button across the top of the grid. */
@Composable
private fun NewPlaylistButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(NEW_PLAYLIST_TILE_TAG),
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(PlaylistCopy.NEW_PLAYLIST), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * A playlist's menu: Rename, and Delete in red in a group of its own. It opens beside its button in
 * the half of a folded screen that holds it, and [state] (keyed by playlist) keeps it open across a
 * restart of the screen.
 */
@Composable
internal fun PlaylistMenu(
    name: String,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String = PlaylistsCopy.DELETE,
    compact: Boolean = false,
    state: BoxloreMenuState = rememberBoxloreMenuState(key = name),
) {
    val moreLabel = PlaylistsCopy.moreFor(name)
    val rename = PlaylistsCopy.RENAME
    Box {
        BoxloreTooltip(moreLabel) {
            // The compact button keeps a 48dp touch target; only the drawn size shrinks.
            IconButton(onClick = state::open, modifier = if (compact) Modifier.size(28.dp) else Modifier) {
                Icon(Icons.Rounded.MoreVert, contentDescription = moreLabel, modifier = if (compact) Modifier.size(18.dp) else Modifier)
            }
        }
        BoxloreDropdownMenu(state) {
            item(rename, onRename, Icons.Rounded.Edit)
            item(deleteLabel, onDelete, Icons.Rounded.DeleteOutline, destructive = true)
        }
    }
}

/**
 * What [state] has open: New playlist and Add shows ([NewPlaylistFlow], over the page, with
 * [pickSource] and the shows already in the playlist, [inPlaylist]), Rename playlist or Delete
 * playlist. Add shows goes to [onAddShows].
 */
@Composable
internal fun PlaylistEditorDialogs(
    state: PlaylistPagesState,
    playlists: List<Playlist>,
    actions: PlaylistsActions,
    pickSource: PlaylistPickSource,
    inPlaylist: Set<String> = emptySet(),
    onAddShows: (List<String>) -> Unit = {},
) {
    state.newDraft?.let { draft ->
        // Over the whole window like New folder: full screen on phones, a framed panel on tablets.
        cx.aswin.boxlore.feature.library.subscriptions.EditorDialog(
            onBack = { NewPlaylistFlowLogic.back(draft)?.let { state.newDraft = it } ?: state.closeFlow() },
            dismissOnClickOutside = false,
        ) {
            NewPlaylistFlow(
            draft = draft,
            onDraftChange = { state.newDraft = it },
            data = NewPlaylistFlowData(
                followed = pickSource.followed,
                folders = pickSource.folders,
                existingNames = playlists.map { it.name },
                inPlaylist = inPlaylist,
            ),
            actions = NewPlaylistFlowActions(
                onClose = state::closeFlow,
                onCreate = { request ->
                    state.closeFlow()
                    actions.create(request)
                },
                onAddShows = { _, ids ->
                    state.closeFlow()
                    onAddShows(ids)
                },
            ),
        )
        }
    }
    when (val editor = state.editor) {
        null -> Unit
        is PlaylistEditor.Rename -> cx.aswin.boxlore.core.designsystem.components.PlaylistNameDialog(
            title = stringResource(PlaylistCopy.RENAME_PLAYLIST),
            name = state.nameDraft,
            onNameChange = { state.nameDraft = it },
            confirmLabel = stringResource(PlaylistCopy.SAVE),
            onConfirm = { name ->
                state.closeEditor()
                actions.rename(editor.playlistId, name)
            },
            onDismissRequest = state::closeEditor,
        )
        is PlaylistEditor.Delete -> {
            val name = playlists.firstOrNull { it.id == editor.playlistId }?.name
            if (name == null) {
                // The playlist went while the dialog was open (or before recreation).
                androidx.compose.runtime.LaunchedEffect(editor) { if (playlists.isNotEmpty()) state.closeEditor() }
            } else {
                cx.aswin.boxlore.core.designsystem.components.DeletePlaylistDialog(
                    name = name,
                    onConfirm = {
                        state.closeEditor()
                        actions.delete(editor.playlistId)
                    },
                    onDismissRequest = state::closeEditor,
                )
            }
        }
    }
}

/**
 * The Playlists grid's columns: three across on every phone, the Galaxy Z Fold 8 cover and split
 * halves (cards grow with the window), and roomier cards once the grid is tablet-wide (tablets,
 * unfolded foldables, phones in landscape), so cards never shrink to stamps as the window grows.
 */
internal object PlaylistGridCells : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val cells = if (isPlaylistWideGrid(availableSize.toDp())) GridCells.Adaptive(PlaylistCardWideMinWidth) else GridCells.Fixed(PHONE_COLUMNS)
        return with(cells) { calculateCrossAxisCellSizes(availableSize, spacing) }
    }
}

/** From this grid width Playlists uses wide cards, and New playlist becomes the grid's first card. */
internal fun isPlaylistWideGrid(gridWidth: Dp): Boolean = gridWidth >= PlaylistWideGrid

private const val PHONE_COLUMNS = 3
private const val PLAYLIST_SKELETON_COLUMNS = 3
private val PlaylistCardWideMinWidth = 152.dp
private val PlaylistWideGrid = 568.dp

/** The Playlists grid's 16dp sides, which come off its width before cards are counted. */
private const val PlaylistGridSidePadding = 32f
internal val PlaylistGridSpacing = 10.dp

/** The name and summary area under a card's cover, so cards in a row line up. */
private val CardTextHeight = 62.dp

/** The Playlists grid and its New playlist button, for tests. */
internal const val PLAYLISTS_GRID_TAG = "playlists_grid"
internal const val NEW_PLAYLIST_TILE_TAG = "new_playlist_tile"
