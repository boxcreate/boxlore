package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.StableCompletedDownloadList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import cx.aswin.boxlore.feature.home.logic.HomeMixModeLogic

internal fun formatRelativeDate(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        diff < 2592000 -> "${diff / 604800}w ago"
        diff < 31536000 -> "${diff / 2592000}mo ago"
        else -> "${diff / 31536000}y ago"
    }
}

@Composable
@Suppress("LongParameterList")
internal fun HomeMixModule(
    dailyPodcasts: List<Podcast>,
    subscribedPodcastCount: Int,
    completedDownloads: StableCompletedDownloadList,
    selectedMode: HomeMixMode,
    episodePlaybackState: StablePlaybackStateMap,
    softExpireProgressEpisodeIds: Set<String>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    downloadedEpisodeIds: Set<String>,
    onModeChanged: (HomeMixMode) -> Unit,
    onPlayMix: (HomeMixMode) -> Unit,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    onViewDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canOfferOffline =
        HomeMixModeLogic.canOfferOffline(
            subscriptionCount = subscribedPodcastCount,
            completedDownloadCount = completedDownloads.list.size,
        )
    val mode = HomeMixModeLogic.resolveMode(selectedMode, canOfferOffline)

    val visibleDownloads =
        remember(completedDownloads.list) {
            HomeMixModeLogic.visibleOfflineItems(completedDownloads.list)
        }
    val visibleItemCount =
        when (mode) {
            HomeMixMode.DAILY -> dailyPodcasts.size
            HomeMixMode.OFFLINE -> visibleDownloads.size
        }
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        HomeMixHeader(
            mode = mode,
            canOfferOffline = canOfferOffline,
            offlineCount = completedDownloads.list.size,
            playEnabled = visibleItemCount > 0,
            onModeSelected = { selected ->
                if (selected != selectedMode) {
                    onModeChanged(selected)
                }
            },
            onPlay = { onPlayMix(mode) },
            modifier = Modifier.padding(horizontal = 18.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        HomeMixRail(
            mode = mode,
            dailyPodcasts = dailyPodcasts,
            visibleDownloads = visibleDownloads,
            episodePlaybackState = episodePlaybackState,
            softExpireProgressEpisodeIds = softExpireProgressEpisodeIds,
            currentPlayingEpisodeId = currentPlayingEpisodeId,
            isPlaying = isPlaying,
            downloadedEpisodeIds = downloadedEpisodeIds,
            onEpisodeClick = onEpisodeClick,
            onPlayEpisode = onPlayEpisode,
            onViewDownloads = onViewDownloads,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun HomeMixRail(
    mode: HomeMixMode,
    dailyPodcasts: List<Podcast>,
    visibleDownloads: List<CompletedDownloadItem>,
    episodePlaybackState: StablePlaybackStateMap,
    softExpireProgressEpisodeIds: Set<String>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    downloadedEpisodeIds: Set<String>,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    onViewDownloads: () -> Unit,
) {
    val dailyScrollState = rememberLazyListState()
    val offlineScrollState = rememberLazyListState()
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            val direction = if (targetState == HomeMixMode.OFFLINE) 1 else -1
            (
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 420),
                    initialOffsetX = { width -> direction * width / 3 },
                ) +
                    fadeIn(animationSpec = tween(durationMillis = 300, delayMillis = 45)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(durationMillis = 380),
                    )
                ) togetherWith
                (
                    slideOutHorizontally(
                        animationSpec = tween(durationMillis = 240),
                        targetOffsetX = { width -> -direction * width / 4 },
                    ) +
                        fadeOut(animationSpec = tween(durationMillis = 180))
                    )
        },
        contentKey = { activeMode -> activeMode.name },
        label = "home_mix_mode_content",
        modifier =
        Modifier
            .fillMaxWidth()
            .height(124.dp),
    ) { activeMode ->
        when (activeMode) {
            HomeMixMode.DAILY ->
                DailyMixRail(
                    podcasts = dailyPodcasts,
                    scrollState = dailyScrollState,
                    episodePlaybackState = episodePlaybackState,
                    softExpireProgressEpisodeIds = softExpireProgressEpisodeIds,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    downloadedEpisodeIds = downloadedEpisodeIds,
                    onEpisodeClick = onEpisodeClick,
                    onPlayEpisode = onPlayEpisode,
                )

            HomeMixMode.OFFLINE ->
                OfflineMixRail(
                    downloads = visibleDownloads,
                    scrollState = offlineScrollState,
                    episodePlaybackState = episodePlaybackState,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    onEpisodeClick = onEpisodeClick,
                    onPlayEpisode = onPlayEpisode,
                    onViewDownloads = onViewDownloads,
                )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun DailyMixRail(
    podcasts: List<Podcast>,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    episodePlaybackState: StablePlaybackStateMap,
    softExpireProgressEpisodeIds: Set<String>,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    downloadedEpisodeIds: Set<String>,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
) {
    if (podcasts.isEmpty()) {
        HomeMixEmptyState()
        return
    }
    LazyRow(
        state = scrollState,
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = podcasts,
            key = { _, podcast -> podcast.latestEpisode?.id ?: podcast.id },
        ) { index, podcast ->
            val episode = podcast.latestEpisode ?: return@itemsIndexed
            val playbackState = episodePlaybackState.map[episode.id]
            val softExpire = episode.id in softExpireProgressEpisodeIds
            AnimatedHomeMixCard(
                index = index,
                enterFromEnd = false,
            ) {
                MixtapeEpisodeCard(
                    episode = episode,
                    podcast = podcast,
                    onClick = { onEpisodeClick(episode, podcast, HOME_MIXTAPE_EPISODES_ENTRY_POINT) },
                    onPlay = {
                        onPlayEpisode(
                            episode,
                            podcast,
                            cx.aswin.boxlore.core.model.PlaybackEntryPoint.HOME_MIXTAPE,
                        )
                    },
                    overrideStatus =
                    if (softExpire) {
                        EpisodeStatus.UNPLAYED
                    } else {
                        playbackState?.first
                    },
                    overrideProgress = if (softExpire) 0f else playbackState?.second,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    isDownloaded = episode.id in downloadedEpisodeIds,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun OfflineMixRail(
    downloads: List<CompletedDownloadItem>,
    scrollState: androidx.compose.foundation.lazy.LazyListState,
    episodePlaybackState: StablePlaybackStateMap,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    onViewDownloads: () -> Unit,
) {
    LazyRow(
        state = scrollState,
        contentPadding = PaddingValues(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = downloads,
            key = { _, item -> item.episode.id },
        ) { index, item ->
            AnimatedHomeMixCard(
                index = index,
                enterFromEnd = true,
            ) {
                OfflineMixEpisodeCard(
                    item = item,
                    playbackState = episodePlaybackState.map[item.episode.id],
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    onEpisodeClick = onEpisodeClick,
                    onPlayEpisode = onPlayEpisode,
                )
            }
        }
        item(key = "view_all_downloads") {
            AnimatedHomeMixCard(
                index = downloads.size,
                enterFromEnd = true,
            ) {
                ViewAllDownloadsCard(onClick = onViewDownloads)
            }
        }
    }
}

@Composable
private fun HomeMixHeader(
    mode: HomeMixMode,
    canOfferOffline: Boolean,
    offlineCount: Int,
    playEnabled: Boolean,
    onModeSelected: (HomeMixMode) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeMixDropdownHeading(
                mode = mode,
                subtitle =
                when (mode) {
                    HomeMixMode.DAILY -> stringResource(R.string.home_mix_daily_subtitle)
                    HomeMixMode.OFFLINE ->
                        pluralStringResource(
                            R.plurals.home_mix_offline_subtitle,
                            offlineCount,
                            offlineCount,
                        )
                },
                enabled = canOfferOffline,
                onModeSelected = onModeSelected,
                modifier = Modifier.weight(1f),
            )

            Button(
                onClick = onPlay,
                enabled = playEnabled,
                shape = CircleShape,
                modifier = Modifier.height(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_mix_play),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        }
    }
}

@Composable
private fun HomeMixDropdownHeading(
    mode: HomeMixMode,
    subtitle: String,
    enabled: Boolean,
    onModeSelected: (HomeMixMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by
        animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(180),
            label = "home_mix_title_chevron",
        )
    Box(modifier = modifier) {
        Column(
            modifier =
            if (enabled) {
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { role = Role.Button }
                    .expressiveClickable(
                        shape = RoundedCornerShape(10.dp),
                        onClick = { expanded = true },
                    )
                    .padding(end = 4.dp)
            } else {
                Modifier
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                AnimatedHomeMixTitle(mode = mode)
                if (enabled) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.home_mix_change_mode),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier =
                        Modifier
                            .size(20.dp)
                            .rotate(chevronRotation),
                    )
                }
            }
            AnimatedHomeMixSubtitle(subtitle = subtitle)
        }

        HomeMixDropdownMenu(
            mode = mode,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            onModeSelected = {
                expanded = false
                onModeSelected(it)
            },
        )
    }
}

@Composable
private fun HomeMixDropdownMenu(
    mode: HomeMixMode,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onModeSelected: (HomeMixMode) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        HomeMixMode.entries.forEach { option ->
            val selected = option == mode
            DropdownMenuItem(
                text = {
                    Text(
                        text =
                        stringResource(
                            when (option) {
                                HomeMixMode.DAILY -> R.string.home_mix_daily_title
                                HomeMixMode.OFFLINE -> R.string.home_mix_offline_title
                            },
                        ),
                        fontWeight = if (selected) GoogleSansWeight.bold else GoogleSansWeight.regular,
                    )
                },
                onClick = { onModeSelected(option) },
                leadingIcon = {
                    Icon(
                        imageVector =
                        when (option) {
                            HomeMixMode.DAILY -> Icons.AutoMirrored.Rounded.QueueMusic
                            HomeMixMode.OFFLINE -> Icons.Outlined.DownloadDone
                        },
                        contentDescription = null,
                    )
                },
                trailingIcon =
                if (selected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun OfflineMixEpisodeCard(
    item: CompletedDownloadItem,
    playbackState: Pair<EpisodeStatus, Float>?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
) {
    MixtapeEpisodeCard(
        episode = item.episode,
        podcast = item.podcast,
        onClick = { onEpisodeClick(item.episode, item.podcast, HOME_MIXTAPE_EPISODES_ENTRY_POINT) },
        onPlay = {
            onPlayEpisode(
                item.episode,
                item.podcast,
                cx.aswin.boxlore.core.model.PlaybackEntryPoint.HOME_MIXTAPE,
            )
        },
        overrideStatus = playbackState?.first,
        overrideProgress = playbackState?.second,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        isPlaying = isPlaying,
        isDownloaded = true,
    )
}

@Composable
private fun HomeMixEmptyState() {
    Row(
        modifier =
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(
                text = stringResource(R.string.home_mix_caught_up_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.bold,
            )
            Text(
                text = stringResource(R.string.home_mix_caught_up_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ViewAllDownloadsCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier =
        Modifier
            .width(148.dp)
            .height(116.dp)
            .expressiveClickable(
                shape = RoundedCornerShape(20.dp),
                onClick = onClick,
            ),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.DownloadDone,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = stringResource(R.string.home_mix_view_all_downloads),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun MixtapeSelectorCover(
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp),
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.05f else 0.95f, label = "scale")
    val alpha by animateFloatAsState(
        targetValue =
        if (isSelected) {
            1f
        } else if (isAnyPodcastSelected) {
            0.6f
        } else {
            1f
        },
        label = "alpha",
    )
    val cornerRadius by animateDpAsState(targetValue = if (isSelected) 16.dp else 12.dp, label = "cornerRadius")
    val borderStrokeWidth by animateDpAsState(targetValue = if (isSelected) 3.dp else 0.dp, label = "borderStrokeWidth")

    Box(
        modifier =
        modifier
            .scale(scale),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .expressiveClickable(
                    shape = RoundedCornerShape(cornerRadius),
                    onClick = onClick,
                ).clip(RoundedCornerShape(cornerRadius)),
        ) {
            Box(
                modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .alpha(alpha),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "For You",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            if (isSelected) {
                Box(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, RoundedCornerShape(cornerRadius)),
                )
            }
        }
    }
}

private const val HOME_MIXTAPE_EPISODES_ENTRY_POINT = "home_mixtape_episodes"

@Composable
internal fun MixtapeEpisodeCard(
    episode: Episode,
    podcast: Podcast,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    overrideStatus: EpisodeStatus? = null,
    overrideProgress: Float? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
) {
    val status = overrideStatus ?: if (podcast.latestEpisode?.id == episode.id) podcast.episodeStatus else EpisodeStatus.UNPLAYED
    val progress = overrideProgress ?: if (podcast.latestEpisode?.id == episode.id) (podcast.resumeProgress ?: 0f) else 0f
    val isInProgress = status == EpisodeStatus.IN_PROGRESS
    val isCompleted = status == EpisodeStatus.COMPLETED
    val isCurrentPlaying = currentPlayingEpisodeId == episode.id && isPlaying

    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier =
        modifier
            .width(264.dp)
            .height(116.dp)
            .expressiveClickable(shape = RoundedCornerShape(20.dp), onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Left: Cover art with download & played badges
                Box(
                    modifier =
                    Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    OptimizedImage(
                        url = (
                            episode.imageUrl?.takeIf { it.isNotEmpty() } ?: podcast.imageUrl.takeIf { it.isNotEmpty() }
                                ?: podcast.fallbackImageUrl
                            ),
                        proxyWidth = 152,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (isCompleted) {
                        Box(
                            modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Played",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }

                    if (isDownloaded) {
                        Box(
                            modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .border(0.5.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }

                    Box(
                        modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Surface(
                            onClick = onPlay,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Icon(
                                    imageVector =
                                    if (isCurrentPlaying) {
                                        Icons.Rounded.Pause
                                    } else {
                                        Icons.Rounded.PlayArrow
                                    },
                                    contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }
                }

                // Center: Info Column (Titles + Metadata)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Text(
                        text = episode.title,
                        style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = GoogleSansWeight.bold,
                            lineHeight = 18.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val isNew =
                            status == EpisodeStatus.UNPLAYED &&
                                podcast.subscribedAt > 0L &&
                                episode.publishedDate > (podcast.subscribedAt / 1000L - 7 * 24 * 3600L)
                        if (isNew) {
                            Box(
                                modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = "NEW",
                                    style =
                                    MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = GoogleSansWeight.bold,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.4.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        val relativeDate = formatRelativeDate(episode.publishedDate)
                        if (relativeDate.isNotEmpty()) {
                            val prefix = if (isNew) "• " else ""
                            Text(
                                text = "$prefix$relativeDate",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (episode.duration > 0) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }
                        }

                        if (episode.duration > 0) {
                            val h = episode.duration / 3600
                            val m = (episode.duration % 3600) / 60
                            val timeText =
                                if (isInProgress && progress > 0f) {
                                    val remaining = ((1f - progress) * episode.duration).toInt()
                                    val rm = (remaining % 3600) / 60
                                    "${rm}m left"
                                } else {
                                    if (h > 0) "${h}h ${m}m" else "${m}m"
                                }
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = GoogleSansWeight.medium,
                                color =
                                if (isInProgress) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            // Absolute Bottom: Progress Bar spanning full card width
            if (isInProgress && progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
