package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.RegionSegmentedSelector
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import kotlinx.coroutines.flow.first

/** The suggestions screen's main button. */
internal sealed interface SuggestionsFooterState {
    data object Loading : SuggestionsFooterState

    data object Retry : SuggestionsFooterState

    data object Adding : SuggestionsFooterState

    data class Finish(val count: Int) : SuggestionsFooterState

    /** Search and import, before any request: see similar shows or go to Home. */
    data class Choice(val homeFirst: Boolean) : SuggestionsFooterState
}

/** The suggestions screen's actions, grouped so the route wiring stays readable. */
internal data class SuggestionsCallbacks(
    val onBack: () -> Unit,
    val onToggle: (String) -> Unit,
    val onSetShowsSelected: (List<Podcast>, Boolean) -> Unit,
    val onRegionChange: (String) -> Unit,
    val onRetry: () -> Unit,
    val onFinish: () -> Unit,
    val onScrolled: () -> Unit = {},
    /** Search and import only: ask for similar shows, or go to Home (also while they load). */
    val onSeeSimilar: () -> Unit = {},
    val onGoHome: () -> Unit = {},
)

/**
 * The shared suggestions screen, reached from Echo, genres, search and library import. One
 * vertical list of lanes, each a row of shows you scroll sideways: every lane is visible at a
 * glance. Nothing starts selected except shows the listener picked themselves (Echo's "Your picks"
 * lane; search picks and imported libraries, already in the library, are summed up at the top).
 * One sticky button: "Start without adding", or "Add N shows" once anything is selected.
 */
@Composable
internal fun AiSuggestionsScreen(uiState: OnboardingUiState, callbacks: SuggestionsCallbacks) {
    val origin = OnboardingSuggestionsPresentation.origin(uiState)
    val showHeader = suggestionsHeaderShows(origin, boxloreIsShortPane())
    val loading = OnboardingSuggestionsPresentation.isLoading(uiState)
    val error = OnboardingSuggestionsPresentation.isError(uiState)
    val lanes = remember(uiState.aiCurriculumRows, uiState.genreChartsPodcasts, uiState.selectedGenres, uiState.aiPicks, origin) {
        OnboardingSuggestionsPresentation.lanes(uiState)
    }
    // Kept by id, so the details sheet stays open through a fold, turn or display-size change.
    var detailsId by rememberSaveable { mutableStateOf<String?>(null) }
    val details = detailsId?.let { id -> lanes.firstNotNullOfOrNull { lane -> lane.podcasts.find { it.id == id } } ?: uiState.selectedPodcasts[id] }
    val wide = LocalBoxloreWindowLayout.current.widthDp >= SuggestionGridWindowWidth
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex > 0 }.first { it }
        callbacks.onScrolled()
    }
    val choosing = SimilarShowsChoice.showing(uiState)
    val footer = suggestionsFooterState(uiState, loading, failed = error || lanes.isEmpty())
    Scaffold(
        modifier = Modifier.boxloreCardWidth(if (wide) BoxloreCardFamily.Grid else BoxloreCardFamily.Utility).fillMaxSize().testTag("suggestions_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { SuggestionsTopBar(callbacks.onBack) },
        bottomBar = {
            SuggestionsFooter(
                footer,
                SuggestionsFooterActions(
                    onFinish = callbacks.onFinish,
                    onRetry = callbacks.onRetry,
                    onSeeSimilar = callbacks.onSeeSimilar,
                    onGoHome = callbacks.onGoHome,
                    canSkipToHome = SimilarShowsChoice.canSkipLoading(uiState, loading),
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                choosing -> SimilarShowsChoiceContent(uiState)
                loading -> SuggestionsLoading(uiState, origin)
                error || lanes.isEmpty() -> SuggestionsProblem(error, uiState.onboardingErrorKind)
                else -> LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().testTag("suggestions_lanes"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    if (showHeader) item(key = "header") { SuggestionsHeader(uiState, origin) }
                    lanes.forEachIndexed { index, lane ->
                        item(key = lane.id) {
                            SuggestionLane(lane, index, uiState, callbacks, onDetails = { detailsId = it.id })
                        }
                    }
                }
            }
        }
    }
    details?.let { podcast ->
        SuggestionPodcastDetailSheet(
            podcast = podcast,
            isSubscribed = podcast.id in uiState.subscribedPodcastIds,
            onDismiss = { detailsId = null },
            onToggleSubscription = callbacks.onToggle,
        )
    }
}

@Composable
private fun SuggestionsTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(if (boxloreIsShortPane()) 56.dp else 64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.suggestions_back)) }
        Text(
            stringResource(R.string.suggestions_title),
            Modifier.padding(start = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * What the listener sees first. Echo's flow: Echo beside a bubble. Search and import: a summary
 * card of the shows already in the library. Both say "tap to add, hold for details" in passing,
 * so no line of space goes to it.
 */
@Composable
private fun SuggestionsHeader(uiState: OnboardingUiState, origin: SuggestionsOrigin) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (origin) {
            SuggestionsOrigin.Echo -> EchoSays(uiState, EchoScreenKind.Ready, SuggestionsEchoWidth, stringResource(R.string.suggestions_echo_found))
            SuggestionsOrigin.Search, SuggestionsOrigin.Library -> SuggestionsSeedSummary(uiState, origin)
            SuggestionsOrigin.Genres -> Unit
        }
    }
}

