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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import cx.aswin.boxlore.core.designsystem.briefing.BRIEFING_COVER_ASPECT_RATIO
import cx.aswin.boxlore.core.designsystem.briefing.BriefingCover
import cx.aswin.boxlore.core.designsystem.briefing.BriefingEditorialTheme
import cx.aswin.boxlore.core.designsystem.briefing.BriefingPageStatusBars
import cx.aswin.boxlore.core.designsystem.briefing.BriefingRegionMenu
import cx.aswin.boxlore.core.designsystem.briefing.briefingRegionLabel
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.QueueManager
import cx.aswin.boxlore.core.prefs.BriefingEdition
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
@Suppress("LongParameterList") // Public screen callbacks delegate to focused components.
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
    val scroll = rememberScrollState()
    var showPreferences by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val success = uiState as? BriefingUiState.Success
    var coverHeight by remember { mutableFloatStateOf(0f) }
    val hasCover = success != null
    val coverVisible by remember(hasCover, scroll) {
        derivedStateOf { hasCover && (coverHeight == 0f || scroll.value < coverHeight) }
    }
    BriefingPageStatusBars(coverVisible = coverVisible)
    LaunchedEffect(success?.briefing?.region, success?.briefing?.date) {
        success?.briefing?.let {
            AnalyticsHelper.trackDailyBriefingScreenViewed(it.region, it.date, initialRegion?.let { "notification" } ?: "home_banner")
        }
    }
    BriefingEditorialTheme {
        Surface(modifier.fillMaxSize().onSizeChanged { coverHeight = it.width / BRIEFING_COVER_ASPECT_RATIO }, color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(uiState, contentKey = { it.presentationKey() }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "briefingState") { displayedState ->
                    when (displayedState) {
                        is BriefingUiState.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            BoxLoreLoader.Expressive(size = 48.dp)
                            Text(stringResource(R.string.briefing_loading_edition, stringResource(briefingRegionLabel(displayedState.selectedRegion))), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        is BriefingUiState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(displayedState.message, style = MaterialTheme.typography.bodyLarge)
                            Button(onClick = { onRegionSelect(displayedState.selectedRegion) }, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.briefing_try_again)) }
                            BriefingRegionMenu(displayedState.selectedRegion, onRegionSelect)
                        }
                        is BriefingUiState.Success -> BriefingEditionContent(
                            displayedState,
                            scroll,
                            BriefingContentActions(onRegionSelect, onPlayPauseClick, onSeekTo, onEpisodeClick, onFeedbackClick, onSources = { showSources = true }),
                            topPadding,
                            bottomContentPadding,
                        )
                    }
                }
                Row(
                    Modifier.align(AbsoluteAlignment.TopRight).statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        IconButton(onBackClick, Modifier.testTag("briefingBack")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.briefing_back)) }
                    }
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        IconButton(onClick = { showPreferences = true }) { Icon(Icons.Rounded.Tune, stringResource(R.string.briefing_preferences)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingPlaybackControls(state: BriefingUiState.Success, onPlay: () -> Unit, onSources: () -> Unit, onSeekTo: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onPlay, modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp), shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 4.dp, bottomEnd = 4.dp)) {
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
            if (state.briefing.sources.isNotEmpty()) {
                FilledTonalIconButton(onSources, modifier = Modifier.width(52.dp).fillMaxHeight(), shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 28.dp, bottomEnd = 28.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Rounded.Link, stringResource(R.string.briefing_sources))
                }
            }
        }
        if (state.duration > 0) {
            var seekPosition by remember(state.briefing.region, state.briefing.date) { mutableStateOf<Float?>(null) }
            val interactionSource = remember { MutableInteractionSource() }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Slider(
                    value = seekPosition ?: (state.currentPosition.toFloat() / state.duration).coerceIn(0f, 1f),
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
                    modifier = Modifier.weight(1f).height(48.dp).testTag("briefingSeekbar"),
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.briefing_default_region), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.briefing_default_region_description), modifier = Modifier.padding(top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (listOf(BriefingEdition.FOLLOW_APP) + BriefingEdition.regions).forEach { edition ->
                Surface(onClick = { onSelect(edition) }, color = if (edition == defaultEdition) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = edition == defaultEdition, onClick = null)
                        Text(stringResource(if (edition == BriefingEdition.FOLLOW_APP) R.string.briefing_follow_app_region else briefingRegionLabel(edition)), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            TextButton(onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.briefing_done)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingSourcesSheet(briefing: Briefing, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.briefing_sources), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun BriefingEditionContent(
    state: BriefingUiState.Success,
    scroll: ScrollState,
    actions: BriefingContentActions,
    topPadding: Dp,
    bottomContentPadding: Dp,
) {
    val onRegionSelect = actions.region
    val onPlayPauseClick = actions.play
    val onSeekTo = actions.seek
    val onEpisodeClick = actions.episode
    val onFeedbackClick = actions.feedback
    Column(
        Modifier.fillMaxWidth().verticalScroll(scroll).testTag("briefingArticle").padding(bottom = bottomContentPadding + 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BriefingCover(
            region = state.briefing.region,
            editionDate = state.briefing.date,
            modifier = Modifier.fillMaxWidth().aspectRatio(BRIEFING_COVER_ASPECT_RATIO).clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).testTag("briefingPageCover"),
            mastheadTopPadding = topPadding + 12.dp,
            mastheadScrim = true,
        )
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val date = remember(state.briefing.date) {
                    runCatching { LocalDate.parse(state.briefing.date).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())) }.getOrDefault(state.briefing.date)
                }
                val stories = state.chapters.takeIf { it.isNotEmpty() }?.let {
                    pluralStringResource(R.plurals.briefing_story_count, it.size, it.size)
                }
                Text(
                    listOfNotNull(date, stories, formatBriefingListenDuration(state.duration)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BriefingRegionMenu(state.selectedRegion, onSelect = { region ->
                    AnalyticsHelper.trackDailyBriefingRegionChanged(state.selectedRegion, region, state.briefing.date)
                    onRegionSelect(region)
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
                onPlayPauseClick(state.briefing, null)
            }, onSources = {
                AnalyticsHelper.trackDailyBriefingInteraction("sources_sheet_opened", state.briefing.region, state.briefing.date, mapOf("sources_count" to state.briefing.sources.size))
                actions.onSources()
            }, onSeekTo = onSeekTo)
        }
        BriefingStoriesPager(
            briefing = state.briefing,
            chapters = state.chapters,
            isPlaying = state.isPlaying,
            currentPositionMs = state.currentPosition,
            accentColor = MaterialTheme.colorScheme.primary,
            onEpisodeClick = onEpisodeClick,
            onPlayPauseClick = { onPlayPauseClick(state.briefing, it) },
            onShowSources = { actions.onSources() },
        )
        Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
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
                TextButton(onClick = onFeedbackClick, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.briefing_report_issue)) }
            }
        }
    }
}

private fun BriefingUiState.presentationKey(): String = when (this) {
    is BriefingUiState.Loading -> "loading"
    is BriefingUiState.Error -> "error"
    is BriefingUiState.Success -> "success"
}
