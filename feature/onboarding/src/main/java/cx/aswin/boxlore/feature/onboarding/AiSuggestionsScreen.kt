package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.RegionSegmentedSelector
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
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
    val loading = OnboardingSuggestionsPresentation.isLoading(uiState)
    val error = OnboardingSuggestionsPresentation.isError(uiState)
    val picksTitle = stringResource(R.string.suggestions_picks_lane)
    val lanes = remember(uiState.aiCurriculumRows, uiState.genreChartsPodcasts, uiState.selectedGenres, uiState.aiPicks, origin, picksTitle) {
        OnboardingSuggestionsPresentation.lanes(uiState, picksTitle)
    }
    var details by remember { mutableStateOf<Podcast?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex > 0 }.first { it }
        callbacks.onScrolled()
    }
    val choosing = SimilarShowsChoice.showing(uiState)
    val footer = when {
        choosing -> SuggestionsFooterState.Choice(homeFirst = SimilarShowsChoice.homeIsPrimary(uiState))
        loading -> SuggestionsFooterState.Loading
        error || lanes.isEmpty() -> SuggestionsFooterState.Retry
        uiState.isCompleting -> SuggestionsFooterState.Adding
        else -> SuggestionsFooterState.Finish(OnboardingSuggestionsPresentation.addCount(uiState))
    }
    Scaffold(
        modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility).fillMaxSize().testTag("suggestions_screen"),
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
                error || lanes.isEmpty() -> SuggestionsProblem(error)
                else -> LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize().testTag("suggestions_lanes"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    item(key = "header") { SuggestionsHeader(uiState, origin) }
                    lanes.forEachIndexed { index, lane ->
                        item(key = lane.id) {
                            SuggestionLane(lane, index, uiState, callbacks, onDetails = { details = it })
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
            onDismiss = { details = null },
            onToggleSubscription = callbacks.onToggle,
        )
    }
}

@Composable
private fun SuggestionsTopBar(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
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
                lane.title,
                Modifier.weight(1f).padding(start = 12.dp, end = 4.dp).semantics { heading() },
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
            RegionSegmentedSelector(activeRegion = uiState.currentRegion, onSwitchRegion = callbacks.onRegionChange, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        }
        if (lane.isCharts && uiState.isLoadingPodcasts) {
            SuggestionCardsSkeleton(Modifier.padding(top = 12.dp))
        } else {
            LazyRow(
                Modifier.fillMaxWidth().padding(top = 12.dp),
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
private fun SuggestionsProblem(error: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp).testTag("suggestions_problem"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(if (error) Icons.Rounded.CloudOff else Icons.Rounded.Grain, null, Modifier.size(32.dp), tint = colors.onSecondaryContainer)
        }
        Text(
            stringResource(if (error) R.string.suggestions_error_title else R.string.suggestions_empty_title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(if (error) R.string.suggestions_error_body else R.string.suggestions_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The one sticky button. Nothing selected: a tonal "Start without adding". Shows selected: a
 * filled "Add N shows". Loading and adding keep the button in place and say what is happening;
 * a failed load offers "Try again" with "Start without adding" above it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SuggestionsFooter(state: SuggestionsFooterState, actions: SuggestionsFooterActions) {
    val (container, content) = suggestionsButtonColors(state)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp).testTag("suggestions_footer"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SuggestionsSecondaryAction(state, actions)
        Button(
            onClick = {
                when (state) {
                    SuggestionsFooterState.Retry -> actions.onRetry()
                    is SuggestionsFooterState.Choice -> if (state.homeFirst) actions.onGoHome() else actions.onSeeSimilar()
                    else -> actions.onFinish()
                }
            },
            enabled = state !is SuggestionsFooterState.Loading && state !is SuggestionsFooterState.Adding,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("suggestions_finish"),
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(28.dp), pressedShape = RoundedCornerShape(18.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = container, disabledContentColor = content),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            AnimatedContent(targetState = state, transitionSpec = EchoMotion.handoff(), contentAlignment = Alignment.Center, label = "Suggestions button label") { target ->
                SuggestionsFooterLabel(target)
            }
        }
    }
}

/** What the footer's buttons do; [canSkipToHome] adds "Skip to Home" while search or import suggestions load. */
internal data class SuggestionsFooterActions(
    val onFinish: () -> Unit,
    val onRetry: () -> Unit,
    val onSeeSimilar: () -> Unit,
    val onGoHome: () -> Unit,
    val canSkipToHome: Boolean,
)

/** The quieter way on, above the main button: start without, the other choice, or skip a load. */
@Composable
private fun SuggestionsSecondaryAction(state: SuggestionsFooterState, actions: SuggestionsFooterActions) {
    val (label, onClick, tag) = when {
        state is SuggestionsFooterState.Retry -> Triple(R.string.suggestions_start_without, actions.onFinish, "suggestions_start_without")
        state is SuggestionsFooterState.Choice && state.homeFirst -> Triple(R.string.similar_choice_see, actions.onSeeSimilar, "similar_choice_similar")
        state is SuggestionsFooterState.Choice -> Triple(R.string.similar_choice_home, actions.onGoHome, "similar_choice_home")
        state is SuggestionsFooterState.Loading && actions.canSkipToHome -> Triple(R.string.similar_skip_home, actions.onGoHome, "suggestions_skip_home")
        else -> return
    }
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 4.dp).testTag(tag)) {
        Text(stringResource(label))
    }
}

/** Busy reads tonal and alive; nothing selected is a quieter tonal; something to add is filled. */
@Composable
private fun suggestionsButtonColors(state: SuggestionsFooterState): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val busy = state is SuggestionsFooterState.Loading || state is SuggestionsFooterState.Adding
    val filled = state is SuggestionsFooterState.Retry || state is SuggestionsFooterState.Choice || (state is SuggestionsFooterState.Finish && state.count > 0)
    val container by animateColorAsState(
        when {
            busy -> colors.primaryContainer
            filled -> colors.primary
            else -> colors.secondaryContainer
        },
        label = "Suggestions button",
    )
    val content by animateColorAsState(
        when {
            busy -> colors.onPrimaryContainer
            filled -> colors.onPrimary
            else -> colors.onSecondaryContainer
        },
        label = "Suggestions button text",
    )
    return container to content
}

@Composable
private fun SuggestionsFooterLabel(state: SuggestionsFooterState) {
    when (state) {
        SuggestionsFooterState.Loading -> EchoThinkingLabel(stringArrayResource(R.array.suggestions_finding_words))
        SuggestionsFooterState.Adding -> EchoThinkingLabel(arrayOf(stringResource(R.string.suggestions_adding)))
        SuggestionsFooterState.Retry -> Text(stringResource(R.string.suggestions_try_again), style = MaterialTheme.typography.titleMedium)
        is SuggestionsFooterState.Choice -> Text(
            stringResource(if (state.homeFirst) R.string.similar_choice_home else R.string.similar_choice_see),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        is SuggestionsFooterState.Finish -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.count > 0) pluralStringResource(R.plurals.suggestions_add_shows, state.count, state.count) else stringResource(R.string.suggestions_start_without),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}
