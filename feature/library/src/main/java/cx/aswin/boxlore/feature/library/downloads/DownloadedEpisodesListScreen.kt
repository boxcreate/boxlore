package cx.aswin.boxlore.feature.library.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreTooltip
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.DownloadsSortOrder
import cx.aswin.boxlore.feature.library.LibraryBlockSkeleton
import cx.aswin.boxlore.feature.library.LibraryEmptyContent
import cx.aswin.boxlore.feature.library.LibraryEmptyState
import cx.aswin.boxlore.feature.library.LibraryLargeTitle
import cx.aswin.boxlore.feature.library.LibraryLoading
import cx.aswin.boxlore.feature.library.LibraryPlaybackLayout
import cx.aswin.boxlore.feature.library.LibraryRowsSkeleton
import cx.aswin.boxlore.feature.library.LibraryUiState
import cx.aswin.boxlore.feature.library.LibraryViewModel
import cx.aswin.boxlore.feature.library.PlayAllFab
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.libraryHeaderScrolledColor
import cx.aswin.boxlore.feature.library.rememberLibraryHeaderScrollBehavior
import cx.aswin.boxlore.feature.library.rememberLibraryHeaderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedEpisodesScreen(
    viewModel: LibraryViewModel,
    userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    isOffline: Boolean = false,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onPodcastShowClick: (String, String) -> Unit, // Navigation callback
    onSettingsClick: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    isSyncing: Boolean = false,
    isPlayerActive: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val header = rememberLibraryHeaderState()
    val listAtTop = { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    val scrollBehavior = rememberLibraryHeaderScrollBehavior(listAtTop)

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

    TrackScreenView("downloads") {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryDownloadsViewed("library_hub_card")
    }

    val downloads = (uiState as? LibraryUiState.Success)?.downloadedEpisodes ?: emptyList()
    val currentSort by viewModel.downloadsSortOrder.collectAsStateWithLifecycle()

    val sortedGroups = remember(downloads, currentSort) {
        val grouped = downloads.groupBy { it.podcastId.ifEmpty { it.podcastName } }
        val groups = grouped.entries.map { entry ->
            val podcastId = entry.key
            val items = entry.value
            val firstItem = items.firstOrNull()
            val podcastName = firstItem?.podcastName ?: ""
            val podcastImageUrl = firstItem?.podcastImageUrl
            val totalSize = items.playableDownloads().sumOf { it.sizeBytes }
            val latestDownloadedAt = items.maxOfOrNull { it.downloadedAt } ?: 0L
            PodcastGroup(
                podcastId = podcastId,
                podcastName = podcastName,
                podcastImageUrl = podcastImageUrl,
                episodes = items,
                totalSizeBytes = totalSize,
                latestDownloadedAt = latestDownloadedAt
            )
        }
        when (currentSort) {
            DownloadsSortOrder.RECENT -> groups.sortedByDescending { it.latestDownloadedAt }
            DownloadsSortOrder.NAME -> groups.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.podcastName })
            DownloadsSortOrder.SIZE -> groups.sortedByDescending { it.totalSizeBytes }
            DownloadsSortOrder.COUNT -> groups.sortedByDescending { it.episodes.size }
        }
    }

    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    // Saved, so select mode never comes back after a restart with nothing selected.
    val selectedPodcastIds = rememberSaveable(saver = selectedIdsSaver()) { mutableStateListOf<String>() }
    val dashboardExpanded = rememberSaveable { mutableStateOf(false) }
    var dashboardHeightPx by remember { mutableIntStateOf(0) }
    val groupIds = remember(sortedGroups) { sortedGroups.map { it.podcastId } }
    LaunchedEffect(uiState is LibraryUiState.Success, groupIds) { selectedPodcastIds.keepListed(uiState is LibraryUiState.Success, groupIds) }
    var deleteConfirmSelectedDialog by rememberSaveable { mutableStateOf(false) }

    val summary = remember(downloads) { downloads.downloadSummary() }

    val listBottomPadding = remember(downloads, isSelectionMode, isPlayerActive) {
        if (downloads.isNotEmpty() && !isSelectionMode) {
            if (isPlayerActive) 240.dp else 160.dp
        } else {
            if (isPlayerActive) 150.dp else 80.dp
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isSelectionMode) {
                    // The same width as the list and its normal header, so Close and Delete don't jump to the window edge.
                    TopAppBar(
                        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                        title = {
                            Text(
                                text = pluralStringResource(R.plurals.library_selected_count_title, selectedPodcastIds.size, selectedPodcastIds.size),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = GoogleSansWeight.bold,
                            )
                        },
                        navigationIcon = {
                            val cancelLabel = stringResource(R.string.library_cancel_selection)
                            BoxloreTooltip(cancelLabel) {
                                IconButton(onClick = {
                                    isSelectionMode = false
                                    selectedPodcastIds.clear()
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = cancelLabel)
                                }
                            }
                        },
                        actions = {
                            val selectAllLabel = stringResource(R.string.library_select_all)
                            BoxloreTooltip(selectAllLabel) {
                                IconButton(onClick = {
                                    if (selectedPodcastIds.coversAll(groupIds)) {
                                        selectedPodcastIds.clear()
                                    } else {
                                        selectedPodcastIds.clear()
                                        selectedPodcastIds.addAll(sortedGroups.map { it.podcastId })
                                    }
                                }) {
                                    Icon(Icons.Rounded.SelectAll, contentDescription = selectAllLabel)
                                }
                            }
                            val deleteLabel = stringResource(R.string.library_delete_selected)
                            BoxloreTooltip(deleteLabel) {
                            IconButton(
                                onClick = { deleteConfirmSelectedDialog = true },
                                enabled = selectedPodcastIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = deleteLabel,
                                    tint = if (selectedPodcastIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    }
                                )
                            }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = libraryHeaderScrolledColor(listAtTop),
                        ),
                    )
                } else {
                    LargeTopAppBar(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                        title = {
                            LibraryLargeTitle(
                                title = stringResource(R.string.library_hub_downloads),
                                collapsedFraction = scrollBehavior.state.collapsedFraction,
                                header = header,
                                subtitle = if (downloads.isNotEmpty()) summary.countLine(DownloadSizeWording.Used, currentLocale()).asString() else null,
                            )
                        },
                        expandedHeight = header.expandedHeight(),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.library_back))
                            }
                        },
                        actions = {
                            if (sortedGroups.isNotEmpty()) {
                                IconButton(onClick = { isSelectionMode = true }) {
                                    Icon(Icons.Rounded.Checklist, contentDescription = stringResource(R.string.library_downloads_select_podcasts))
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = libraryHeaderScrolledColor(listAtTop),
                        ),
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
                                    text = stringResource(R.string.library_downloads_offline_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = GoogleSansWeight.bold
                                )
                                Text(
                                    text = stringResource(R.string.library_downloads_offline_body),
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
      CompositionLocalProvider(LocalSmartDownloadsExpanded provides dashboardExpanded) {
        LibraryPlaybackLayout(modifier = Modifier.padding(innerPadding).boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize(), content = {
            when (uiState) {
                is LibraryUiState.Loading -> LibraryLoading {
                    LibraryRowsSkeleton(artwork = 56.dp, rows = 3, leading = { LibraryBlockSkeleton(height = 80.dp) })
                }
                is LibraryUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.library_downloads_error_loading))
                    }
                }
                is LibraryUiState.Success -> if (downloads.isEmpty()) {
                    // Smart Downloads, then the empty page centred in the space under it. The whole page
                    // scrolls, so an open dashboard taller than a short window stays reachable.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    val room = maxHeight - with(LocalDensity.current) { dashboardHeightPx.toDp() } - EmptyPageTopPadding
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .boxloreCardWidth(BoxloreCardFamily.Episode).padding(start = 16.dp, end = 16.dp, top = EmptyPageTopPadding)
                            .testTag(DOWNLOADS_EMPTY_PAGE_TAG),
                    ) {
                        Box(Modifier.onSizeChanged { dashboardHeightPx = it.height }) {
                        SmartDownloadsDashboardCard(
                            userPrefs = userPrefs,
                            downloads = downloads,
                            onSettingsClick = onSettingsClick,
                            onSyncNow = onSyncNow,
                            isSyncing = isSyncing
                        )
                        }
                        LibraryEmptyState(
                            content = LibraryEmptyContent(
                                title = stringResource(R.string.library_downloads_empty_title),
                                body = stringResource(if (isOffline) R.string.library_downloads_empty_offline_body else R.string.library_downloads_empty_body),
                                icon = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.DownloadDone,
                                shape = ExpressiveShapes.Hexagon,
                                actionText = if (isOffline) null else stringResource(R.string.library_find_podcasts),
                                secondaryAction = true,
                            ),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            onAction = onExploreClick,
                            // The space under the dashboard, and never less than the empty page needs to show.
                            modifier = Modifier.height(maxOf(room, EmptyPageMinHeight)),
                        )
                    }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = if (LocalBoxloreWindowLayout.current.usesTabletLayout) 24.dp else listBottomPadding, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize()
                    ) {
                        // Smart Downloads Dashboard Card
                        item {
                            SmartDownloadsDashboardCard(
                                userPrefs = userPrefs,
                                downloads = downloads,
                                onSettingsClick = onSettingsClick,
                                onSyncNow = onSyncNow,
                                isSyncing = isSyncing
                            )
                        }

                        items(items = sortedGroups, key = { it.podcastId }) { group ->
                            val isSelected = group.podcastId in selectedPodcastIds
                            PodcastListShowCard(
                                group = group,
                                onClick = {
                                    onPodcastShowClick(group.podcastId, group.podcastName)
                                },
                                onPlayClick = {
                                    // A failed download has no audio, so playing skips it.
                                    val playable = group.episodes.playableDownloads()
                                    if (playable.isNotEmpty()) {
                                        viewModel.playQueue(playable.map { it.toEpisode() }, playable.first().toPodcast())
                                    }
                                },
                                selection =
                                DownloadShowCardSelection(
                                    active = isSelectionMode,
                                    selected = isSelected,
                                    onSelectedChange = { selected ->
                                        if (selected) {
                                            selectedPodcastIds.add(group.podcastId)
                                        } else {
                                            selectedPodcastIds.remove(group.podcastId)
                                        }
                                    },
                                    onLongClick = {
                                        isSelectionMode =
                                            selectedPodcastIds.applyLongPressDownloadSelection(
                                                isSelectionMode,
                                                group.podcastId,
                                            )
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }, playbackAction = {
            // Play All FAB aligned to bottom right
            if (downloads.isNotEmpty() && !isSelectionMode) {
                val allDownloadsTitle = stringResource(R.string.library_downloads_all)
                PlayAllFab(
                    isPlayerActive = isPlayerActive,
                    onClick = {
                        val episodes = downloads.playableDownloads().map { it.toEpisode() }
                        val dummyPodcast = Podcast(
                            id = "downloads_all",
                            title = allDownloadsTitle,
                            artist = "",
                            imageUrl = "",
                            description = "",
                            genre = ""
                        )
                        if (episodes.isNotEmpty()) viewModel.playQueue(episodes, dummyPodcast)
                    }
                )
            }
        })
      }
        // At screen level, so the question stays open when Play All moves between its tablet row and the list.
        if (deleteConfirmSelectedDialog) {
            BoxloreAlertDialog(
                onDismissRequest = { deleteConfirmSelectedDialog = false },
                title = { Text(stringResource(R.string.library_downloads_delete_title)) },
                text = {
                    val selectedCount = selectedPodcastIds.size
                    Text(pluralStringResource(R.plurals.library_downloads_delete_podcasts_body, selectedCount, selectedCount))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val epsToDelete = sortedGroups
                                .filter { it.podcastId in selectedPodcastIds }
                                .flatMap { it.episodes }
                                .map { it.episodeId }
                            viewModel.removeMultipleDownloads(epsToDelete)
                            isSelectionMode = false
                            selectedPodcastIds.clear()
                            deleteConfirmSelectedDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.library_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmSelectedDialog = false }) {
                        Text(stringResource(R.string.library_cancel))
                    }
                }
            )
        }
    }
}

/** The empty page's gap above Smart Downloads, and the least height the empty page gets under it. */
private val EmptyPageTopPadding = 8.dp
private val EmptyPageMinHeight = 240.dp

/** Test tag on the empty Downloads page's scrolling column. */
internal const val DOWNLOADS_EMPTY_PAGE_TAG = "downloads_empty_page"
