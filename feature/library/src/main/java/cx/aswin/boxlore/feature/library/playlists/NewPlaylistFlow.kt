package cx.aswin.boxlore.feature.library.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PLAYLIST_NAME_FIELD_TAG
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.PlaylistCover
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.icon.findExactGenreIconKey
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.PLAYLIST_NAME_MAX_LENGTH
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.normalizePlaylistName
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.subscriptions.FolderFlowBottomBar
import cx.aswin.boxlore.feature.library.subscriptions.FolderFlowTopBar
import cx.aswin.boxlore.feature.library.subscriptions.FolderIconGrid
import cx.aswin.boxlore.feature.library.subscriptions.PickableCover
import cx.aswin.boxlore.feature.library.subscriptions.ShowSearchField
import cx.aswin.boxlore.feature.library.subscriptions.resolveSubscriptionGenreItem
import cx.aswin.boxlore.feature.library.subscriptions.subscriptionGenreDisplayName
import cx.aswin.boxlore.feature.library.subscriptions.subscriptionGenreNameRes

/** What the playlist flow picks from: followed shows, Library folders, and what already exists. */
internal data class NewPlaylistFlowData(
    val followed: List<Podcast>,
    val folders: List<SubscriptionFolder> = emptyList(),
    /** Names of the listener's playlists, so a suggestion never repeats one. */
    val existingNames: List<String> = emptyList(),
    /** Shows already in the playlist being added to. */
    val inPlaylist: Set<String> = emptySet(),
    val nowMs: Long = System.currentTimeMillis(),
)

internal data class NewPlaylistFlowActions(
    val onClose: () -> Unit,
    val onCreate: (PlaylistCreateRequest) -> Unit,
    val onAddShows: (playlistId: String, podcastIds: List<String>) -> Unit,
)

/**
 * New playlist (or Add shows), drawn over the playlist pages. Step 1 offers playlists made from the
 * listener's shows (a genre, a folder, recent follows), each with its icon and covers, or picking
 * shows yourself; step 2 is the show grid, narrowed by genre chips and search; step 3 names the
 * playlist, gives it an icon and sets how it plays. A suggestion goes straight to step 3.
 */
@Composable
internal fun NewPlaylistFlow(
    draft: NewPlaylistDraft,
    onDraftChange: (NewPlaylistDraft) -> Unit,
    data: NewPlaylistFlowData,
    actions: NewPlaylistFlowActions,
    modifier: Modifier = Modifier,
) {
    val onBack = { NewPlaylistFlowLogic.back(draft)?.let(onDraftChange) ?: actions.onClose() }
    BackHandler(onBack = onBack)
    val creating = draft.mode == NewPlaylistMode.Create
    TrackScreenView(
        when {
            !creating -> "playlist_add_shows"
            draft.step == NewPlaylistDraft.START -> "new_playlist_start"
            draft.step == NewPlaylistDraft.PICK -> "new_playlist_shows"
            else -> "new_playlist_finish"
        },
    )
    Surface(modifier.fillMaxSize().testTag(NEW_PLAYLIST_FLOW_TAG), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            FolderFlowTopBar(
                title = if (creating) stringResource(PlaylistCopy.NEW_PLAYLIST) else stringResource(R.string.library_add_shows),
                onBack = onBack,
                showClose = !creating || draft.step == NewPlaylistDraft.START,
                step = if (creating) draft.step else null,
                stepCount = draft.stepCount,
            )
            Box(Modifier.weight(1f)) {
                when {
                    creating && draft.step == NewPlaylistDraft.START -> StartStep(draft, onDraftChange, data)
                    creating && draft.step == NewPlaylistDraft.FINISH -> FinishStep(draft, onDraftChange, data)
                    else -> PickStep(draft, onDraftChange, data)
                }
            }
            NewPlaylistBottomBar(draft, onDraftChange, data, actions)
        }
    }
}

@Composable
private fun NewPlaylistBottomBar(
    draft: NewPlaylistDraft,
    onDraftChange: (NewPlaylistDraft) -> Unit,
    data: NewPlaylistFlowData,
    actions: NewPlaylistFlowActions,
) {
    val canGoNext = NewPlaylistFlowLogic.canGoNext(draft)
    val picked = pluralStringResource(R.plurals.library_shows_picked, draft.pickedIds.size, draft.pickedIds.size)
    val genreName = genreNameResolver()
    when {
        draft.mode == NewPlaylistMode.AddShows -> FolderFlowBottomBar(
            primaryLabel = pluralStringResource(R.plurals.library_playlist_add_shows_action, draft.pickedIds.size, draft.pickedIds.size),
            primaryEnabled = canGoNext,
            onPrimary = { draft.playlistId?.let { actions.onAddShows(it, draft.pickedIds) } },
            title = picked,
        )
        draft.step == NewPlaylistDraft.START -> Unit
        draft.step == NewPlaylistDraft.PICK -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_next),
            primaryEnabled = canGoNext,
            onPrimary = { onDraftChange(NewPlaylistFlowLogic.next(draft, data.followed, ::findExactGenreIconKey, genreName)) },
            title = picked,
            subtitle = stringResource(R.string.library_playlist_pick_hint),
        )
        else -> FolderFlowBottomBar(
            primaryLabel = stringResource(R.string.library_playlist_create_action),
            primaryEnabled = canGoNext,
            onPrimary = {
                actions.onCreate(
                    PlaylistCreateRequest(normalizePlaylistName(draft.name), draft.pickedIds, draft.mix, draft.perShowLimit, draft.icon, draft.origin),
                )
            },
            title = picked,
        )
    }
}

