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
            HomeMixMode.DAILY -> dailyPodcasts.count { it.latestEpisode != null }
            HomeMixMode.OFFLINE -> visibleDownloads.size
        }
    if (mode == HomeMixMode.DAILY && visibleItemCount == 0) {
        HomeMixDropdownHeading(
            mode = mode,
            subtitle = stringResource(R.string.home_mix_caught_up_title),
            enabled = canOfferOffline,
            onModeSelected = onModeChanged,
            modifier = modifier.fillMaxWidth().padding(vertical = HomeMixLayout.VerticalPadding, horizontal = 18.dp),
        )
        return
    }
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .homeMixBackdrop(enabled = visibleItemCount > 0)
            .padding(vertical = HomeMixLayout.VerticalPadding),
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

        Spacer(modifier = Modifier.height(HomeMixLayout.HeaderGap))

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
            .height(HomeMixLayout.RailHeight),
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
        ) { _, podcast ->
            val episode = podcast.latestEpisode ?: return@itemsIndexed
            val playbackState = episodePlaybackState.map[episode.id]
            val softExpire = episode.id in softExpireProgressEpisodeIds
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
        ) { _, item ->
            OfflineMixEpisodeCard(
                item = item,
                playbackState = episodePlaybackState.map[item.episode.id],
                currentPlayingEpisodeId = currentPlayingEpisodeId,
                isPlaying = isPlaying,
                onEpisodeClick = onEpisodeClick,
                onPlayEpisode = onPlayEpisode,
            )
        }
        item(key = "view_all_downloads") {
            ViewAllDownloadsCard(onClick = onViewDownloads)
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
                modifier = Modifier.height(HomeMixLayout.HeaderHeight),
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
            .height(HomeMixLayout.CardHeight)
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
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
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

    val cardShape = RoundedCornerShape(24.dp)
    Card(
        shape = cardShape,
        colors =
        CardDefaults.cardColors(
            containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier =
        modifier
            .width(HomeMixLayout.CardWidth)
            .height(HomeMixLayout.CardHeight)
            .expressiveClickable(shape = cardShape, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Artwork with duration, playback and status controls.
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    OptimizedImage(
                        url = resolveMixArtwork(
                            episode.imageUrl,
                            episode.podcastImageUrl,
                            podcast.imageUrl,
                            podcast.fallbackImageUrl,
                        ),
                        proxyWidth = 512,
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

                    if (isDownloaded && !isCompleted) {
                        Box(
                            modifier =
                            Modifier
                                .align(Alignment.TopEnd)
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
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                        ) {
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = GoogleSansWeight.medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }

                    Surface(
                        onClick = onPlay,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 0.dp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(48.dp),
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
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Center the titles in the space remaining above any progress indicator.
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = episode.title,
                        style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = GoogleSansWeight.semiBold,
                            lineHeight = 18.sp,
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = podcast.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = GoogleSansWeight.medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isInProgress && progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.secondaryContainer,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}
