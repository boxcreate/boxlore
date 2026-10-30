package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.icon.findExactGenreIconKey
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast

/** Step 1 of New folder for picked shows and for one genre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickShowsContent(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
    header: @Composable () -> Unit,
) {
    val folderNameByShow = remember(data.folders) {
        buildMap { data.folders.forEach { folder -> folder.podcastIds.forEach { id -> putIfAbsent(id, folder.name) } } }
    }
    val shown = remember(data.podcasts, draft.search) {
        val query = draft.search.trim()
        if (query.isEmpty()) data.podcasts else data.podcasts.filter { it.title.contains(query, ignoreCase = true) }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "pick_header", span = { GridItemSpan(maxLineSpan) }) { header() }
        item(key = "pick_search", span = { GridItemSpan(maxLineSpan) }) {
            ShowSearchField(query = draft.search, onQueryChange = { onDraftChange(draft.copy(search = it)) })
        }
        items(shown, key = { it.id }) { podcast ->
            PickableCover(
                podcast = podcast,
                picked = podcast.id in draft.pickedIds,
                otherFolder = folderNameByShow[podcast.id],
                onToggle = { onDraftChange(NewFolderFlowLogic.togglePick(draft, podcast.id)) },
            )
        }
    }
}

@Composable
private fun PickableCover(
    podcast: Podcast,
    picked: Boolean,
    otherFolder: String?,
    onToggle: () -> Unit,
) {
    val label = buildString {
        append(podcast.title)
        if (otherFolder != null) append(", in $otherFolder")
    }
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        border = if (picked) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = label
                selected = picked
                role = Role.Checkbox
            },
    ) {
        Box {
            PreviewCover(podcast)
            PickMark(picked = picked, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** A tick in a filled circle when picked; an empty ring (on a light scrim over the art) when not. */
@Composable
private fun PickMark(picked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (picked) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f))
            .then(if (picked) Modifier else Modifier.border(2.dp, Color.White, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (picked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Filled search pill, as at the top of Explore. */
@Composable
private fun ShowSearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(text = "Search your shows") },
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OneGenreContent(
    draft: NewFolderDraft,
    onDraftChange: (NewFolderDraft) -> Unit,
    data: NewFolderFlowData,
) {
    val genres = remember(data.podcasts) { extractDistinctGenres(data.podcasts) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.forEach { genre ->
            val item = resolveSubscriptionGenreItem(genre, data.podcasts)
            val count = matchingShows(data.podcasts, genre).size
            SubscriptionChip(
                label = "${item.label} · $count",
                selected = draft.genre.equals(item.label, ignoreCase = true),
                onClick = { onDraftChange(NewFolderFlowLogic.withGenre(draft, item.label, findExactGenreIconKey(item.label))) },
                icon = item.icon,
            )
        }
    }
    val genre = draft.genre ?: return
    GenreJoinsCard(genre = genre, data = data)
    FolderNameField(
        name = draft.name,
        onNameChange = { onDraftChange(NewFolderFlowLogic.withName(draft, it, findExactGenreIconKey(it))) },
        iconKey = draft.icon,
        helper = NewFolderFlowLogic.savedNameNote(draft.name),
    )
}

/** The shows that join now, as overlapping covers with the auto mark and a count. */
@Composable
private fun GenreJoinsCard(genre: String, data: NewFolderFlowData) {
    val joining = remember(data.podcasts, genre) { matchingShows(data.podcasts, genre) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
                joining.take(4).forEach { show ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    ) { PreviewCover(show) }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            AutoFolderMark(size = 18.dp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = showCountLabel(joining.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
    }
}
