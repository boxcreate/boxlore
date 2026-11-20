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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.PlaylistCover
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.ExpressiveSolarSystemEmptyState
import cx.aswin.boxlore.feature.library.R

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

    /** A card's second line: its show count and how it plays. */
    @Composable
    fun summary(playlist: Playlist): String =
        stringResource(
            R.string.library_playlist_summary_mix,
            PlaylistCopy.showCount(playlist.showCount).asString(),
            stringResource(playlistMixCopy(playlist.mix).name),
        )
}

/** What the Playlists page and its dialogs do; [PlaylistsScreen] implements it with [PlaylistsViewModel]. */
internal interface PlaylistsActions {
    fun open(playlistId: String)
    fun create(request: PlaylistCreateRequest)
    fun rename(playlistId: String, name: String)
    fun delete(playlistId: String)
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
    val actions = remember(viewModel, onOpenPlaylist) {
        object : PlaylistsActions {
            override fun open(playlistId: String) = onOpenPlaylist(playlistId)
            override fun create(request: PlaylistCreateRequest) =
                viewModel.create(request.name, request.podcastIds, request.mix, request.perShowLimit, request.iconKey, request.origin) { onOpenPlaylist(it.id) }
            override fun rename(playlistId: String, name: String) = viewModel.rename(playlistId, name)
            override fun delete(playlistId: String) = viewModel.delete(playlistId)
        }
    }
    PlaylistsContent(playlists, state, actions, onBack, PlaylistPickSource(followed, folders), bottomPadding = if (isPlayerActive) 160.dp else 96.dp)
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
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleStyle = lerp(
        start = MaterialTheme.typography.displayMedium.copy(fontWeight = GoogleSansWeight.bold),
        stop = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold),
        fraction = scrollBehavior.state.collapsedFraction,
    )
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                title = { Text(PlaylistsCopy.TITLE, style = titleStyle) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = PlaylistsCopy.BACK) }
                },
                actions = {
                    IconButton(onClick = state::openCreate) { Icon(Icons.Rounded.Add, contentDescription = stringResource(PlaylistCopy.NEW_PLAYLIST)) }
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
                playlists == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                playlists.isEmpty() -> Box(Modifier.boxloreCardWidth(BoxloreCardFamily.Utility)) {
                    ExpressiveSolarSystemEmptyState(
                        title = PlaylistsCopy.EMPTY_TITLE,
                        description = PlaylistsCopy.EMPTY_BODY,
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        actionText = stringResource(PlaylistCopy.NEW_PLAYLIST),
                        onExploreClick = state::openCreate,
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = boxloreContentBottomPadding(bottomPadding)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { actions.open(playlist.id) },
                            onRename = { state.openRename(playlist.id, playlist.name) },
                            onDelete = { state.openDelete(playlist.id) },
                        )
                    }
                }
            }
        }
    }
    PlaylistEditorDialogs(state, playlists.orEmpty(), actions, pickSource)
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlaylistCover(playlist.coverUrls, size = 64.dp, iconKey = playlist.iconKey)
            Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(PlaylistsCopy.summary(playlist), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PlaylistMenu(playlist.name, onRename, onDelete)
        }
    }
}

@Composable
internal fun PlaylistMenu(name: String, onRename: () -> Unit, onDelete: () -> Unit, deleteLabel: String = PlaylistsCopy.DELETE) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = PlaylistsCopy.moreFor(name)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(PlaylistsCopy.RENAME) }, onClick = {
                expanded = false
                onRename()
            })
            DropdownMenuItem(text = { Text(deleteLabel) }, onClick = {
                expanded = false
                onDelete()
            })
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
