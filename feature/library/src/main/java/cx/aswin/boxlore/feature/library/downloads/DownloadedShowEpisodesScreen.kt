package cx.aswin.boxlore.feature.library.downloads

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.RemoveDownloadConfirmationDialog
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.downloads.canRetryDownload
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.library.LibraryUiState
import cx.aswin.boxlore.feature.library.LibraryViewModel
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.ShowSortOrder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedShowEpisodesScreen(
    viewModel: LibraryViewModel,
    podcastId: String,
    podcastTitle: String,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    isPlayerActive: Boolean = false
) {
    TrackScreenView("downloaded_show_episodes", podcastId)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads = (uiState as? LibraryUiState.Success)?.downloadedEpisodes ?: emptyList()

    // Retrieve episodes belonging to this show
    val showEpisodes = remember(downloads, podcastId, podcastTitle) {
        downloads.filter {
            (podcastId.isNotEmpty() && it.podcastId == podcastId) ||
                (podcastTitle.isNotEmpty() && it.podcastName == podcastTitle)
        }
    }

    // Auto navigate back if all downloads are removed
    LaunchedEffect(uiState, showEpisodes) {
        if (uiState is LibraryUiState.Success && showEpisodes.isEmpty()) {
            onBack()
        }
    }

    val currentSort by viewModel.showSortOrder.collectAsStateWithLifecycle()
    val sortedEpisodes = remember(showEpisodes, currentSort) {
        when (currentSort) {
            ShowSortOrder.NEWEST -> showEpisodes.sortedByDescending { it.downloadedAt }
            ShowSortOrder.OLDEST -> showEpisodes.sortedBy { it.downloadedAt }
            ShowSortOrder.LARGEST -> showEpisodes.sortedByDescending { it.sizeBytes }
        }
    }

    val summary = remember(showEpisodes) { showEpisodes.downloadSummary() }

    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                1000f
            }
        }
    }
    val density = LocalDensity.current
    val morphThreshold = with(density) { 140.dp.toPx() }
    val scrollFraction = (scrollOffset / morphThreshold).coerceIn(0f, 1f)
    val titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedEpisodeIds = remember { mutableStateListOf<String>() }
    var deleteConfirmSelectedDialog by remember { mutableStateOf(false) }
    var deleteConfirmShowDialog by remember { mutableStateOf(false) }
    var swipeToDeleteEpisode by remember { mutableStateOf<DownloadedEpisodeEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showEpisodes.isNotEmpty()) {
            val firstItem = showEpisodes.first()

            // Blurred Background Header matching PodcastInfoScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeaderHeight + 200.dp)
                    .graphicsLayer {
                        translationY = -scrollOffset * 0.5f
                        alpha = (1f - scrollFraction).coerceIn(0f, 1f)
                    }
            ) {
                OptimizedImage(
            proxyWidth = 240,
            url = firstItem.podcastImageUrl,
                    fallbackUrls = listOf(firstItem.episodeImageUrl),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.18f)
                        .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }
        }

        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .boxloreCardWidth(BoxloreCardFamily.Utility)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = scrollFraction))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedEpisodeIds.clear()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.library_cancel_selection))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pluralStringResource(R.plurals.library_selected_count_title, selectedEpisodeIds.size, selectedEpisodeIds.size),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = GoogleSansWeight.bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (selectedEpisodeIds.size == showEpisodes.size) {
                                selectedEpisodeIds.clear()
                            } else {
                                selectedEpisodeIds.clear()
                                selectedEpisodeIds.addAll(showEpisodes.map { it.episodeId })
                            }
                        }) {
                            Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.library_select_all))
                        }
                        IconButton(
                            onClick = { deleteConfirmSelectedDialog = true },
                            enabled = selectedEpisodeIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.library_delete_selected),
                                tint = if (selectedEpisodeIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.library_back))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = podcastTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = GoogleSansWeight.bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = titleAlpha }
                        )
                        if (showEpisodes.isNotEmpty()) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Rounded.Checklist, contentDescription = stringResource(R.string.library_downloads_select_episodes))
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (showEpisodes.isNotEmpty()) {
                    val firstItem = showEpisodes.first()

                    LazyColumn(
                        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode),
                        state = listState,
                        contentPadding = PaddingValues(bottom = if (isPlayerActive) 150.dp else 80.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compact, Space-Efficient Left-Aligned Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OptimizedImage(
            proxyWidth = 240,
            url = firstItem.podcastImageUrl,
                    fallbackUrls = listOf(firstItem.episodeImageUrl),
                                    contentDescription = podcastTitle,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = podcastTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = GoogleSansWeight.bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = summary.countLine(DownloadSizeWording.Saved, currentLocale()).asString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Play All Pill
                                        Row(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .expressiveClickable(
                                                    feedback = ExpressivePressFeedback.Control,
                                                    shape = RoundedCornerShape(50),
                                                    onClick = {
                                                        // A failed download has no audio, so playing skips it.
                                                        val episodes = showEpisodes.playableDownloads().map { it.toEpisode() }
                                                        if (episodes.isNotEmpty()) viewModel.playQueue(episodes, firstItem.toPodcast())
                                                    }
                                                )
                                                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50))
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.library_play_all),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = GoogleSansWeight.bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }

                                        // Delete All Pill
                                        Row(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .expressiveClickable(
                                                    feedback = ExpressivePressFeedback.Control,
                                                    shape = RoundedCornerShape(50),
                                                    onClick = { deleteConfirmShowDialog = true }
                                                )
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(50))
                                                .background(Color.Transparent)
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(R.string.library_delete_all),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = GoogleSansWeight.bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                        }

                        // Episode List with Swipe to Delete
                        items(items = sortedEpisodes, key = { it.episodeId }) { download ->
                            val isSelected = selectedEpisodeIds.contains(download.episodeId)
                            var showMenu by remember { mutableStateOf(false) }
                            // A download that failed stays listed, offering to download it again instead of playing.
                            val canRetry = download.canRetryDownload()

                            val dismissScope = rememberCoroutineScope()
                            val dismissState = rememberSwipeToDismissBoxState()

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = !isSelectionMode,
                                onDismiss = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        swipeToDeleteEpisode = download
                                    }
                                    // Keep the row; confirm dialog owns deletion (same as former veto).
                                    dismissScope.launch { dismissState.reset() }
                                },
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = stringResource(R.string.library_delete),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .expressiveClickable(
                                                shape = RoundedCornerShape(12.dp),
                                                onLongClickLabel = stringResource(R.string.library_select),
                                                onLongClick = {
                                                    isSelectionMode =
                                                        selectedEpisodeIds.applyLongPressDownloadSelection(
                                                            isSelectionMode,
                                                            download.episodeId,
                                                        )
                                                },
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        if (selectedEpisodeIds.contains(download.episodeId)) {
                                                            selectedEpisodeIds.remove(download.episodeId)
                                                        } else {
                                                            selectedEpisodeIds.add(download.episodeId)
                                                        }
                                                    } else {
                                                        val episode = download.toEpisode()
                                                        val podcast = download.toPodcast()
                                                        viewModel.genericEpisodesClickedCount++
                                                        onEpisodeClick(episode, podcast)
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = {
                                                    if (isSelected) {
                                                        selectedEpisodeIds.remove(download.episodeId)
                                                    } else {
                                                        selectedEpisodeIds.add(download.episodeId)
                                                    }
                                                }
                                            )
                                        }

                                        // Cover art with download status overlay badges
                                        Box(modifier = Modifier.size(46.dp)) {
                                            OptimizedImage(
            proxyWidth = 240,
            url = download.episodeImageUrl,
                                                fallbackUrls = listOf(download.podcastImageUrl),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )

                                            when (download.status) {
                                                DownloadedEpisodeEntity.STATUS_DOWNLOADING -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(18.dp),
                                                            strokeWidth = 2.dp,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                                DownloadedEpisodeEntity.STATUS_QUEUED -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Queue,
                                                            contentDescription = stringResource(R.string.library_downloads_status_queued),
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                                DownloadedEpisodeEntity.STATUS_FAILED -> {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.4f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Info,
                                                            contentDescription = stringResource(R.string.library_downloads_status_failed),
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = download.episodeTitle,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                fontWeight = GoogleSansWeight.semiBold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            val locale = currentLocale()
                                            val relativeDate = formatRelativeDate(download.publishedDate, locale)?.asString()
                                            val durationText = if (download.durationMs > 0) {
                                                LocaleFormats.duration(download.durationMs / 1000 / 60 * 60, locale, LocaleFormats.DurationWidth.NARROW)
                                            } else {
                                                null
                                            }
                                            val sizeText = formatSize(download.sizeBytes, locale).asString()
                                            val subText = listOfNotNull(
                                                relativeDate,
                                                durationText,
                                                sizeText
                                            ).joinToString(" • ")

                                            Text(
                                                text = subText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (canRetry) {
                                                Text(
                                                    text = stringResource(DOWNLOAD_FAILED_LINE),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }

                                            // Linear progress loader under text if actively downloading
                                            if (download.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(2.dp)
                                                        .clip(RoundedCornerShape(100.dp)),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                                )
                                            }
                                        }

                                        if (!isSelectionMode) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (canRetry) {
                                                            viewModel.retryDownload(download.episodeId)
                                                        } else {
                                                            viewModel.playQueue(listOf(download.toEpisode()), download.toPodcast())
                                                        }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (canRetry) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                                                        contentDescription = stringResource(if (canRetry) DOWNLOAD_AGAIN_LABEL else R.string.library_play_episode),
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Box {
                                                    IconButton(
                                                        onClick = { showMenu = true },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.MoreVert,
                                                            contentDescription = stringResource(R.string.library_options),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = showMenu,
                                                        onDismissRequest = { showMenu = false },
                                                        shape = RoundedCornerShape(20.dp),
                                                        offset = DpOffset(x = (-12).dp, y = 4.dp)
                                                    ) {
                                                        if (canRetry) {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(DOWNLOAD_AGAIN_LABEL)) },
                                                                onClick = {
                                                                    showMenu = false
                                                                    viewModel.retryDownload(download.episodeId)
                                                                },
                                                                leadingIcon = {
                                                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                                                }
                                                            )
                                                        } else {
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.library_play)) },
                                                                onClick = {
                                                                    showMenu = false
                                                                    viewModel.playQueue(listOf(download.toEpisode()), download.toPodcast())
                                                                },
                                                                leadingIcon = {
                                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.library_play_next)) },
                                                                onClick = {
                                                                    showMenu = false
                                                                    viewModel.addToQueueNext(download.toEpisode(), download.toPodcast())
                                                                },
                                                                leadingIcon = {
                                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                                }
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text(stringResource(R.string.library_add_to_queue_menu)) },
                                                                onClick = {
                                                                    showMenu = false
                                                                    viewModel.addToQueue(download.toEpisode(), download.toPodcast())
                                                                },
                                                                leadingIcon = {
                                                                    Icon(Icons.Rounded.Queue, contentDescription = null)
                                                                }
                                                            )
                                                        }
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.library_remove_download)) },
                                                            onClick = {
                                                                showMenu = false
                                                                swipeToDeleteEpisode = download
                                                            },
                                                            leadingIcon = {
                                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog for delete confirmation (swipe or 3-dots menu)
        if (swipeToDeleteEpisode != null) {
            val episode = swipeToDeleteEpisode!!
            RemoveDownloadConfirmationDialog(
                episodeTitle = episode.episodeTitle,
                onConfirm = {
                    viewModel.removeDownload(episode.episodeId)
                    swipeToDeleteEpisode = null
                },
                onDismiss = {
                    swipeToDeleteEpisode = null
                },
            )
        }

        // Dialog for deleting selected
        if (deleteConfirmSelectedDialog) {
            AlertDialog(
                onDismissRequest = { deleteConfirmSelectedDialog = false },
                title = { Text(stringResource(R.string.library_downloads_delete_episodes_title)) },
                text = { Text(pluralStringResource(R.plurals.library_downloads_delete_episodes_body, selectedEpisodeIds.size, selectedEpisodeIds.size)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeMultipleDownloads(selectedEpisodeIds.toList())
                        selectedEpisodeIds.clear()
                        isSelectionMode = false
                        deleteConfirmSelectedDialog = false
                    }) {
                        Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error, fontWeight = GoogleSansWeight.bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmSelectedDialog = false }) {
                        Text(stringResource(R.string.library_cancel))
                    }
                }
            )
        }

        // Dialog for deleting whole show
        if (deleteConfirmShowDialog) {
            AlertDialog(
                onDismissRequest = { deleteConfirmShowDialog = false },
                title = { Text(stringResource(R.string.library_downloads_delete_show_title)) },
                text = { Text(stringResource(R.string.library_downloads_delete_show_body, podcastTitle)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeMultipleDownloads(showEpisodes.map { it.episodeId })
                        deleteConfirmShowDialog = false
                    }) {
                        Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error, fontWeight = GoogleSansWeight.bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmShowDialog = false }) {
                        Text(stringResource(R.string.library_cancel))
                    }
                }
            )
        }
    }
}

/** Under a download that failed, which then offers [DOWNLOAD_AGAIN_LABEL] instead of Play. */
@StringRes
internal val DOWNLOAD_FAILED_LINE: Int = R.string.library_downloads_failed_line

@StringRes
internal val DOWNLOAD_AGAIN_LABEL: Int = R.string.library_downloads_download_again
