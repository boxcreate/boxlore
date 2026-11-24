package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreScrollableCenteredColumn
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreConfigurationWindowSize
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.playback.playQueue
import cx.aswin.boxlore.core.playback.togglePlayPause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    playbackRepository: cx.aswin.boxlore.core.playback.PlaybackRepository,
    bottomContentPadding: Dp,
    onEpisodeClick: (Episode) -> Unit,
    onQueueEpisode: (Episode) -> Unit,
    onPodcastClick: (feedId: Long?, itunesId: Long?, feedUrl: String, title: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val windowSize = loreTabletPaneSize(LocalBoxloreWindowLayout.current, LocalBoxlorePaneBounds.current)
        ?: boxloreConfigurationWindowSize()
    val compactSpacing = windowSize.width < 360.dp || windowSize.height < 720.dp
    // The gap above the deck and the deck's sides, tighter in small windows.
    val deckGap = if (compactSpacing) 8.dp else 16.dp
    val deckSides = if (compactSpacing) 12.dp else 20.dp
    val uiState by viewModel.uiState.collectAsState()
    val stablePlayerState = remember(playbackRepository) {
        playbackRepository.playerState
            .map { it.copy(position = 0L, bufferedPosition = 0L) }
            .distinctUntilChanged()
    }
    val playerState by stablePlayerState.collectAsState(
        initial = cx.aswin.boxlore.core.playback.PlayerState()
    )
    val coroutineScope = rememberCoroutineScope()

    TrackScreenView("learn") {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLearnScreenViewed()
    }

    TrackScreenSession(
        onSessionResume = viewModel::onScreenResume,
        onSessionExit = viewModel::trackScreenExit
    )

    val artworkColors by viewModel.artworkAccentColors.collectAsState()
    val cards = (uiState as? LearnUiState.Success)?.questionsStack.orEmpty()
    val fallbackAccent = MaterialTheme.colorScheme.primary
    val activeArtwork = cards.firstOrNull()?.artworkSources.orEmpty()
    val baseAccentColor = artworkColors[activeArtwork]?.let { Color(it) } ?: fallbackAccent
    val podcastFallback = stringResource(R.string.lore_podcast_fallback)
    val unknownArtist = stringResource(R.string.lore_unknown_artist)
    val handleLearnCardAction: (String, LearnCuriosityCard) -> Unit = { action, card ->
        val mappedEpisode = card.toEpisode()
        when (action) {
            "dismiss" -> {
                viewModel.trackCardDismissed(card)
                trackLearnCardAction("dismiss", mappedEpisode)
                viewModel.dismissCuriosity(card, LearnHistoryAction.DISMISS)
            }
            "queue" -> {
                viewModel.trackCardQueued(card)
                trackLearnCardAction("queue", mappedEpisode)
                onQueueEpisode(mappedEpisode)
                viewModel.dismissCuriosity(card, LearnHistoryAction.QUEUE)
            }
            "info" -> {
                viewModel.trackInfoClicked(card)
                trackLearnCardAction("info", mappedEpisode)
                onEpisodeClick(mappedEpisode)
            }
            "play" -> {
                viewModel.trackPlayClicked(card)
                trackLearnCardAction("play", mappedEpisode)
                playLoreEpisode(mappedEpisode, playerState, playbackRepository, coroutineScope, podcastFallback to unknownArtist)
            }
            "podcast" -> {
                viewModel.trackPodcastClicked(card)
                trackLearnCardAction("podcast", mappedEpisode)
                onPodcastClick(
                    mappedEpisode.podcastId?.toLongOrNull(),
                    null,
                    "",
                    mappedEpisode.podcastTitle ?: podcastFallback
                )
            }
        }
    }

    val accentTransition = remember { LoreAccentTransition(baseAccentColor) }
    val swipeThresholdPx = with(LocalDensity.current) { LoreSwipeThresholdDp.dp.toPx() }
    val swipeState = rememberSwipeableCardState(
        key = cards.take(2).map { it.episodeId },
        onDragStarted = { state ->
            val nextArtwork = cards.getOrNull(1)?.artworkSources
            val nextAccent = if (nextArtwork == null) {
                baseAccentColor
            } else {
                artworkColors[nextArtwork]?.let(::Color) ?: fallbackAccent
            }
            accentTransition.beginSwipe(nextAccent, state.offset, swipeThresholdPx)
        },
    ) { direction ->
        cards.firstOrNull()?.let { card ->
            handleLearnCardAction(if (direction == SwipeDirection.Left) "dismiss" else "queue", card)
        }
    }
    LaunchedEffect(swipeState, baseAccentColor, swipeState.phase) {
        if (swipeState.phase == LoreSwipePhase.Idle) accentTransition.settle(baseAccentColor)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        LoreHaloBackground(
            accentColor = accentTransition.color,
            modifier = Modifier.fillMaxSize()
        ) {
            val isRefreshing = when (val state = uiState) {
                is LearnUiState.Success -> state.isRefreshing
                is LearnUiState.CaughtUp -> state.isRefreshing
                else -> false
            }
            val pullToRefreshState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                state = pullToRefreshState,
                modifier = Modifier.fillMaxSize()
                    .navigationBarsPadding()
                    .lorePlayerClearance(bottomContentPadding + 16.dp, playerState.currentEpisode != null)
            ) {
                // One header for every state, so the logo and History never move when the state changes.
                // On tablets the header and deck sit together in the middle of a tall pane.
                val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        // Clear of a sideways phone's camera and buttons; the colour halo still reaches the edge.
                        .boxloreHorizontalSafePadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (tablet) Arrangement.Center else Arrangement.Top,
                ) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LoreDeckHeader(
                        accentColor = accentTransition.color,
                        onNavigateToHistory = onNavigateToHistory,
                        compactSpacing = compactSpacing,
                    )
                    when (val state = uiState) {
                        is LearnUiState.Loading -> {
                            // A card-shaped placeholder in the first card's exact place.
                            Spacer(modifier = Modifier.height(deckGap))
                            LoreCardSkeleton(
                                accentColor = fallbackAccent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = !tablet)
                                    .padding(horizontal = deckSides)
                            )
                        }
                        is LearnUiState.CaughtUp -> {
                            LoreStatePanel(
                                tablet = tablet,
                                compactSpacing = compactSpacing,
                                title = stringResource(R.string.lore_caught_up_title),
                                description = stringResource(R.string.lore_caught_up_body),
                            ) {
                                FilledTonalButton(
                                    onClick = viewModel::refresh,
                                    shape = CircleShape
                                ) {
                                    Text(stringResource(R.string.lore_check_new_cards))
                                }
                                TextButton(onClick = onNavigateToHistory) {
                                    Text(stringResource(R.string.lore_open_history))
                                }
                            }
                        }
                        is LearnUiState.Success -> {
                            val visibleCard = state.questionsStack.firstOrNull()
                            LaunchedEffect(visibleCard?.episodeId) {
                                visibleCard?.let(viewModel::trackCardVisible)
                            }
                            Spacer(modifier = Modifier.height(deckGap))
                            CuriosityCardStack(
                                questions = state.questionsStack,
                                swipeState = swipeState,
                                playerState = playerState,
                                onCardAction = { action, card -> handleLearnCardAction(action.loreActionName(), card) },
                                accentColor = fallbackAccent,
                                artworkAccentColors = artworkColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = !tablet)
                                    .padding(horizontal = deckSides)
                            )
                        }
                        is LearnUiState.Error -> {
                            LoreStatePanel(
                                tablet = tablet,
                                compactSpacing = compactSpacing,
                                title = stringResource(R.string.lore_error_title),
                                description = state.message.asString(),
                            ) {
                                FilledTonalButton(
                                    onClick = viewModel::refresh,
                                    shape = CircleShape
                                ) {
                                    Text(stringResource(R.string.explore_try_again))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The name a card action goes by in the screen's handler and in analytics. */
private fun CardAction.loreActionName(): String = when (this) {
    CardAction.Dismiss -> "dismiss"
    CardAction.Queue -> "queue"
    CardAction.Play -> "play"
    CardAction.Click -> "info"
    CardAction.PodcastClick -> "podcast"
}

/** Keep playback action branching out of the screen layout; reuse the injected player. */
internal fun loreTabletPaneSize(window: BoxloreWindowLayout, pane: BoxloreLayoutBounds?): DpSize? =
    if (window.usesTabletLayout && pane != null) DpSize(pane.width.dp, pane.height.dp) else null

private fun playLoreEpisode(
    episode: Episode,
    playerState: PlayerState,
    playbackRepository: PlaybackRepository,
    scope: CoroutineScope,
    fallbackTitleAndArtist: Pair<String, String>,
) {
    if (playerState.currentEpisode?.id == episode.id) {
        playbackRepository.togglePlayPause()
    } else {
        val podcast = cx.aswin.boxlore.core.model.Podcast(
            id = episode.podcastId ?: "learn_fallback",
            title = episode.podcastTitle ?: fallbackTitleAndArtist.first,
            artist = episode.podcastTitle ?: fallbackTitleAndArtist.second,
            imageUrl = episode.imageUrl ?: ""
        )
        scope.launch {
            playbackRepository.playQueue(
                episodes = listOf(episode),
                podcast = podcast,
                startIndex = 0,
                entryPoint = cx.aswin.boxlore.core.model.PlaybackEntryPoint.LEARN
            )
        }
    }
}

/**
 * Lore's caught-up and failed panels: centred under the header when they fit, scrolling when the
 * window is too short, so their buttons are never cut off and pull to refresh still works.
 */
/**
 * Caught up and error panels. On tablets they take the deck's frame, after the same gap, so the
 * header stays exactly where it is for the cards; on every window they stay as narrow as the header.
 */
@Composable
private fun ColumnScope.LoreStatePanel(
    tablet: Boolean,
    compactSpacing: Boolean,
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    if (tablet) {
        Spacer(modifier = Modifier.height(if (compactSpacing) 8.dp else 16.dp))
        LoreDeckFrame(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .padding(horizontal = if (compactSpacing) 12.dp else 20.dp),
        ) { _ ->
            LoreStateCard(title, description, Modifier.fillMaxSize(), content)
        }
    } else {
        LoreStateCard(title, description, Modifier.weight(1f).widthIn(max = LoreHeaderMaxWidth), content)
    }
}

@Composable
private fun LoreStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxloreScrollableCenteredColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .boxloreCardWidth(BoxloreCardFamily.Reading)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = GoogleSansWeight.bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                content()
            }
        }
    }
}

private fun trackLearnCardAction(
    action: String,
    episode: Episode
) {
    val analytics = cx.aswin.boxlore.core.analytics.AnalyticsHelper
    val episodeId = episode.id
    val episodeTitle = episode.title
    val podcastId = episode.podcastId
    val podcastTitle = episode.podcastTitle

    when (action) {
        "dismiss" -> analytics.trackLearnCardDismissed(episodeId, episodeTitle, podcastId, podcastTitle)
        "queue" -> analytics.trackLearnCardQueued(episodeId, episodeTitle, podcastId, podcastTitle)
        "info" -> analytics.trackLearnCardInfoClicked(episodeId, episodeTitle, podcastId, podcastTitle)
        "play" -> analytics.trackLearnCardPlayClicked(episodeId, episodeTitle, podcastId, podcastTitle)
        "podcast" -> analytics.trackLearnCardPodcastClicked(podcastId, podcastTitle)
    }
}
