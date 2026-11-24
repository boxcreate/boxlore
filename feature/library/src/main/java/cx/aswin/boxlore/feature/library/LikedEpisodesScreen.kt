package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.feature.library.subscriptions.EpisodeRowArtwork
import cx.aswin.boxlore.feature.library.subscriptions.LatestEpisodePlayButton

/**
 * Liked episodes: how many there are (and the time left when lengths are known), Play all, and each
 * episode in the shared Library row with its own play button. Tapping a row opens the episode, as
 * before. Play all plays the unfinished liked episodes in order through [onPlayEpisodes], the same
 * multi-show path as New Episodes' Play All.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedEpisodesScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onExploreClick: () -> Unit,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)? = null,
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
                viewModel.trackLikedExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    TrackScreenView("liked_episodes") {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryLikedViewed("library_hub_card")
    }

    val likedRows = (uiState as? LibraryUiState.Success)?.likedEpisodes.orEmpty()
    // Backend feeds can repeat an episode; count, play and list each one once.
    val liked = remember(likedRows) { LazyListKeyPolicy.deduplicateById(likedRows) { it.episodeId } }
    val countLine = likedCountLine(liked)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                title = {
                    LibraryLargeTitle(
                        title = stringResource(R.string.library_hub_liked_episodes),
                        collapsedFraction = scrollBehavior.state.collapsedFraction,
                        header = header,
                        subtitle = countLine,
                    )
                },
                expandedHeight = header.expandedHeight(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.library_back))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = libraryHeaderScrolledColor(listAtTop),
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when {
                uiState is LibraryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.library_error_loading))
                }
                uiState !is LibraryUiState.Success -> LibraryLoading { LibraryRowsSkeleton() }
                liked.isEmpty() -> LibraryEmptyState(
                    content = LibraryEmptyContent(
                        title = stringResource(R.string.library_liked_empty_title),
                        body = stringResource(R.string.library_liked_empty_body),
                        icon = Icons.Rounded.Favorite,
                        shape = ExpressiveShapes.Heart,
                        actionText = stringResource(R.string.library_find_podcasts),
                    ),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    onAction = onExploreClick,
                    modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                )
                else -> LikedList(liked, listState, viewModel, onEpisodeClick, onPlayEpisodes)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LikedList(
    liked: List<ListeningHistoryEntity>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    viewModel: LibraryViewModel,
    onEpisodeClick: (Episode, Podcast) -> Unit,
    onPlayEpisodes: ((List<Episode>, Podcast) -> Unit)?,
) {
    val allTitle = stringResource(R.string.library_hub_liked_episodes)
    // Finished episodes stay out of the queue, so Play all shows only while one can go in.
    val playable = remember(liked) { likedPlayAll(liked) }
    LazyColumn(
        state = listState,
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Episode).fillMaxSize().testTag(LIKED_LIST_TAG),
        // Phones keep room for the floating bar and mini player; tablets end just above the player.
        contentPadding = PaddingValues(bottom = boxloreContentBottomPadding(PHONE_LIST_BOTTOM)),
    ) {
        if (onPlayEpisodes != null && playable.isNotEmpty()) {
            item(key = PLAY_ALL_KEY) {
                Button(
                    onClick = { onPlayEpisodes(playable, Podcast(LIKED_ALL_ID, allTitle, "", "")) },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp).testTag(LIKED_PLAY_ALL_TAG),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.library_play_all), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        items(liked, key = { it.episodeId }) { historyItem ->
            val episode = historyItem.toEpisode()
            val podcast = historyItem.toPodcast()
            val progress = if (historyItem.durationMs > 0) (historyItem.progressMs.toFloat() / historyItem.durationMs).coerceIn(0f, 1f) else 0f
            LibraryEpisodeRow(
                title = episode.title,
                artwork = {
                    EpisodeRowArtwork(
                        episode = episode,
                        podcast = podcast,
                        isCompleted = historyItem.isCompleted,
                        isInProgress = !historyItem.isCompleted && historyItem.progressMs > 0,
                        progress = progress,
                    )
                },
                onClick = {
                    viewModel.genericEpisodesClickedCount++
                    onEpisodeClick(episode, podcast)
                },
                subtitle = podcast.title,
                meta = if (historyItem.durationMs > 0) {
                    {
                        Text(
                            text = LocaleFormats.duration(historyItem.durationMs / 1000, currentLocale()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
                trailing = { LatestEpisodePlayButton(onPlay = { viewModel.playEpisode(episode, podcast) }) },
            )
        }
    }
}

/** "12 episodes · 9 hr left": how many liked episodes, and the listening left when lengths are known. */
@Composable
private fun likedCountLine(liked: List<ListeningHistoryEntity>): String? {
    if (liked.isEmpty()) return null
    val count = pluralStringResource(R.plurals.library_hub_episode_count, liked.size, liked.size)
    val leftSeconds = likedTimeLeftSeconds(liked) ?: return count
    val left = stringResource(R.string.library_liked_time_left, LocaleFormats.duration(leftSeconds, currentLocale()))
    return "$count · $left"
}

/** Seconds of listening left in the unfinished liked episodes, or null when no length is known. */
internal fun likedTimeLeftSeconds(liked: List<ListeningHistoryEntity>): Long? {
    val known = liked.filter { it.durationMs > 0 && !it.isCompleted }
    if (known.isEmpty()) return null
    return known.sumOf { (it.durationMs - it.progressMs).coerceAtLeast(0L) } / 1000
}

/** Play all's queue: the liked episodes in their order, finished ones left out. */
internal fun likedPlayAll(liked: List<ListeningHistoryEntity>): List<Episode> =
    liked.filterNot { it.isCompleted }.distinctBy { it.episodeId }.map { it.toEpisode() }

private fun ListeningHistoryEntity.toEpisode() = Episode(
    id = episodeId,
    title = episodeTitle,
    description = "",
    imageUrl = episodeImageUrl ?: "",
    audioUrl = episodeAudioUrl ?: "",
    duration = (durationMs / 1000).toInt(),
    publishedDate = 0L,
    podcastId = podcastId,
    podcastTitle = podcastName,
    podcastImageUrl = podcastImageUrl,
)

private fun ListeningHistoryEntity.toPodcast() = Podcast(
    id = podcastId,
    title = podcastName,
    artist = "",
    imageUrl = podcastImageUrl ?: "",
    description = "",
)

/** Phones: room under the last row for the floating bar and mini player. */
private val PHONE_LIST_BOTTOM = 180.dp
private const val PLAY_ALL_KEY = "liked_play_all"
private const val LIKED_ALL_ID = "liked_all"

/** Test tags for the Liked list and its Play all button. */
internal const val LIKED_LIST_TAG = "liked_list"
internal const val LIKED_PLAY_ALL_TAG = "liked_play_all"