/** A genre's name in the listener's language, for names saved outside composition. */
@Composable
private fun genreNameResolver(): (String) -> String {
    val resources = LocalContext.current.resources
    return remember(resources) { { genre -> subscriptionGenreNameRes(genre)?.let(resources::getString) ?: genre } }
}

@Composable
private fun StartStep(draft: NewPlaylistDraft, onDraftChange: (NewPlaylistDraft) -> Unit, data: NewPlaylistFlowData) {
    val genreName = genreNameResolver()
    val recentName = stringResource(R.string.library_playlist_suggestion_recent)
    val suggestions = remember(data, genreName, recentName) {
        PlaylistSuggestions.suggestions(data.followed, data.folders, data.existingNames, data.nowMs, ::findExactGenreIconKey, genreName, recentName)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(SuggestionMinWidth),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "start_title", span = { GridItemSpan(maxLineSpan) }) {
            StepTitle(stringResource(R.string.library_playlist_start_title))
        }
        if (data.followed.isEmpty()) {
            item(key = "no_followed", span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.library_playlist_no_followed), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@LazyVerticalGrid
        }
        if (suggestions.isNotEmpty()) {
            item(key = "suggested", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.library_playlist_start_suggested)) }
            itemsIndexed(suggestions, key = { _, s -> s.key }) { index, suggestion ->
                val name = suggestionName(suggestion, recentName)
                SuggestionCard(
                    suggestion = suggestion,
                    name = name,
                    tone = index % SuggestionTones,
                    onClick = { onDraftChange(NewPlaylistFlowLogic.withSuggestion(draft, suggestion, name)) },
                )
            }
        }
        item(key = "own", span = { GridItemSpan(maxLineSpan) }) {
            PickOwnCard(onClick = { onDraftChange(NewPlaylistFlowLogic.pickOwn(draft)) })
        }
    }
}

@Composable
private fun suggestionName(suggestion: PlaylistSuggestion, recentName: String): String = when (suggestion.kind) {
    PlaylistSuggestion.Kind.GENRE -> subscriptionGenreDisplayName(suggestion.label.orEmpty())
    PlaylistSuggestion.Kind.FOLDER -> suggestion.label.orEmpty()
    PlaylistSuggestion.Kind.RECENT -> recentName
}

/** A suggestion as a solid colour block: its icon on an expressive cookie, the shows' covers, a name and count. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionCard(suggestion: PlaylistSuggestion, name: String, tone: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val icon = GenreIcons.findIcon(suggestion.iconKey) ?: when (suggestion.kind) {
        PlaylistSuggestion.Kind.GENRE -> resolveSubscriptionGenreItem(suggestion.label.orEmpty()).icon
        else -> GenreIcons.defaultFolderIcon()
    }
    val count = PlaylistCopy.showCount(suggestion.podcastIds.size).asString()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("playlist_suggestion_${suggestion.key}"),
    ) {
        Column(Modifier.heightIn(min = 164.dp).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(52.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(content),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = container, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                CoverStack(suggestion.covers, ring = container)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Up to three covers overlapping in a row, each ringed in the card's colour. */
@Composable
private fun CoverStack(covers: List<String>, ring: Color, size: Dp = 30.dp) {
    val shown = covers.take(StackCovers)
    Box(Modifier.size(width = size + (size * 0.6f) * (shown.size - 1).coerceAtLeast(0), height = size)) {
        shown.forEachIndexed { index, url ->
            OptimizedImage(
                url = url,
                contentDescription = null,
                proxyWidth = 96,
                contentScale = ContentScale.Crop,
                showLoadingPlaceholder = false,
                modifier = Modifier
                    .offset(x = size * 0.6f * index)
                    .size(size)
                    .clip(CircleShape)
                    .border(2.dp, ring, CircleShape),
            )
        }
    }
}

