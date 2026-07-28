package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.RegionSegmentedSelector
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.core.model.Podcast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSuggestionsScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onToggleSubscription: (String) -> Unit,
    onToggleRowSubscriptions: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
) {
    val isLoading =
        uiState.isLoadingPodcasts &&
            uiState.aiCurriculumRows.isEmpty() &&
            uiState.genreChartsPodcasts.isEmpty()
    val isError =
        uiState.onboardingError != null &&
            uiState.aiCurriculumRows.isEmpty() &&
            uiState.genreChartsPodcasts.isEmpty()

    val lanes =
        remember(uiState.aiCurriculumRows, uiState.genreChartsPodcasts, uiState.selectedGenres) {
            OnboardingSuggestionsLanes.build(
                curriculumRows = uiState.aiCurriculumRows,
                chartsPodcasts = uiState.genreChartsPodcasts,
                selectedGenres = uiState.selectedGenres,
            )
        }

    var selectedLaneIndex by rememberSaveable(lanes.map { it.id }) { mutableIntStateOf(0) }
    val safeIndex = OnboardingSuggestionsLanes.clampIndex(selectedLaneIndex, lanes.size)
    val activeLane = lanes.getOrNull(safeIndex)

    var detailPodcast by remember { mutableStateOf<Podcast?>(null) }
    val selectedCount = uiState.subscribedPodcastIds.size

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Designed for you",
                        fontWeight = GoogleSansWeight.bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .expressiveClickable(shape = CircleShape) { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
        bottomBar = {
            if (!isLoading && !isError) {
                SuggestionsFinishBar(
                    uiState = uiState,
                    selectedCount = selectedCount,
                    isCompleting = uiState.isCompleting,
                    onFinish = onFinish,
                )
            }
        },
    ) { innerPadding ->
        when {
            isLoading -> SuggestionsLoadingState(uiState = uiState, modifier = Modifier.padding(innerPadding))
            isError ->
                SuggestionsErrorState(
                    message = requireNotNull(uiState.onboardingError),
                    onRetry = onRetry,
                    modifier = Modifier.padding(innerPadding),
                )
            activeLane == null ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No suggestions yet. Try again.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SuggestionsIntroHeader()
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SuggestionsLaneChipRow(
                            lanes = lanes,
                            selectedIndex = safeIndex,
                            subscribedIds = uiState.subscribedPodcastIds,
                            onSelect = { selectedLaneIndex = it },
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AnimatedContent(
                            targetState = activeLane.id,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "laneHeader",
                        ) {
                            val lane = lanes.firstOrNull { l -> l.id == it } ?: activeLane
                            SuggestionsActiveLaneHeader(
                                lane = lane,
                                selectedInLane =
                                    OnboardingSuggestionsLanes.selectedCountInLane(
                                        lane,
                                        uiState.subscribedPodcastIds,
                                    ),
                                onToggleAll = {
                                    if (!lane.isCharts) {
                                        onToggleRowSubscriptions(lane.title)
                                    } else {
                                        val allSelected =
                                            lane.podcasts.isNotEmpty() &&
                                                lane.podcasts.all { it.id in uiState.subscribedPodcastIds }
                                        lane.podcasts.forEach { podcast ->
                                            val isSelected = podcast.id in uiState.subscribedPodcastIds
                                            if (allSelected == isSelected) {
                                                onToggleSubscription(podcast.id)
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (activeLane.isCharts) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            RegionSegmentedSelector(
                                activeRegion = uiState.currentRegion,
                                onSwitchRegion = onRegionChange,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        if (uiState.isLoadingPodcasts) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BoxLoreLoader.Expressive(size = 48.dp)
                                }
                            }
                        }
                    }

                    if (!activeLane.isCharts || !uiState.isLoadingPodcasts) {
                        if (activeLane.podcasts.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SuggestionsEmptyLane()
                            }
                        } else {
                            items(activeLane.podcasts, key = { it.id }) { podcast ->
                                SuggestionSelectCard(
                                    podcast = podcast,
                                    isSubscribed = podcast.id in uiState.subscribedPodcastIds,
                                    onToggleSubscription = onToggleSubscription,
                                    onOpenDetails = { detailPodcast = it },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    detailPodcast?.let { podcast ->
        SuggestionPodcastDetailSheet(
            podcast = podcast,
            isSubscribed = podcast.id in uiState.subscribedPodcastIds,
            onDismiss = { detailPodcast = null },
            onToggleSubscription = onToggleSubscription,
        )
    }
}

@Composable
private fun SuggestionsIntroHeader() {
    Text(
        text = "Pick a few shows to start",
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontFamily = rememberSectionHeaderFontFamily(),
            ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun SuggestionsLaneChipRow(
    lanes: List<OnboardingSuggestionsLane>,
    selectedIndex: Int,
    subscribedIds: Set<String>,
    onSelect: (Int) -> Unit,
) {
    // Single horizontal row — long titles truncate instead of stacking into a tall chip wall.
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        itemsIndexed(lanes, key = { _, lane -> lane.id }) { index, lane ->
            val selected = index == selectedIndex
            val count = OnboardingSuggestionsLanes.selectedCountInLane(lane, subscribedIds)
            SuggestionsLaneChip(
                title = lane.title,
                selected = selected,
                selectedCount = count,
                icon = laneIcon(lane, index),
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun SuggestionsLaneChip(
    title: String,
    selected: Boolean,
    selectedCount: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh
    val content = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    val border =
        if (selected) {
            BorderStroke(1.5.dp, scheme.primary)
        } else {
            BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.45f))
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (selected) scheme.primary else content,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.semiBold,
                maxLines = 1,
            )
            if (selectedCount > 0) {
                SuggestionsLaneCountBadge(selected = selected, count = selectedCount)
            }
        }
    }
}

@Composable
private fun SuggestionsLaneCountBadge(
    selected: Boolean,
    count: Int,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.secondaryContainer,
        contentColor = if (selected) scheme.onPrimary else scheme.onSecondaryContainer,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = GoogleSansWeight.bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun SuggestionsActiveLaneHeader(
    lane: OnboardingSuggestionsLane,
    selectedInLane: Int,
    onToggleAll: () -> Unit,
) {
    val allSelected = lane.podcasts.isNotEmpty() && selectedInLane == lane.podcasts.size
    // Compact toolbar: purpose + Select all only (title already on the active chip).
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lane.purpose,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onToggleAll,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = if (allSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (allSelected) "Clear" else "Select all",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
    }
}

@Composable
private fun SuggestionsEmptyLane() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Nothing in this lane right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionsFinishBar(
    uiState: OnboardingUiState,
    selectedCount: Int,
    isCompleting: Boolean,
    onFinish: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            val cta = suggestionsFinishCtaLabel(uiState, selectedCount)

            FilledTonalButton(
                onClick = onFinish,
                enabled = !isCompleting,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                if (isCompleting) {
                    BoxLoreLoader.Expressive(size = 28.dp)
                } else {
                    Text(
                        text = cta,
                        fontWeight = GoogleSansWeight.bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

internal fun suggestionsFinishCtaLabel(
    uiState: OnboardingUiState,
    selectedCount: Int,
): String {
    if (!uiState.reachedSuggestionsViaSearchFlow) {
        return if (selectedCount > 0) {
            "Subscribe & start · $selectedCount"
        } else {
            "Start without subscribing"
        }
    }
    val recommendedIds =
        uiState.aiCurriculumRows
            .flatMap { it.podcasts }
            .map { it.id.toString() }
            .toSet()
    val selectedRecommendationsCount = uiState.subscribedPodcastIds.count { it in recommendedIds }
    return if (selectedRecommendationsCount > 0) {
        "Subscribe & start · +$selectedRecommendationsCount recommended"
    } else {
        "Start without subscribing"
    }
}

@Composable
private fun SuggestionsLoadingState(
    uiState: OnboardingUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            BoxLoreLoader.Expressive(size = 80.dp)
            Text(
                text =
                    when {
                        uiState.reachedSuggestionsViaOpmlFlow ->
                            "Your OPML shows are subscribed!\nGathering new shows inspired by your library…"
                        uiState.reachedSuggestionsViaSearchFlow ->
                            "Subscribed to ${uiState.selectedPodcasts.size} shows!\nFinding similar shows you might love…"
                        else -> "Synthesizing your feed…"
                    },
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = GoogleSansWeight.bold,
                        textAlign = TextAlign.Center,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SuggestionsErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

private fun laneIcon(
    lane: OnboardingSuggestionsLane,
    index: Int,
): ImageVector {
    if (lane.isCharts) return Icons.AutoMirrored.Rounded.TrendingUp
    return when (index % 4) {
        0 -> Icons.Rounded.AutoAwesome
        1 -> Icons.Rounded.Star
        2 -> Icons.Rounded.Bookmark
        else -> Icons.Rounded.Grain
    }
}