private val SuggestionsEchoWidth = 96.dp

/**
 * Whether the list opens with the header. Short windows (sideways phones, tabletop halves) leave
 * Echo's line out, so the first row of shows and their names fit above the footer; the top bar
 * still names the screen.
 */
internal fun suggestionsHeaderShows(origin: SuggestionsOrigin, shortPane: Boolean): Boolean = when (origin) {
    SuggestionsOrigin.Echo -> !shortPane
    SuggestionsOrigin.Search, SuggestionsOrigin.Library -> true
    SuggestionsOrigin.Genres -> false
}

/** The search picks or imported shows, already in the library: a few covers and a count. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SuggestionsSeedSummary(uiState: OnboardingUiState, origin: SuggestionsOrigin) {
    val seeds = OnboardingSuggestionsPresentation.seedShows(uiState)
    val count = uiState.suggestionSeedCount.takeIf { it > 0 } ?: seeds.size
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().testTag("suggestions_seed_summary"),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (seeds.isEmpty()) {
                Box(Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.LibraryAddCheck, null, tint = colors.onPrimary)
                }
            } else {
                // Overlapping covers, like a small stack of the shows already added.
                Box(Modifier.width(48.dp + 18.dp * (seeds.take(4).size - 1)).height(48.dp)) {
                    seeds.take(4).forEachIndexed { index, podcast ->
                        OptimizedImage(
                            url = podcast.imageUrl,
                            fallbackUrls = podcast.artworkUrls(),
                            proxyWidth = 120,
                            contentDescription = null,
                            modifier = Modifier.offset(x = 18.dp * index).size(48.dp).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerHigh),
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (origin == SuggestionsOrigin.Library) {
                        pluralStringResource(R.plurals.suggestions_library_seeds, count, count)
                    } else {
                        pluralStringResource(R.plurals.suggestions_search_seeds, count, count)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold),
                )
                Text(stringResource(R.string.suggestions_more_like), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * One lane: an expressive-shape icon, its title, "Add all" / "Remove all", then the
 * shows in a sideways row. The charts lane adds the region picker.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionLane(lane: OnboardingSuggestionsLane, index: Int, uiState: OnboardingUiState, callbacks: SuggestionsCallbacks, onDetails: (Podcast) -> Unit) {
    val selectedIds = uiState.subscribedPodcastIds

    val colors = MaterialTheme.colorScheme
    val picks = lane.id == OnboardingSuggestionsPresentation.PICKS_LANE_ID
    val shows = lane.podcasts
    val allSelected = shows.isNotEmpty() && shows.all { it.id in selectedIds }
    Column(Modifier.fillMaxWidth().testTag("suggestions_lane_${lane.id}")) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(SuggestionLaneShapes[index % SuggestionLaneShapes.size].toShape()).background(if (picks) colors.primary else colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(suggestionLaneIcon(lane, index), null, Modifier.size(20.dp), tint = if (picks) colors.onPrimary else colors.onSecondaryContainer)
            }
            Text(
                lane.title.asString(),
                Modifier.weight(1f).padding(start = SuggestionLaneTitleStart - 60.dp, end = 4.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (shows.size >= 2) {
                TextButton(onClick = { callbacks.onSetShowsSelected(shows, !allSelected) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (allSelected) R.string.suggestions_remove_all else R.string.suggestions_add_all))
                }
            }
        }
        if (lane.isCharts) {
            // A small country chip on its own line, lined up with the lane title.
            RegionSegmentedSelector(
                activeRegion = uiState.currentRegion,
                onSwitchRegion = callbacks.onRegionChange,
                modifier = Modifier.padding(start = SuggestionLaneTitleStart, end = 20.dp).testTag("suggestions_region"),
                compact = true,
            )
        }
        if (lane.isCharts && uiState.isLoadingPodcasts) {
            SuggestionCardsSkeleton(Modifier.padding(top = 12.dp))
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (maxWidth >= SuggestionGridMinWidth) {
                    SuggestionLaneGrid(lane, index, shows, selectedIds, maxWidth, callbacks.onToggle, onDetails)
                } else {
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(shows, key = { _, show -> LazyListKeyPolicy.safeKey(show.id, prefix = "suggestion_${lane.id}") }) { position, show ->
                            SuggestionShowCard(show, show.id in selectedIds, shapeIndex = index * 3 + position, onToggle = callbacks.onToggle, onOpenDetails = onDetails)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A lane on a wide column: its shows in rows, as many across as fit, two rows deep, then "Show all"
 * for the rest. Whether a lane is open is kept through window changes.
 */
