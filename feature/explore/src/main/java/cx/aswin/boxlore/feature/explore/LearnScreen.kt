package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.TrackScreenSession
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.playQueue
import cx.aswin.boxlore.core.playback.togglePlayPause
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

    LaunchedEffect(Unit) {
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
    // Only the page eases between cards; each card keeps its own prepared artwork colour.
    val animatedAccentColor by animateColorAsState(
        targetValue = baseAccentColor,
        animationSpec = tween(durationMillis = 180),
        label = "AccentColorTransition"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        LoreHaloBackground(
            accentColor = animatedAccentColor,
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
            ) {
                // Apply a baseline 16.dp safety padding on top of the dynamic bottom content padding
                val bottomContentPaddingCalculated = bottomContentPadding + 16.dp

                when (val state = uiState) {
                    is LearnUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = bottomContentPaddingCalculated),
                            contentAlignment = Alignment.Center
                        ) {
                            BoxLoreLoader.Expressive(size = 64.dp)
                        }
                    }
                    is LearnUiState.CaughtUp -> {
                        LoreStateCard(
                            accentColor = animatedAccentColor,
                            title = "You’re all caught up",
                            description = "New curiosities arrive daily. Restore a favorite from your Lore history whenever inspiration strikes.",
                            bottomContentPadding = bottomContentPaddingCalculated
                        ) {
                            FilledTonalButton(
                                onClick = viewModel::refresh,
                                shape = CircleShape
                            ) {
                                Text("Check for new cards")
                            }
                            TextButton(onClick = onNavigateToHistory) {
                                Text("Open Lore history")
                            }
                        }
                    }
                    is LearnUiState.Success -> {
                        val visibleCard = state.questionsStack.firstOrNull()
                        LaunchedEffect(visibleCard?.episodeId) {
                            visibleCard?.let(viewModel::trackCardVisible)
                        }
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
                                    val isCurrent = playerState.currentEpisode?.id == mappedEpisode.id
                                    if (isCurrent) {
                                        playbackRepository.togglePlayPause()
                                    } else {
                                        val podcast = cx.aswin.boxlore.core.model.Podcast(
                                            id = mappedEpisode.podcastId ?: "learn_fallback",
                                            title = mappedEpisode.podcastTitle ?: "Podcast",
                                            artist = mappedEpisode.podcastTitle ?: "Unknown",
                                            imageUrl = mappedEpisode.imageUrl ?: ""
                                        )
                                        coroutineScope.launch {
                                            playbackRepository.playQueue(
                                                episodes = listOf(mappedEpisode),
                                                podcast = podcast,
                                                startIndex = 0,
                                                entryPoint = cx.aswin.boxlore.core.model.PlaybackEntryPoint.LEARN
                                            )
                                        }
                                    }
                                }
                                "podcast" -> {
                                    viewModel.trackPodcastClicked(card)
                                    trackLearnCardAction("podcast", mappedEpisode)
                                    onPodcastClick(
                                        mappedEpisode.podcastId?.toLongOrNull(),
                                        null,
                                        "",
                                        mappedEpisode.podcastTitle ?: "Podcast"
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .padding(bottom = bottomContentPaddingCalculated),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore),
                                    contentDescription = "Lore",
                                    colorFilter = ColorFilter.tint(animatedAccentColor),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .align(Alignment.Center)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 2.dp,
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    IconButton(onClick = onNavigateToHistory) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = "Lore history",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(30.dp))
                            CuriosityCardStack(
                                questions = state.questionsStack,
                                playerState = playerState,
                                onCardAction = { action, card ->
                                    val actionName = when (action) {
                                        CardAction.Dismiss -> "dismiss"
                                        CardAction.Queue -> "queue"
                                        CardAction.Play -> "play"
                                        CardAction.Click -> "info"
                                        CardAction.PodcastClick -> "podcast"
                                    }
                                    handleLearnCardAction(actionName, card)
                                },
                                accentColor = fallbackAccent,
                                artworkAccentColors = artworkColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 20.dp)
                            )
                        }
                    }
                    is LearnUiState.Error -> {
                        LoreStateCard(
                            accentColor = animatedAccentColor,
                            title = "Lore lost the thread",
                            description = state.message,
                            bottomContentPadding = bottomContentPaddingCalculated
                        ) {
                            FilledTonalButton(
                                onClick = viewModel::refresh,
                                shape = CircleShape
                            ) {
                                Text("Try again")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoreStateCard(
    accentColor: Color,
    title: String,
    description: String,
    bottomContentPadding: Dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = bottomContentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore),
            contentDescription = "Lore",
            colorFilter = ColorFilter.tint(accentColor),
            modifier = Modifier.height(38.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
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
