package cx.aswin.boxcast.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Queue
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity
import cx.aswin.boxcast.core.model.Episode
import cx.aswin.boxcast.core.model.Podcast
import cx.aswin.boxcast.core.designsystem.theme.expressiveClickable

// Helpers to map DownloadedEpisodeEntity to domain models
fun DownloadedEpisodeEntity.toEpisode() = Episode(
    id = episodeId,
    title = episodeTitle,
    description = episodeDescription ?: "",
    audioUrl = localFilePath,
    imageUrl = episodeImageUrl,
    podcastImageUrl = podcastImageUrl,
    duration = (durationMs / 1000).toInt(),
    publishedDate = publishedDate
)

fun DownloadedEpisodeEntity.toPodcast() = Podcast(
    id = podcastId,
    title = podcastName,
    artist = "",
    imageUrl = podcastImageUrl ?: "",
    description = "",
    genre = ""
)

@Composable
fun DownloadedEpisodesScreen(
    viewModel: LibraryViewModel,
    userPrefs: cx.aswin.boxcast.core.data.UserPreferencesRepository,
    isOffline: Boolean = false,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onPodcastShowClick: (String, String) -> Unit, // Navigation callback
    onSettingsClick: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    isSyncing: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackDownloadsExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        cx.aswin.boxcast.core.data.analytics.AnalyticsHelper.trackLibraryDownloadsViewed("library_hub_card")
    }

    val downloads = (uiState as? LibraryUiState.Success)?.downloadedEpisodes ?: emptyList()

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloads",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isOffline) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Offline Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "No internet connection. Playing downloaded episodes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (uiState) {
                is LibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error loading downloads")
                    }
                }
                is LibraryUiState.Success -> {
                    val groupedDownloads = remember(downloads) {
                        downloads.groupBy { it.podcastId.ifEmpty { it.podcastName } }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Smart Downloads Dashboard Card
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SmartDownloadsDashboardCard(
                                userPrefs = userPrefs,
                                downloads = downloads,
                                onSettingsClick = onSettingsClick,
                                onSyncNow = onSyncNow,
                                isSyncing = isSyncing
                            )
                        }

                        if (downloads.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                val emptyTitle = "Buddha woulda coulda"
                                val emptyDescription = if (isOffline) {
                                    "No downloaded episodes found. Turn on Wi-Fi and charging to auto-sync your smart downloads!"
                                } else {
                                    "No downloaded episodes yet. Turn on Smart Downloads above or find podcasts to download manually."
                                }
                                ExpressiveSolarSystemEmptyState(
                                    title = emptyTitle,
                                    description = emptyDescription,
                                    icon = Icons.Rounded.CloudOff,
                                    actionText = if (isOffline) null else "Find Podcasts",
                                    modifier = Modifier,
                                    onExploreClick = onExploreClick
                                )
                            }
                        } else {
                            // Overall Play All Row at the top of the grid
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .expressiveClickable(
                                                shape = RoundedCornerShape(50),
                                                onClick = {
                                                    val episodes = downloads.map { it.toEpisode() }
                                                    val dummyPodcast = Podcast(
                                                        id = "downloads_all",
                                                        title = "All Downloads",
                                                        artist = "",
                                                        imageUrl = "",
                                                        description = "",
                                                        genre = ""
                                                    )
                                                    viewModel.playQueue(episodes, dummyPodcast)
                                                }
                                            )
                                            .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50))
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Play All Downloads (${downloads.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            items(items = groupedDownloads.keys.toList(), key = { it }) { podcastId ->
                                val podcastDownloads = groupedDownloads[podcastId] ?: emptyList()
                                val firstItem = podcastDownloads.first()
                                
                                PodcastGridShowCard(
                                    title = firstItem.podcastName,
                                    imageUrl = firstItem.podcastImageUrl,
                                    downloadCount = podcastDownloads.size,
                                    onClick = {
                                        onPodcastShowClick(podcastId, firstItem.podcastName)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedShowEpisodesScreen(
    viewModel: LibraryViewModel,
    podcastId: String,
    podcastTitle: String,
    onBack: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit
) {
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

    val listState = rememberLazyListState()
    val scrollOffset by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else 1000f
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (showEpisodes.isNotEmpty()) {
            val firstItem = showEpisodes.first()
            
            // Blurred Background Header matching PodcastInfoScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(collapsedHeaderHeight + 240.dp)
                    .graphicsLayer {
                        translationY = -scrollOffset * 0.5f
                        alpha = (1f - scrollFraction).coerceIn(0f, 1f)
                    }
            ) {
                AsyncImage(
                    model = firstItem.podcastImageUrl,
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
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedEpisodeIds.size} Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
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
                            Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(
                            onClick = { deleteConfirmSelectedDialog = true },
                            enabled = selectedEpisodeIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (selectedEpisodeIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = podcastTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = titleAlpha }
                        )
                        if (showEpisodes.isNotEmpty()) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Rounded.Checklist, contentDescription = "Select episodes")
                            }
                        }
                    }
                }
            },
            containerColor = Color.Transparent // Allow blurred background behind content to shine through
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (showEpisodes.isNotEmpty()) {
                    val firstItem = showEpisodes.first()
                    val totalMins = showEpisodes.sumOf { it.durationMs / 1000 } / 60

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Premium centered hero header (mimics podinfo)
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Large cover artwork
                                Surface(
                                    modifier = Modifier.size(150.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    AsyncImage(
                                        model = firstItem.podcastImageUrl,
                                        contentDescription = podcastTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                // Show info
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = podcastTitle,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Text(
                                        text = "${showEpisodes.size} ${if (showEpisodes.size == 1) "episode" else "episodes"} • ${totalMins}m saved",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Quick actions
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .expressiveClickable(
                                                shape = RoundedCornerShape(50),
                                                onClick = {
                                                    val episodes = showEpisodes.map { it.toEpisode() }
                                                    val podcast = firstItem.toPodcast()
                                                    viewModel.playQueue(episodes, podcast)
                                                }
                                            )
                                            .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Play All", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .expressiveClickable(
                                                shape = RoundedCornerShape(50),
                                                onClick = { deleteConfirmShowDialog = true }
                                            )
                                            .border(1.dp, MaterialTheme.colorScheme.error, shape = RoundedCornerShape(50))
                                            .background(Color.Transparent),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete All", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
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

                        // Episode List
                        items(items = showEpisodes, key = { it.episodeId }) { download ->
                            val isSelected = selectedEpisodeIds.contains(download.episodeId)
                            var showMenu by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .expressiveClickable(
                                        shape = RoundedCornerShape(12.dp),
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
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
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

                                AsyncImage(
                                    model = download.episodeImageUrl ?: download.podcastImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = download.episodeTitle,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    val relativeDate = formatRelativeDate(download.publishedDate)
                                    val durationText = if (download.durationMs > 0) "${(download.durationMs / 1000) / 60}m" else null
                                    val subText = listOfNotNull(relativeDate.takeIf { it.isNotEmpty() }, durationText).joinToString(" • ")
                                    if (subText.isNotEmpty()) {
                                        Text(
                                            text = subText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!isSelectionMode) {
                                    Box {
                                        IconButton(
                                            onClick = { showMenu = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MoreVert,
                                                contentDescription = "Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Play Next") },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.addToQueueNext(download.toEpisode(), download.toPodcast())
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Add to Queue") },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.addToQueue(download.toEpisode(), download.toPodcast())
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Queue, contentDescription = null)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Remove Download") },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.removeDownload(download.episodeId)
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialog for deleting selected
        if (deleteConfirmSelectedDialog) {
            AlertDialog(
                onDismissRequest = { deleteConfirmSelectedDialog = false },
                title = { Text("Delete Downloads?") },
                text = { Text("Remove ${selectedEpisodeIds.size} selected downloaded episodes?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeMultipleDownloads(selectedEpisodeIds.toList())
                        selectedEpisodeIds.clear()
                        isSelectionMode = false
                        deleteConfirmSelectedDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmSelectedDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Dialog for deleting whole show
        if (deleteConfirmShowDialog) {
            AlertDialog(
                onDismissRequest = { deleteConfirmShowDialog = false },
                title = { Text("Delete Show Downloads?") },
                text = { Text("Remove all downloaded episodes for '$podcastTitle'?") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeMultipleDownloads(showEpisodes.map { it.episodeId })
                        deleteConfirmShowDialog = false
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmShowDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PodcastGridShowCard(
    title: String,
    imageUrl: String?,
    downloadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick, shape = RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    )
            )
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (downloadCount == 1) "1 download" else "$downloadCount downloads",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatRelativeDate(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 0 -> "Just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        diff < 2592000 -> "${diff / 604800}w ago"
        diff < 31536000 -> "${diff / 2592000}mo ago"
        else -> "${diff / 31536000}y ago"
    }
}

@Composable
fun SquigglyProgressLoader(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: androidx.compose.ui.unit.Dp = 3.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "squiggly")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val path = Path()

        // Fits ~7 full waves for a tighter, denser squiggly waveform
        val wavelength = width / 7.0f
        // Amplitude is 25% of the height to keep it clean and bounded
        val amplitude = height * 0.25f

        path.moveTo(0f, midY)
        for (x in 0..width.toInt()) {
            val angle = (x.toFloat() / wavelength) * (2f * Math.PI.toFloat()) - phaseShift
            val y = midY + amplitude * kotlin.math.sin(angle)
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun SmartDownloadsDashboardCard(
    userPrefs: cx.aswin.boxcast.core.data.UserPreferencesRepository,
    downloads: List<cx.aswin.boxcast.core.data.database.DownloadedEpisodeEntity>,
    onSettingsClick: () -> Unit,
    onSyncNow: () -> Unit,
    isSyncing: Boolean = false
) {
    val isEnabled by userPrefs.smartDownloadsEnabledStream.collectAsState(initial = false)
    val maxEpisodes by userPrefs.smartDownloadsMaxEpisodesStream.collectAsState(initial = 10)
    val storageBudget by userPrefs.smartDownloadsStorageBudgetStream.collectAsState(initial = 250L)
    val lastSyncTime by userPrefs.smartDownloadsLastSyncTimeStream.collectAsState(initial = 0L)
    
    // Count how many completed smart downloaded episodes we currently have
    val smartDownloadedCount = remember(downloads) {
        downloads.count { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
    }
    
    // Count how many smart downloads are currently in-progress (downloading)
    val smartDownloadingCount = remember(downloads) {
        downloads.count { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
    }
    
    // Calculate total storage used by completed smart downloads in MB
    val smartDownloadedSizeMb = remember(downloads) {
        downloads.filter { it.isSmartDownloaded && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }.sumOf { it.sizeBytes } / (1024 * 1024)
    }

    if (!isEnabled) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .expressiveClickable(
                    shape = RoundedCornerShape(16.dp),
                    onClick = onSettingsClick
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Enable Smart Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        return
    }

    // Active Smart Downloads Dashboard Card (Strictly Material 3 Expressive)
    val subQuota = (maxEpisodes * 0.7).toInt().coerceAtLeast(1)
    val recQuota = (maxEpisodes * 0.2).toInt().coerceAtLeast(1)
    val trendQuota = maxEpisodes - subQuota - recQuota

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Smart Mixtape Sync",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automated offline library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Smart Downloads Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Sync Status & Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (smartDownloadingCount > 0) {
                            "Syncing mixtape candidates…"
                        } else if (isSyncing) {
                            "Checking for updates…"
                        } else {
                            "Offline queue ready"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (smartDownloadingCount > 0 || isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last sync: " + if (lastSyncTime <= 0L) "Never" else {
                            val diff = System.currentTimeMillis() - lastSyncTime
                            when {
                                diff < 60_000 -> "Just now"
                                diff < 3600_000 -> "${diff / 60_000}m ago"
                                diff < 86400_000 -> "${diff / 3600_000}h ago"
                                else -> java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncTime))
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(100.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.expressiveClickable(
                        shape = RoundedCornerShape(100.dp),
                        onClick = onSyncNow,
                        enabled = !isSyncing
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSyncing) "Syncing" else "Sync Now",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Squiggly Wave Progress Loader (Displays during active network requests or sync tasks)
            if (isSyncing || smartDownloadingCount > 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SquigglyProgressLoader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = if (smartDownloadingCount > 0) "$smartDownloadingCount episode(s) downloading in background" else "Syncing with cloud endpoints...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Linear Progress Metrics matching limits & storage budget
            val countProgress = ((smartDownloadedCount + smartDownloadingCount).toFloat() / maxEpisodes.toFloat()).coerceIn(0f, 1f)
            val storageProgress = if (storageBudget <= 0L) 0f else {
                (smartDownloadedSizeMb.toFloat() / storageBudget.toFloat()).coerceIn(0f, 1f)
            }
            
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Count Progress
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mixtape Episodes ($smartDownloadedCount / $maxEpisodes)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(countProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { countProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    )
                }

                // Storage Limits Progress
                if (storageBudget > 0L) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage Budget ($smartDownloadedSizeMb MB / $storageBudget MB)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(storageProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { storageProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(100.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Storage Used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$smartDownloadedSizeMb MB (Unlimited)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
