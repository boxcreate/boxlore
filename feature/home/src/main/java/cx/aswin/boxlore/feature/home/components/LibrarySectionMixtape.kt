package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
        diff < 60 -> "Just now"
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
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .animateContentSize(YourShowsMotion.spatial())
            .padding(top = HomeMixLayout.TopPadding, bottom = HomeMixLayout.BottomPadding),
    ) {
        if (mode == HomeMixMode.DAILY && visibleItemCount == 0) {
            HomeMixDropdownHeading(
                mode = mode,
                subtitle = stringResource(R.string.home_mix_caught_up_title),
                enabled = canOfferOffline,
                onModeSelected = onModeChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = HomeMixLayout.ContentInset),
            )
        } else {
            HomeMixHeader(
                mode = mode,
                canOfferOffline = canOfferOffline,
                subtitle = when (mode) {
                    HomeMixMode.DAILY -> dailyMixSubtitle(remember(dailyPodcasts) { dailyMixSummary(dailyPodcasts) })
                    HomeMixMode.OFFLINE ->
                        pluralStringResource(R.plurals.home_mix_offline_subtitle, completedDownloads.list.size, completedDownloads.list.size)
                },
                playEnabled = visibleItemCount > 0,
                onModeSelected = { selected ->
                    if (selected != selectedMode) {
                        onModeChanged(selected)
                    }
                },
                onPlay = { onPlayMix(mode) },
                modifier = Modifier.padding(horizontal = HomeMixLayout.ContentInset),
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
    val railLayout = LocalYourShowsRailLayout.current
    AnimatedContent(
        targetState = mode,
        transitionSpec = { yourShowsContentTransform(forward = targetState == HomeMixMode.OFFLINE) },
        contentKey = { activeMode -> activeMode.name },
        label = "home_mix_mode_content",
        modifier =
        Modifier
            .fillMaxWidth()
            .height(if (railLayout.stacked) railLayout.height else homeMixCardHeight()),
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cardWidth = if (LocalYourShowsRailLayout.current.stacked) (maxWidth - HomeMixLayout.ContentInset * 2).coerceAtMost(720.dp) else HomeMixLayout.railCardWidth(maxWidth)
    AdaptiveHomeMixRail(scrollState) {
        itemsIndexed(
            items = podcasts,
            key = { _, podcast -> podcast.latestEpisode?.id ?: podcast.id },
        ) { _, podcast ->
            val episode = podcast.latestEpisode ?: return@itemsIndexed
            val playbackState = episodePlaybackState.map[episode.id]
            val softExpire = episode.id in softExpireProgressEpisodeIds
            MixtapeEpisodeCard(
                modifier = Modifier.width(cardWidth),
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
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val cardWidth = if (LocalYourShowsRailLayout.current.stacked) (maxWidth - HomeMixLayout.ContentInset * 2).coerceAtMost(720.dp) else HomeMixLayout.railCardWidth(maxWidth)
    AdaptiveHomeMixRail(scrollState) {
        itemsIndexed(
            items = downloads,
            key = { _, item -> item.episode.id },
        ) { _, item ->
            OfflineMixEpisodeCard(
                modifier = Modifier.width(cardWidth),
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
}

@Composable
private fun AdaptiveHomeMixRail(state: LazyListState, content: LazyListScope.() -> Unit) {
    if (LocalYourShowsRailLayout.current.stacked) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(horizontal = HomeMixLayout.ContentInset),
            verticalArrangement = Arrangement.spacedBy(HomeMixLayout.CardGap),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().testTag("home_mix_stacked_rail"),
            content = content,
        )
    } else {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = HomeMixLayout.ContentInset),
            horizontalArrangement = Arrangement.spacedBy(HomeMixLayout.CardGap),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun HomeMixHeader(
    mode: HomeMixMode,
    canOfferOffline: Boolean,
    subtitle: String,
    playEnabled: Boolean,
    onModeSelected: (HomeMixMode) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YourShowsContentHeader(
        modifier = modifier,
        lead = {
            HomeMixDropdownHeading(
                mode = mode,
                subtitle = subtitle,
                enabled = canOfferOffline,
                onModeSelected = onModeSelected,
            )
        },
    ) {
        Button(
            onClick = onPlay,
            enabled = playEnabled,
            shape = RoundedCornerShape(topStart = 17.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 17.dp),
            modifier = Modifier.heightIn(min = HomeMixLayout.HeaderHeight),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
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
    modifier: Modifier,
    item: CompletedDownloadItem,
    playbackState: Pair<EpisodeStatus, Float>?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
) {
    MixtapeEpisodeCard(
        modifier = modifier,
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
            .padding(horizontal = HomeMixLayout.ContentInset + 6.dp),
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
            .height(homeMixCardHeight())
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
                maxLines = 2,
            )
        }
    }
}

private const val HOME_MIXTAPE_EPISODES_ENTRY_POINT = "home_mixtape_episodes"

@Composable
private fun dailyMixSubtitle(summary: DailyMixSummary): String {
    if (summary.minutes <= 0) {
        return pluralStringResource(R.plurals.home_mix_daily_count, summary.episodes, summary.episodes)
    }
    val time = if (summary.minutes >= 60) {
        stringResource(R.string.home_hero_hours_minutes, summary.minutes / 60, summary.minutes % 60)
    } else {
        stringResource(R.string.home_hero_minutes, summary.minutes)
    }
    return pluralStringResource(R.plurals.home_mix_daily_summary, summary.episodes, summary.episodes, time)
}
