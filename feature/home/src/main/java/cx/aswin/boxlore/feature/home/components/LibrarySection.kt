package cx.aswin.boxlore.feature.home.components

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.LocalSurfaceStyle
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.StableCompletedDownloadList
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode

val LocalLastSeenEpisodes = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun YourShowsSection(
    subscribedPodcasts: StablePodcastList,
    latestEpisodes: StablePodcastList, // Enriched with latest episodes
    selectedPodcastId: String?,
    selectedPodcastEpisodes: StableEpisodeList,
    isSelectedPodcastLoading: Boolean,
    isSelectedRssRefreshing: Boolean,
    episodePlaybackState: StablePlaybackStateMap,
    softExpireProgressEpisodeIds: Set<String> = emptySet(),
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    onPodcastSelected: (String?) -> Unit,
    onPodcastClick: (Podcast) -> Unit,
    onEpisodeClick: (Episode, Podcast, String) -> Unit,
    onPlayMix: (HomeMixMode) -> Unit,
    onMixModeChanged: (HomeMixMode) -> Unit,
    selectedMixMode: HomeMixMode,
    onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    onViewLibrary: () -> Unit,
    onViewDownloads: () -> Unit,
    downloadedEpisodeIds: Set<String> = emptySet(),
    completedDownloads: StableCompletedDownloadList = StableCompletedDownloadList(emptyList()),
    modifier: Modifier = Modifier,
    pinnedPodcastIds: Set<String> = emptySet(),
    onTogglePin: (String) -> Unit = {},
) {
    if (subscribedPodcasts.list.isEmpty()) return
    val lastSeenEpisodes = LocalLastSeenEpisodes.current

    val interleavedPodcasts =
        remember(subscribedPodcasts) {
            val list = subscribedPodcasts.list
            if (list.size > 9) {
                // LazyHorizontalGrid is column-major. Pairwise swap puts list[0] (pin 1)
                // on the top row immediately after mixtape, matching the ≤9 row layout.
                val result = mutableListOf<Podcast>()
                var i = 0
                while (i < list.size) {
                    if (i + 1 < list.size) {
                        result.add(list[i + 1])
                    }
                    result.add(list[i])
                    i += 2
                }
                result
            } else {
                list
            }
        }

    val filteredScrollState = rememberScrollState()
    val filteredScrollConnection =
        remember(filteredScrollState) {
            object : NestedScrollConnection {
                var isFirstScrollEvent = true
                var lockToChild = false
                var wasLocked = false

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y != 0f) {
                        if (isFirstScrollEvent) {
                            isFirstScrollEvent = false
                            val isScrollingUp = available.y < 0f // dragging up / scrolling down
                            val isScrollingDown = available.y > 0f // dragging down / scrolling up

                            val isAtTop = filteredScrollState.value == 0
                            val isAtBottom = filteredScrollState.value >= filteredScrollState.maxValue

                            lockToChild =
                                when {
                                    isScrollingUp && isAtBottom -> false
                                    isScrollingDown && isAtTop -> false
                                    else -> true
                                }
                            wasLocked = lockToChild
                        }
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source == NestedScrollSource.UserInput && lockToChild) {
                        // Slight pass: pass 10% to parent, consume 90%
                        return available * 0.9f
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    isFirstScrollEvent = true
                    lockToChild = false
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (wasLocked) {
                        wasLocked = false
                        return available // Block momentum from scrolling parent if gesture started inside child
                    }
                    return Velocity.Zero
                }
            }
        }

    Column(modifier = modifier) {
        // --- Header ---
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 0.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.expressiveClickable {
                        if (selectedPodcastId != null) {
                            onPodcastSelected(null) // clear selection
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your Shows",
                    style =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = rememberSectionHeaderFontFamily(),
                            fontWeight = FontWeight.Normal,
                        ),
                    letterSpacing = (-0.5).sp,
                )
                if (selectedPodcastId != null && subscribedPodcasts.list.size > 1) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { onPodcastSelected(null) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Filtered",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = GoogleSansWeight.bold,
                                    letterSpacing = 0.5.sp,
                                ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear Filter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onViewLibrary,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "View Library",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // --- The Selector Grid ---
        if (subscribedPodcasts.list.size <= 4) {
            LazyRow(
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (subscribedPodcasts.list.size > 1) {
                    item(key = "mixtape") {
                        MixtapeSelectorCover(
                            isSelected = selectedPodcastId == null,
                            isAnyPodcastSelected = selectedPodcastId != null,
                            onClick = { onPodcastSelected(null) },
                            modifier = Modifier.size(60.dp).animateItem(),
                        )
                    }
                }
                items(subscribedPodcasts.list, key = { it.id }) { podcast ->
                    SelectorCover(
                        podcast = podcast,
                        lastSeenId = lastSeenEpisodes[podcast.id],
                        isSelected = selectedPodcastId == podcast.id,
                        isAnyPodcastSelected = selectedPodcastId != null,
                        onClick = {
                            if (subscribedPodcasts.list.size > 1) {
                                onPodcastSelected(if (selectedPodcastId == podcast.id) null else podcast.id)
                            }
                        },
                        modifier = Modifier.size(60.dp).animateItem(),
                        pin =
                            CoverPin(
                                pinned = podcast.id in pinnedPodcastIds,
                                onToggle = { onTogglePin(podcast.id) },
                            ),
                    )
                }
            }
        } else if (subscribedPodcasts.list.size <= 9) {
            val allItems =
                remember(subscribedPodcasts) {
                    val list = mutableListOf<Any>()
                    if (subscribedPodcasts.list.size > 1) {
                        list.add("mixtape")
                    }
                    list.addAll(subscribedPodcasts.list)
                    list
                }
            val row1Items = remember(allItems) { allItems.take(5) }
            val row2Items = remember(allItems) { allItems.drop(5) }

            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                        .padding(bottom = 16.dp),
            ) {
                val containerWidth = maxWidth
                val availableWidth = containerWidth - 8.dp

                val itemSize =
                    remember(availableWidth) {
                        val minSpacing = 6.dp
                        val neededWidthFor60 = (60.dp * 5) + (minSpacing * 4) // 324.dp
                        if (availableWidth < neededWidthFor60) {
                            ((availableWidth - (minSpacing * 4)) / 5).coerceAtLeast(48.dp)
                        } else {
                            60.dp
                        }
                    }

                val calculatedSpacing =
                    remember(availableWidth, itemSize) {
                        val remaining = availableWidth - (itemSize * 5)
                        if (remaining > 0.dp) {
                            (remaining / 4).coerceAtMost(16.dp).coerceAtLeast(6.dp)
                        } else {
                            6.dp
                        }
                    }

                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(calculatedSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row1Items.forEach { item ->
                            val itemModifier = Modifier.size(itemSize)
                            if (item is String && item == "mixtape") {
                                MixtapeSelectorCover(
                                    isSelected = selectedPodcastId == null,
                                    isAnyPodcastSelected = selectedPodcastId != null,
                                    onClick = { onPodcastSelected(null) },
                                    modifier = itemModifier,
                                )
                            } else if (item is Podcast) {
                                SelectorCover(
                                    podcast = item,
                                    lastSeenId = lastSeenEpisodes[item.id],
                                    isSelected = selectedPodcastId == item.id,
                                    isAnyPodcastSelected = selectedPodcastId != null,
                                    onClick = {
                                        onPodcastSelected(if (selectedPodcastId == item.id) null else item.id)
                                    },
                                    modifier = itemModifier,
                                    pin =
                                        CoverPin(
                                            pinned = item.id in pinnedPodcastIds,
                                            onToggle = { onTogglePin(item.id) },
                                        ),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(calculatedSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row2Items.forEach { item ->
                            val itemModifier = Modifier.size(itemSize)
                            if (item is Podcast) {
                                SelectorCover(
                                    podcast = item,
                                    lastSeenId = lastSeenEpisodes[item.id],
                                    isSelected = selectedPodcastId == item.id,
                                    isAnyPodcastSelected = selectedPodcastId != null,
                                    onClick = {
                                        onPodcastSelected(if (selectedPodcastId == item.id) null else item.id)
                                    },
                                    modifier = itemModifier,
                                    pin =
                                        CoverPin(
                                            pinned = item.id in pinnedPodcastIds,
                                            onToggle = { onTogglePin(item.id) },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier =
                    Modifier
                        .height(156.dp) // 60 + 60 + 12 + 4 + 4 = 140 + 16 (bottom padding) = 156
                        .padding(bottom = 16.dp),
            ) {
                item(key = "mixtape") {
                    MixtapeSelectorCover(
                        isSelected = selectedPodcastId == null,
                        isAnyPodcastSelected = selectedPodcastId != null,
                        onClick = { onPodcastSelected(null) },
                        modifier = Modifier.size(60.dp).animateItem(),
                    )
                }
                items(interleavedPodcasts, key = { it.id }) { podcast ->
                    SelectorCover(
                        podcast = podcast,
                        lastSeenId = lastSeenEpisodes[podcast.id],
                        isSelected = selectedPodcastId == podcast.id,
                        isAnyPodcastSelected = selectedPodcastId != null,
                        onClick = {
                            onPodcastSelected(if (selectedPodcastId == podcast.id) null else podcast.id)
                        },
                        modifier = Modifier.size(60.dp).animateItem(),
                        pin =
                            CoverPin(
                                pinned = podcast.id in pinnedPodcastIds,
                                onToggle = { onTogglePin(podcast.id) },
                            ),
                    )
                }
            }
        }

        // Pure black/white surfaces intentionally collapse low containers into the page
        // background, so only those styles need a quiet boundary around this module.
        val showPureSurfaceBorder =
            when (LocalSurfaceStyle.current) {
                SurfaceStyles.AMOLED, SurfaceStyles.PURE_WHITE, SurfaceStyles.DYNAMIC_OLED_WHITE -> true
                else -> false
            }

        // --- Dynamic Content Area ---
        Card(
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            border =
                if (showPureSurfaceBorder) {
                    BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                    )
                } else {
                    null
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(), // Animates height changes smoothly with a spring curve!
        ) {
            AnimatedContent(
                targetState = selectedPodcastId == null && subscribedPodcasts.list.size > 1,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                        fadeOut(animationSpec = tween(90))
                },
                label = "shows_mode_transition",
                modifier = Modifier.fillMaxWidth(),
            ) { isMixtapeMode ->
                if (isMixtapeMode) {
                    HomeMixModule(
                        dailyPodcasts = latestEpisodes.list,
                        subscribedPodcastCount = subscribedPodcasts.list.size,
                        completedDownloads = completedDownloads,
                        selectedMode = selectedMixMode,
                        episodePlaybackState = episodePlaybackState,
                        softExpireProgressEpisodeIds = softExpireProgressEpisodeIds,
                        currentPlayingEpisodeId = currentPlayingEpisodeId,
                        isPlaying = isPlaying,
                        downloadedEpisodeIds = downloadedEpisodeIds,
                        onModeChanged = onMixModeChanged,
                        onPlayMix = onPlayMix,
                        onEpisodeClick = onEpisodeClick,
                        onPlayEpisode = onPlayEpisode,
                        onViewDownloads = onViewDownloads,
                    )
                } else {
                    // Scenario B: Filtered State (A Specific Sub is Selected) / Scenario C: Only 1 Sub Edge Case
                    val activeId = selectedPodcastId ?: subscribedPodcasts.list.firstOrNull()?.id
                    val selectedPodcast = subscribedPodcasts.list.find { it.id == activeId }

                    if (selectedPodcast == null) {
                        Text(
                            text = "No episodes available",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 18.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .expressiveClickable { onPodcastClick(selectedPodcast) }
                                        .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    OptimizedImage(
                                        url = (selectedPodcast.imageUrl.takeIf { it.isNotEmpty() } ?: selectedPodcast.fallbackImageUrl),
                                        proxyWidth = 88,
                                        contentDescription = selectedPodcast.title,
                                        contentScale = ContentScale.Crop,
                                        modifier =
                                            Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = selectedPodcast.title,
                                                style =
                                                    MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = GoogleSansWeight.bold,
                                                        letterSpacing = (-0.4).sp,
                                                        fontSize = 17.sp,
                                                    ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            if (selectedPodcast.isRss) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                ) {
                                                    Text(
                                                        text = "RSS",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier =
                                                            Modifier.padding(
                                                                horizontal = 6.dp,
                                                                vertical = 2.dp,
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val isOldest = (selectedPodcast.preferredSort ?: "newest") == "oldest"
                                        Text(
                                            text =
                                                buildString {
                                                    append("Tap for show info • ")
                                                    append(if (isOldest) "Next Up" else "Latest Drops")
                                                },
                                            style =
                                                MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 11.5.sp,
                                                    fontWeight = GoogleSansWeight.medium,
                                                ),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Show info",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                thickness = 1.dp,
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            if ((isSelectedPodcastLoading || isSelectedRssRefreshing) &&
                                selectedPodcastEpisodes.list.isEmpty()
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BoxLoreLoader.Expressive(size = 48.dp)
                                }
                            } else if (selectedPodcastEpisodes.list.isEmpty()) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "No episodes available",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 324.dp)
                                            .nestedScroll(filteredScrollConnection)
                                            .verticalScroll(filteredScrollState),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (selectedPodcast.isRss && isSelectedRssRefreshing) {
                                        Text(
                                            text = "Refreshing RSS episodes…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    val latestTwoIds =
                                        remember(selectedPodcastEpisodes) {
                                            selectedPodcastEpisodes.list
                                                .sortedByDescending { it.publishedDate }
                                                .take(2)
                                                .map { it.id }
                                                .toSet()
                                        }
                                    selectedPodcastEpisodes.list.take(8).forEach { episode ->
                                        val state = episodePlaybackState.map[episode.id]
                                        DenseEpisodeRow(
                                            episode = episode,
                                            podcast = selectedPodcast,
                                            actions =
                                                DenseEpisodeRowActions(
                                                    onClick = { onEpisodeClick(episode, selectedPodcast, "home_filtered_latest_episodes") },
                                                    onPlay = {
                                                        onPlayEpisode(
                                                            episode,
                                                            selectedPodcast,
                                                            cx.aswin.boxlore.core.model.PlaybackEntryPoint.GENERIC,
                                                        )
                                                    },
                                                ),
                                            showPodcastTitle = false,
                                            playback =
                                                DenseEpisodeRowPlayback(
                                                    overrideStatus = state?.first,
                                                    overrideProgress = state?.second,
                                                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                                                    isPlaying = isPlaying,
                                                ),
                                            isEligibleForNewTag = episode.id in latestTwoIds,
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier =
                                            Modifier
                                                .height(40.dp)
                                                .fillMaxWidth()
                                                .expressiveClickable { onPodcastClick(selectedPodcast) },
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = "See All Episodes",
                                                style =
                                                    MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = GoogleSansWeight.bold,
                                                        letterSpacing = 0.1.sp,
                                                    ),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
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