@Composable
@Suppress("LongParameterList")
private fun SuggestionLaneGrid(
    lane: OnboardingSuggestionsLane,
    index: Int,
    shows: List<Podcast>,
    selectedIds: Set<String>,
    width: Dp,
    onToggle: (String) -> Unit,
    onDetails: (Podcast) -> Unit,
) {
    var open by rememberSaveable(lane.id) { mutableStateOf(false) }
    val columns = suggestionGridColumns(width.value)
    val shown = if (open) shows else shows.take(columns * SuggestionGridRows)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("suggestions_grid_${lane.id}"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        shown.chunked(columns).forEachIndexed { row, cards ->
            Row(horizontalArrangement = Arrangement.spacedBy(SuggestionGridGap)) {
                cards.forEachIndexed { column, show ->
                    key(show.id) {
                        SuggestionShowCard(show, show.id in selectedIds, shapeIndex = index * 3 + row * columns + column, onToggle = onToggle, onOpenDetails = onDetails)
                    }
                }
            }
        }
        if (shown.size < shows.size) {
            TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("suggestions_show_all_${lane.id}")) {
                Text(pluralStringResource(R.plurals.suggestions_show_all, shows.size, shows.size))
            }
        }
    }
}

/** How many 140dp cards fit across a [widthDp] lane, 12dp apart inside its 20dp sides. */
internal fun suggestionGridColumns(widthDp: Float): Int =
    ((widthDp - SuggestionGridSides + SuggestionGridGap.value) / (SuggestionCardWidth.value + SuggestionGridGap.value)).toInt().coerceAtLeast(1)