@Composable
private fun PickOwnCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("playlist_pick_own"),
    ) {
        Row(Modifier.heightIn(min = 76.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.library_playlist_start_own), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                Text(stringResource(R.string.library_playlist_start_own_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PickStep(draft: NewPlaylistDraft, onDraftChange: (NewPlaylistDraft) -> Unit, data: NewPlaylistFlowData) {
    val inPlaylist = if (draft.mode == NewPlaylistMode.AddShows) data.inPlaylist else emptySet()
    val chips = remember(data.followed, inPlaylist) { PlaylistShowGrid.genreChips(data.followed, inPlaylist) }
    val shown = remember(data.followed, inPlaylist, draft.genreFilter, draft.search) {
        PlaylistShowGrid.visibleShows(data.followed, inPlaylist, draft.genreFilter, draft.search)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CoverMinWidth),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (draft.mode == NewPlaylistMode.Create) {
            item(key = "pick_title", span = { GridItemSpan(maxLineSpan) }) { StepTitle(stringResource(R.string.library_playlist_pick_title)) }
        }
        item(key = "pick_search", span = { GridItemSpan(maxLineSpan) }) {
            ShowSearchField(query = draft.search, onQueryChange = { onDraftChange(draft.copy(search = it)) })
        }
        if (chips.size > 1) {
            item(key = "pick_genres", span = { GridItemSpan(maxLineSpan) }) {
                GenreChips(chips, draft, onDraftChange, shownIds = shown.map { it.id })
            }
        }
        when {
            data.followed.isEmpty() -> item(key = "none", span = { GridItemSpan(maxLineSpan) }) { EmptyLine(stringResource(R.string.library_playlist_no_followed)) }
            shown.isEmpty() && draft.search.isBlank() && draft.genreFilter == null ->
                item(key = "all_added", span = { GridItemSpan(maxLineSpan) }) { EmptyLine(stringResource(R.string.library_playlist_all_added)) }
            else -> items(shown, key = { it.id }) { podcast ->
                PickableCover(
                    podcast = podcast,
                    picked = podcast.id in draft.pickedIds,
                    otherFolder = null,
                    onToggle = { onDraftChange(NewPlaylistFlowLogic.togglePick(draft, podcast.id)) },
                )
            }
        }
    }
}

/** All, then each genre with its count; a chosen genre offers picking (or unpicking) all of its shows. */
@Composable
private fun GenreChips(chips: List<Pair<String, Int>>, draft: NewPlaylistDraft, onDraftChange: (NewPlaylistDraft) -> Unit, shownIds: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item(key = "all") {
                FilterChip(
                    selected = draft.genreFilter == null,
                    onClick = { onDraftChange(NewPlaylistFlowLogic.withGenreFilter(draft, null)) },
                    label = { Text(stringResource(R.string.library_playlist_filter_all)) },
                )
            }
            items(chips, key = { it.first }) { (genre, count) ->
                val item = resolveSubscriptionGenreItem(genre)
                FilterChip(
                    selected = draft.genreFilter == genre,
                    onClick = { onDraftChange(NewPlaylistFlowLogic.withGenreFilter(draft, if (draft.genreFilter == genre) null else genre)) },
                    label = { Text("${subscriptionGenreDisplayName(genre)} · $count") },
                    leadingIcon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        if (draft.genreFilter != null && shownIds.isNotEmpty()) {
            val allPicked = draft.pickedIds.containsAll(shownIds)
            TextButton(onClick = { onDraftChange(NewPlaylistFlowLogic.toggleAll(draft, shownIds)) }) {
                Text(
                    if (allPicked) {
                        stringResource(R.string.library_playlist_unpick_these)
                    } else {
                        pluralStringResource(R.plurals.library_playlist_pick_all, shownIds.size, shownIds.size)
                    },
                )
            }
        }
    }
}

@Composable
private fun FinishStep(draft: NewPlaylistDraft, onDraftChange: (NewPlaylistDraft) -> Unit, data: NewPlaylistFlowData) {
    val covers = remember(data.followed, draft.pickedIds) {
        val byId = data.followed.associateBy { it.id }
        draft.pickedIds.mapNotNull { byId[it]?.imageUrl?.takeIf(String::isNotBlank) }
    }
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = FinishMaxWidth).fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlaylistCover(covers, size = 96.dp, iconKey = draft.icon)
                Column(Modifier.weight(1f)) {
                    Text(
                        normalizePlaylistName(draft.name).ifEmpty { stringResource(PlaylistCopy.NEW_PLAYLIST) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = GoogleSansWeight.bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(PlaylistCopy.showCount(draft.pickedIds.size).asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onDraftChange(NewPlaylistFlowLogic.withName(draft, it.take(PLAYLIST_NAME_MAX_LENGTH), findExactGenreIconKey(it))) },
                label = { Text(stringResource(PlaylistCopy.NAME_LABEL)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag(PLAYLIST_NAME_FIELD_TAG),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.library_folder_icon),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    modifier = Modifier.semantics { heading() },
                )
                FolderIconGrid(selectedKey = draft.icon, onSelect = { onDraftChange(NewPlaylistFlowLogic.withIcon(draft, it)) })
            }
            PlaylistPlaybackSettings(
                mix = draft.mix,
                perShowLimit = draft.perShowLimit,
                onChange = { mix, limit -> onDraftChange(NewPlaylistFlowLogic.withPlayback(draft, mix, limit)) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = GoogleSansWeight.bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
}

private val SuggestionMinWidth = 156.dp
private val CoverMinWidth = 104.dp
private val FinishMaxWidth = 640.dp
private const val SuggestionTones = 3
private const val StackCovers = 3

internal const val NEW_PLAYLIST_FLOW_TAG = "new_playlist_flow"
