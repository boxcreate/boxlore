package cx.aswin.boxlore.feature.briefing

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCover
import cx.aswin.boxlore.core.designsystem.briefing.BriefingEditorialTheme
import cx.aswin.boxlore.core.designsystem.briefing.BriefingPageStatusBars
import cx.aswin.boxlore.core.designsystem.briefing.BriefingRegionMenu
import cx.aswin.boxlore.core.designsystem.briefing.briefingRegionLabel
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreIsShortPane
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneStatusBarsPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreReadingColumn
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.QueueManager
import cx.aswin.boxlore.core.prefs.BriefingEdition
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.feature.briefing.R as BriefingR
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone

@Suppress("LongParameterList") // Stable app-shell callback wiring.
@Composable
fun BriefingRoute(
    podcastRepository: PodcastRepository,
    playbackRepository: PlaybackRepository,
    queueManager: QueueManager,
    onBackClick: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeedbackClick: () -> Unit,
    userPreferences: UserPreferencesRepository,
    modifier: Modifier = Modifier,
    initialRegion: String? = null,
    bottomContentPadding: Dp = 0.dp,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: BriefingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = BriefingViewModel(
                application,
                podcastRepository,
                playbackRepository,
                queueManager,
                initialRegion,
                userPreferences,
            ) as T
        },
    )
    val state by viewModel.uiState.collectAsState()
    val defaultEdition by viewModel.defaultEdition.collectAsState(initial = BriefingEdition.FOLLOW_APP)
    BriefingScreen(
        uiState = state,
        onBackClick = onBackClick,
        onRegionSelect = viewModel::selectRegion,
        onPlayPauseClick = viewModel::togglePlayPause,
        onSeekTo = viewModel::seekTo,
        onEpisodeClick = onEpisodeClick,
        onFeedbackClick = onFeedbackClick,
        initialRegion = initialRegion,
        bottomContentPadding = bottomContentPadding,
        modifier = modifier,
        defaultEdition = defaultEdition,
        onDefaultEditionSelect = viewModel::setDefaultEdition,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
// Public screen callbacks delegate to focused components; the layout choice adds its branches.
@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
fun BriefingScreen(
    uiState: BriefingUiState,
    onBackClick: () -> Unit,
    onRegionSelect: (String) -> Unit,
    onPlayPauseClick: (Briefing, Long?) -> Unit,
    onSeekTo: (Long) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFeedbackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialRegion: String? = null,
    bottomContentPadding: Dp = 0.dp,
    defaultEdition: String = BriefingEdition.FOLLOW_APP,
    onDefaultEditionSelect: (String) -> Unit = {},
) {
    TrackScreenView("daily_briefing", send = {}) // daily_briefing_action screen_viewed is its view
    val scroll = rememberScrollState()
    // Held above the layout switch, so the story the listener is on and each layout's scroll survive
    // turning a tablet, unfolding or resizing a window.
    val listState = rememberLazyListState()
    val startScroll = rememberScrollState()
    var storyIndex by rememberSaveable { mutableIntStateOf(0) }
    var showPreferences by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val success = uiState as? BriefingUiState.Success
    var coverHeight by remember { mutableFloatStateOf(0f) }
    // The loading page already shows the bundled cover, so the status bar treats it like the edition.
    val hasCover = uiState !is BriefingUiState.Error
    LaunchedEffect(success?.briefing?.region, success?.briefing?.date) {
        success?.briefing?.let {
            AnalyticsHelper.trackDailyBriefingScreenViewed(it.region, it.date, initialRegion?.let { "notification" } ?: "home_banner")
        }
    }
    BriefingEditorialTheme {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val mode = BriefingLayoutMode.of(maxWidth.value, maxHeight.value, boxloreIsShortPane())
            val twoPane = mode == BriefingLayoutMode.TwoPane && uiState is BriefingUiState.Success
            // How far the page has scrolled past the cover, in the layout on screen.
            val coverVisible by remember(hasCover, scroll, listState, startScroll, mode) {
                derivedStateOf {
                    val scrolled = when (mode) {
                        BriefingLayoutMode.Pager -> scroll.value
                        BriefingLayoutMode.List -> if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else Int.MAX_VALUE
                        BriefingLayoutMode.TwoPane -> startScroll.value
                    }
                    hasCover && (coverHeight == 0f || scrolled < coverHeight)
                }
            }
            BriefingPageStatusBars(coverVisible = coverVisible)
            // Capped at a reading width on every window, sideways phones included; two panes get a wider frame.
            val frameWidth = if (twoPane) BRIEFING_TWO_PANE_MAX_WIDTH_DP else 720f
            Surface(Modifier.fillMaxSize().boxloreReadingColumn(frameWidth).fillMaxSize().testTag("briefing_reading_frame"), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    val masthead = briefingMastheadLeftPadding()
                    val onCoverMeasured: (Int) -> Unit = { coverHeight = it.toFloat() }
                    val layout = BriefingPageLayout(mode, storyIndex, { storyIndex = it }, listState, startScroll)
                    AnimatedContent(uiState, contentKey = { it.presentationKey() }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "briefingState") { displayedState ->
                        when (displayedState) {
                            is BriefingUiState.Loading -> BriefingLoadingContent(displayedState.selectedRegion, topPadding, masthead, onCoverMeasured)
                            is BriefingUiState.Error -> BriefingErrorContent(displayedState, topPadding, bottomContentPadding, onRegionSelect)
                            is BriefingUiState.Success -> BriefingEditionContent(
                                displayedState,
                                scroll,
                                BriefingContentActions(onRegionSelect, onPlayPauseClick, onSeekTo, onEpisodeClick, onFeedbackClick, onSources = { showSources = true }),
                                BriefingPagePadding(topPadding, bottomContentPadding, masthead),
                                onCoverMeasured,
                                layout,
                            )
                        }
                    }
                    // Back at the start and Preferences at the end, clear of a sideways phone's cutout and side buttons.
                    Box(Modifier.fillMaxWidth().boxlorePaneStatusBarsPadding().boxloreHorizontalSafePadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        BriefingTopButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.briefing_back), onBackClick, Modifier.align(Alignment.TopStart).testTag("briefingBack"))
                        BriefingTopButton(Icons.Rounded.Tune, stringResource(R.string.briefing_preferences), { showPreferences = true }, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
        if (showPreferences) {
            BriefingPreferencesSheet(defaultEdition, onSelect = { onDefaultEditionSelect(it) }, onDismiss = { showPreferences = false })
        }
        if (showSources && success != null) {
            BriefingSourcesSheet(success.briefing, onDismiss = { showSources = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BriefingPlaybackControls(state: BriefingUiState.Success, onPlay: () -> Unit, onSources: () -> Unit, onSeekTo: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Listen and Sources are one connected pair that morphs on press; with no sources Listen is a full pill.
        val hasSources = state.briefing.sources.isNotEmpty()
        val listenShape = if (hasSources) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 7.dp, bottomEnd = 7.dp) else CircleShape
        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), verticalAlignment = Alignment.CenterVertically) {
            Button(onPlay, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp).testTag("briefingListen"), shapes = ButtonDefaults.shapes(shape = listenShape, pressedShape = RoundedCornerShape(18.dp))) {
                if (state.isBuffering) {
                    BoxLoreLoader.CircularWavy(size = 22.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.isPlaying) {
                        stringResource(R.string.briefing_pause)
                    } else if (state.currentPosition > 0) {
                        stringResource(R.string.briefing_resume)
                    } else {
                        stringResource(R.string.briefing_listen)
                    },
                )
            }
            if (hasSources) {
                FilledTonalIconButton(onSources, modifier = Modifier.width(52.dp).fillMaxHeight(), shapes = IconButtonDefaults.shapes(shape = RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 28.dp, bottomEnd = 28.dp), pressedShape = RoundedCornerShape(18.dp)), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Rounded.Link, stringResource(R.string.briefing_sources))
                }
            }
        }
        if (state.duration > 0) {
            var seekPosition by remember(state.briefing.region, state.briefing.date) { mutableStateOf<Float?>(null) }
            val interactionSource = remember { MutableInteractionSource() }
            val sliderValue = seekPosition ?: (state.currentPosition.toFloat() / state.duration).coerceIn(0f, 1f)
            val sliderState = remember { SliderState(sliderValue) }
            // TalkBack hears what the slider is and where it is, "12:03 of 58:00", not just a percentage.
            val seekName = stringResource(BriefingR.string.briefing_screen_seek_name)
            val total = formatChapterTime(state.duration / 1000)
            val positionTemplate = stringResource(BriefingR.string.briefing_screen_seek_position)
            val spokenPosition = { fraction: Float -> positionTemplate.format(formatChapterTime((fraction * state.duration / 1000).toLong()), total) }
            sliderState.value = sliderValue
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Slider(
                    state = sliderState,
                    onValueChange = { seekPosition = it },
                    onValueChangeFinished = {
                        seekPosition?.let { onSeekTo((it * state.duration).toLong()) }
                        seekPosition = null
                    },
                    interactionSource = interactionSource,
                    thumb = {
                        Box(Modifier.width(12.dp).height(48.dp), contentAlignment = Alignment.Center) {
                            SliderDefaults.Thumb(interactionSource, thumbSize = DpSize(12.dp, 12.dp))
                        }
                    },
                    track = { slider ->
                        BriefingProgressTrack(progress = { slider.value }, playing = state.isPlaying)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("briefingSeekbar").semantics {
                        contentDescription = seekName
                        stateDescription = spokenPosition(sliderValue)
                    },
                )
                Text(formatRemaining(((state.duration - state.currentPosition) / 1000).coerceAtLeast(0)) ?: stringResource(R.string.briefing_finished), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BriefingProgressTrack(progress: () -> Float, playing: Boolean) {
    val stroke = Stroke(with(LocalDensity.current) { 3.dp.toPx() }, cap = StrokeCap.Round)
    LinearWavyProgressIndicator(
        progress = progress,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        stroke = stroke,
        trackStroke = stroke,
        gapSize = 4.dp,
        stopSize = 0.dp,
        amplitude = { if (playing && it > 0f && it < 1f) 0.6f else 0f },
        wavelength = 24.dp,
        waveSpeed = if (playing) 24.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().height(12.dp).clearAndSetSemantics {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingPreferencesSheet(defaultEdition: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    TrackScreenView("briefing_preferences")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.briefing_default_region), style = MaterialTheme.typography.headlineSmall, fontWeight = GoogleSansWeight.bold)
            Text(stringResource(R.string.briefing_default_region_description), modifier = Modifier.padding(top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // One choice list, read out as one, like boxlore's other region picker: solid rows, the chosen one filled, the radio at the end.
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf(BriefingEdition.FOLLOW_APP) + BriefingEdition.regions).forEach { edition ->
                    val selected = edition == defaultEdition
                    val scheme = MaterialTheme.colorScheme
                    Surface(
                        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(edition) }),
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
                        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                    ) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(if (edition == BriefingEdition.FOLLOW_APP) R.string.briefing_follow_app_region else briefingRegionLabel(edition)),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            RadioButton(selected = selected, onClick = null)
                        }
                    }
                }
            }
            TextButton(onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) { Text(stringResource(R.string.briefing_done)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingSourcesSheet(briefing: Briefing, onDismiss: () -> Unit) {
    TrackScreenView("briefing_sources")
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.briefing_sources), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            // The title stays put while a long list scrolls.
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(briefing.sources) { source ->
                    Surface(onClick = {
                        AnalyticsHelper.trackDailyBriefingInteraction("source_clicked", briefing.region, briefing.date, mapOf("source_title" to source.title, "source_url" to source.url))
                        uriHandler.openUri(source.url)
                    }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(getDomainName(source.url), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(source.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                            Icon(Icons.Rounded.ArrowOutward, null, Modifier.padding(start = 12.dp).size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class BriefingContentActions(
    val region: (String) -> Unit,
    val play: (Briefing, Long?) -> Unit,
    val seek: (Long) -> Unit,
    val episode: (Episode) -> Unit,
    val feedback: () -> Unit,
    val onSources: () -> Unit,
)

/** The page's layout and the story the listener is on, kept across layouts (pager page ↔ list position). */
private class BriefingPageLayout(val mode: BriefingLayoutMode, val storyIndex: Int, val onStoryIndex: (Int) -> Unit, val listState: LazyListState, val startScroll: ScrollState)

@Composable
private fun BriefingEditionContent(
    state: BriefingUiState.Success,
    scroll: ScrollState,
    actions: BriefingContentActions,
    padding: BriefingPagePadding,
    onCoverMeasured: (Int) -> Unit,
    layout: BriefingPageLayout,
) {
    val bottomInsets = Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
    when (layout.mode) {
        BriefingLayoutMode.Pager -> Column(
            Modifier.fillMaxWidth().verticalScroll(scroll).testTag("briefingArticle").padding(bottom = padding.bottom + 24.dp)
                // Only the bottom bar: the cover reaches the side edges, and text clears side buttons on its own.
                .then(bottomInsets),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BriefingEditionHeader(state, actions, padding, onCoverMeasured)
            BriefingStoriesPager(
                briefing = state.briefing,
                chapters = state.chapters,
                chaptersLoading = state.chaptersLoading,
                isPlaying = state.isPlaying,
                currentPositionMs = state.currentPosition,
                accentColor = MaterialTheme.colorScheme.primary,
                onEpisodeClick = actions.episode,
                onPlayPauseClick = { actions.play(state.briefing, it) },
                onShowSources = { actions.onSources() },
                initialPage = layout.storyIndex,
                onPageChange = layout.onStoryIndex,
            )
            BriefingEditionFooter(state, actions, Modifier.boxloreHorizontalSafePadding().padding(horizontal = 20.dp).boxloreCardWidth(BoxloreCardFamily.Reading).fillMaxWidth())
        }
        // Portrait tablets: the same one column, with every story listed instead of a pager.
        BriefingLayoutMode.List -> {
            val model = rememberStoryListModel(state, actions)
            // Arriving from a pager: open at the story the listener was on (the edition stays above story 1).
            FollowPlayingStory(layout.listState, model, firstStoryItem = 1, startAt = layout.storyIndex.takeIf { it > 0 }, onStoryInView = layout.onStoryIndex)
            LazyColumn(
                state = layout.listState,
                modifier = Modifier.fillMaxSize().testTag("briefingArticle"),
                contentPadding = PaddingValues(bottom = padding.bottom + 24.dp),
                verticalArrangement = BriefingStoryListSpacing,
            ) {
                item(key = "edition") { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { BriefingEditionHeader(state, actions, padding, onCoverMeasured) } }
                if (model.chapters.isEmpty()) item(key = "no_stories") { BriefingStoriesPager(state.briefing, state.chapters, state.isPlaying, state.currentPosition, MaterialTheme.colorScheme.primary, state.chaptersLoading, actions.episode, { actions.play(state.briefing, it) }) }
                briefingStoryItems(model, PaddingValues(horizontal = 20.dp))
                item(key = "footer") { BriefingEditionFooter(state, actions, Modifier.padding(horizontal = 20.dp).fillMaxWidth()) }
            }
        }
        // Wide windows: the edition at the start, every story in a list beside it.
        BriefingLayoutMode.TwoPane -> {
            val model = rememberStoryListModel(state, actions)
            FollowPlayingStory(layout.listState, model, firstStoryItem = 0, startAt = layout.storyIndex, onStoryInView = layout.onStoryIndex)
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val start = (maxWidth * 0.36f).coerceIn(BRIEFING_START_PANE_MIN_DP.dp, BRIEFING_START_PANE_MAX_DP.dp)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.width(start).fillMaxHeight().verticalScroll(layout.startScroll).testTag(BRIEFING_START_PANE_TAG).padding(bottom = padding.bottom + 24.dp).then(bottomInsets),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BriefingEditionHeader(state, actions, padding, onCoverMeasured)
                        BriefingEditionFooter(state, actions, Modifier.padding(horizontal = 20.dp).fillMaxWidth())
                    }
                    LazyColumn(
                        state = layout.listState,
                        modifier = Modifier.weight(1f).widthIn(max = BRIEFING_STORY_LIST_MAX_DP.dp).fillMaxHeight().testTag(BRIEFING_STORY_LIST_TAG),
                        contentPadding = PaddingValues(top = padding.top + 72.dp, end = 20.dp, bottom = padding.bottom + 24.dp),
                        verticalArrangement = BriefingStoryListSpacing,
                    ) {
                        if (model.chapters.isEmpty()) item(key = "no_stories") { BriefingStoriesPager(state.briefing, state.chapters, state.isPlaying, state.currentPosition, MaterialTheme.colorScheme.primary, state.chaptersLoading, actions.episode, { actions.play(state.briefing, it) }) }
                        briefingStoryItems(model)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberStoryListModel(state: BriefingUiState.Success, actions: BriefingContentActions): BriefingStoryListModel {
    val play = remember(state.briefing, actions) { { position: Long? -> actions.play(state.briefing, position) } }
    val sources = remember(actions) { { actions.onSources() } }
    return rememberBriefingStoryListModel(state.briefing, state.chapters, state.isPlaying, state.currentPosition, actions.episode, play, sources)
}

/** The cover, the date line with the edition menu, the headline, Listen and Sources, and the seek bar. */
@Composable
private fun BriefingEditionHeader(state: BriefingUiState.Success, actions: BriefingContentActions, padding: BriefingPagePadding, onCoverMeasured: (Int) -> Unit) {
    BriefingCover(
        region = state.briefing.region,
        editionDate = state.briefing.date,
        modifier = Modifier.briefingPageCoverSize().onSizeChanged { onCoverMeasured(it.height) }.clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).testTag("briefingPageCover"),
        mastheadTopPadding = padding.top + 12.dp,
        mastheadScrim = true,
        mastheadLeftPadding = padding.mastheadLeft,
        artworkContentScale = briefingCoverScale(),
    )
    Column(Modifier.boxloreHorizontalSafePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val locale = currentLocale()
            val date = remember(state.briefing.date, locale) { briefingDisplayDate(state.briefing.date, locale) }
            val stories = state.chapters.takeIf { it.isNotEmpty() }?.let {
                pluralStringResource(R.plurals.briefing_story_count, it.size, it.size)
            }
            Text(
                listOfNotNull(date, stories, formatBriefingListenDuration(state.duration)?.asString()).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Wraps rather than cutting the listen time with large text or in German.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("briefingMeta"),
            )
            BriefingRegionMenu(state.selectedRegion, onSelect = { region ->
                AnalyticsHelper.trackDailyBriefingRegionChanged(state.selectedRegion, region, state.briefing.date)
                actions.region(region)
            })
        }
        Text(
            state.briefing.title,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.4).sp
        )
        BriefingPlaybackControls(state, onPlay = {
            if (state.isPlaying) {
                AnalyticsHelper.trackDailyBriefingPauseClicked(state.briefing.region, state.briefing.date, "briefing_detail")
            } else {
                AnalyticsHelper.trackDailyBriefingPlayClicked(state.briefing.region, state.briefing.date, "briefing_detail")
            }
            actions.play(state.briefing, null)
        }, onSources = {
            AnalyticsHelper.trackDailyBriefingInteraction("sources_sheet_opened", state.briefing.region, state.briefing.date, mapOf("sources_count" to state.briefing.sources.size))
            actions.onSources()
        }, onSeekTo = actions.seek)
    }
}

/** Original reporting, the AI note and Report an issue. */
@Composable
private fun BriefingEditionFooter(state: BriefingUiState.Success, actions: BriefingContentActions, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.briefing.sources.isNotEmpty()) {
                Surface(onClick = { actions.onSources() }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.briefing_original_reporting), style = MaterialTheme.typography.titleSmall)
                            Text(pluralStringResource(R.plurals.briefing_source_count, state.briefing.sources.size, state.briefing.sources.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ArrowOutward, stringResource(R.string.briefing_open_sources), Modifier.size(20.dp))
                    }
                }
            }
            Text(stringResource(R.string.briefing_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = actions.feedback, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.briefing_report_issue)) }
        }
    }
}

private fun BriefingUiState.presentationKey(): String = when (this) {
    is BriefingUiState.Loading -> "loading"
    is BriefingUiState.Error -> "error"
    is BriefingUiState.Success -> "success"
}

/** The brief's `yyyy-MM-dd` [date] as a short day and month in [locale] ("Nov 20", "20. Nov."); as sent when unreadable. */
internal fun briefingDisplayDate(date: String, locale: Locale): String = runCatching {
    val millis = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    LocaleFormats.date(millis, LocaleFormats.DateStyle.DAY_MONTH, locale, TimeZone.getTimeZone("UTC"))
}.getOrDefault(date)