/** Lanes this wide (dp) set their shows in rows; narrower ones scroll sideways. */
private val SuggestionGridMinWidth = 600.dp

/** Windows this wide (dp) widen the page to the grid column. */
private const val SuggestionGridWindowWidth = 840f
private const val SuggestionGridRows = 2
private const val SuggestionGridSides = 40f
private val SuggestionGridGap = 12.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SuggestionLaneShapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Puffy, MaterialShapes.SoftBurst)

private fun suggestionLaneIcon(lane: OnboardingSuggestionsLane, index: Int): ImageVector = when {
    lane.id == OnboardingSuggestionsPresentation.PICKS_LANE_ID -> Icons.Rounded.Favorite
    lane.isCharts -> Icons.AutoMirrored.Rounded.TrendingUp
    else -> listOf(Icons.Rounded.AutoAwesome, Icons.Rounded.Star, Icons.Rounded.Bookmark, Icons.Rounded.Grain)[index % 4]
}

/**
 * Loading, in the loaded geometry: Echo beside its bubble (with the slow-build lines) in Echo's
 * flow, or one line saying what is being found, then skeleton lanes. The button says it is busy.
 */
@Composable
private fun SuggestionsLoading(uiState: OnboardingUiState, origin: SuggestionsOrigin) {
    Column(Modifier.fillMaxSize().testTag("suggestions_loading"), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        Box(Modifier.padding(horizontal = 20.dp)) {
            if (origin == SuggestionsOrigin.Echo) {
                val key = if (uiState.isSynthesizing) "build:${uiState.aiHistory.size}" else null
                val wait = rememberEchoWaitCopy(EchoWaitStep.forBuild(uiState.aiLoadingStage), key)
                EchoSays(uiState, EchoScreenKind.Waiting, SuggestionsEchoWidth, wait?.line ?: stringResource(R.string.suggestions_echo_finding), wait?.detail)
            } else {
                Text(
                    stringResource(
                        when (origin) {
                            SuggestionsOrigin.Search -> R.string.suggestions_loading_search
                            SuggestionsOrigin.Library -> R.string.suggestions_loading_library
                            else -> R.string.suggestions_loading_genres
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        repeat(3) { SuggestionLaneSkeleton() }
    }
}

@Composable
private fun SuggestionLaneSkeleton() {
    ShimmerScope(active = true) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                EchoSkeleton(Modifier.size(40.dp), CircleShape)
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EchoSkeleton(Modifier.width(160.dp).height(16.dp), RoundedCornerShape(6.dp))
                    EchoSkeleton(Modifier.width(220.dp).height(12.dp), RoundedCornerShape(6.dp))
                }
            }
            SuggestionCardsSkeleton(Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun SuggestionCardsSkeleton(modifier: Modifier = Modifier) {
    ShimmerScope(active = true) {
        Row(modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) { EchoSkeleton(Modifier.size(SuggestionCardWidth), RoundedCornerShape(22.dp)) }
        }
    }
}

/** A failed or empty load: what happened and what to do; the buttons below offer both ways on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionsProblem(error: Boolean, kind: OnboardingErrorKind) {
    val skipped = error && kind == OnboardingErrorKind.VerificationSkipped
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("suggestions_problem"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(
                when {
                    skipped -> Icons.Rounded.Shield
                    error -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.Grain
                },
                null,
                Modifier.size(32.dp),
                tint = colors.onSecondaryContainer,
            )
        }
        Text(
            stringResource(
                when {
                    skipped -> R.string.suggestions_check_title
                    error -> R.string.suggestions_error_title
                    else -> R.string.suggestions_empty_title
                },
            ),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(
                when {
                    skipped -> R.string.suggestions_check_body
                    error -> R.string.suggestions_error_body
                    else -> R.string.suggestions_empty_body
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Where a lane's title starts: the 20dp edge, its 40dp icon and a 12dp gap. */
private val SuggestionLaneTitleStart = 72.dp
