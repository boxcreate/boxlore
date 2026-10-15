package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.tabletInputFeedback
import cx.aswin.boxlore.core.playback.CastMediaEligibility
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.v2.logic.TabletPlayerArrangement
import cx.aswin.boxlore.feature.player.v2.logic.TabletPlayerRegions
import cx.aswin.boxlore.feature.player.v2.logic.intersectPlayerBounds
import cx.aswin.boxlore.feature.player.v2.logic.tabletPlayerRegions
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TabletPlayerResources(
    val body: FullPlayerBodyResources,
    val queue: PlayerQueueSheetResources,
    val minimalActions: MinimalPlayerActions,
    val minimal: Boolean,
)

@Composable
internal fun TabletFullPlayerBody(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: TabletPlayerResources,
) {
    val window = LocalBoxloreWindowLayout.current
    val pane = LocalBoxlorePaneBounds.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val safeWindow = with(density) {
        BoxloreLayoutBounds(
            safe.getLeft(this, direction).toDp().value,
            safe.getTop(this).toDp().value,
            window.widthDp - safe.getRight(this, direction).toDp().value,
            window.heightDp - safe.getBottom(this).toDp().value,
        )
    }
    MaterialTheme(colorScheme = display.colorScheme) {
        BoxWithConstraints(resources.body.modifier.fillMaxSize().playerCanvas(display.colorScheme)) {
            val origin = pane ?: BoxloreLayoutBounds(0f, 0f, maxWidth.value, min(maxHeight.value, window.heightDp))
            val viewport = intersectPlayerBounds(origin, safeWindow) ?: origin
            val regions = tabletPlayerRegions(window, viewport, display.isContextPane) ?: return@BoxWithConstraints
            TabletPlayerRegionLayout(
                regions = regions,
                origin = origin,
                scrollStack = !resources.minimal && regions.arrangement in listOf(TabletPlayerArrangement.Stacked, TabletPlayerArrangement.HorizontalFold),
                header = { TabletPlayerHeader(model, display, actions, ui) },
                artwork = {
                    if (resources.minimal) {
                        MinimalPlayerContent(
                            model.state,
                            display,
                            PlayerProgressFlows(flows.position, flows.bufferedPosition),
                            resources.minimalActions.copy(onQueue = { ui.supportingTab = PlayerSupportingTab.UpNext }),
                            section = MinimalPlayerSection.Heading,
                        )
                    } else if (regions.arrangement == TabletPlayerArrangement.HorizontalFold) {
                        TabletTabletopArtwork(model, dependencies, display, actions, flows, ui, regions.artworkSizeDp)
                    } else {
                        TabletPlayerArtwork(model, dependencies, display, actions, flows, ui, regions.artworkSizeDp)
                    }
                },
                controls = {
                    if (resources.minimal) {
                        MinimalPlayerContent(
                            model.state,
                            display,
                            PlayerProgressFlows(flows.position, flows.bufferedPosition),
                            resources.minimalActions.copy(onQueue = { ui.supportingTab = PlayerSupportingTab.UpNext }),
                            section = if (regions.artwork == null) MinimalPlayerSection.Embedded else MinimalPlayerSection.Transport,
                        )
                    } else {
                        TabletPlayerControls(model, dependencies, display, actions, flows, ui, resources, regions, scrollContent = regions.arrangement !in listOf(TabletPlayerArrangement.Stacked, TabletPlayerArrangement.HorizontalFold))
                    }
                },
                support = { TabletPlayerSupport(model, dependencies, display, actions, flows, ui, resources) },
            )
            SnackbarHost(resources.body.snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = resources.body.navBarPadding))
        }
    }
}

@Composable
private fun TabletPlayerRegionLayout(
    regions: TabletPlayerRegions,
    origin: BoxloreLayoutBounds,
    scrollStack: Boolean,
    header: @Composable () -> Unit,
    artwork: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    support: @Composable () -> Unit,
) {
    val empty = BoxloreLayoutBounds(origin.left, origin.top, origin.left, origin.top)
    val body = regions.controls.copy(bottom = regions.support.bottom)
    val bounds = if (scrollStack) listOf(regions.header, regions.artwork ?: empty, body) else listOf(regions.header, regions.artwork ?: empty, regions.controls, regions.support)
    Layout(content = {
        Box(Modifier.clipToBounds()) { header() }
        Box(Modifier.clipToBounds().testTag("tablet_player_artwork_region"), contentAlignment = Alignment.Center) {
            if (regions.artwork != null) artwork()
        }
        if (scrollStack) {
            Column(Modifier.fillMaxSize().clipToBounds().verticalScroll(rememberScrollState()).testTag("tablet_player_scroll")) {
                Box(Modifier.fillMaxWidth().testTag("tablet_player_controls_region")) { controls() }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height((body.height * 0.4f).coerceIn(200f, 360f).dp).testTag("tablet_player_support_region")) { support() }
            }
        } else {
            Box(Modifier.clipToBounds().testTag("tablet_player_controls_region")) { controls() }
            Box(Modifier.clipToBounds().testTag("tablet_player_support_region")) { support() }
        }
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val children = measurables.mapIndexed { index, measurable ->
            val area = bounds[index]
            measurable.measure(Constraints.fixed((area.width * density).roundToInt(), (area.height * density).roundToInt()))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            children.forEachIndexed { index, child ->
                val area = bounds[index]
                child.place(((area.left - origin.left) * density).roundToInt(), ((area.top - origin.top) * density).roundToInt())
            }
        }
    }
}

@Composable
private fun TabletPlayerHeader(model: FullPlayerBodyModel, display: FullPlayerDisplay, actions: FullPlayerActions, ui: FullPlayerUiState) {
    CompositionLocalProvider(LocalContentColor provides display.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BoxLoreCastRouteButton(
            enabled = CastMediaEligibility.isCastable(model.episode.audioUrl) || model.state.playbackRoute.isRemote,
            isCasting = model.state.playbackRoute.isRemote,
            tint = display.colorScheme.onSurface,
            activeTint = display.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(stringResource(R.string.player_now_playing), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (display.isContextPane) {
            actions.onDockPlayer?.let { dock ->
                IconButton(onClick = dock) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.minimal_collapse)) }
            }
            actions.onOpenImmersivePlayer?.let { open ->
                IconButton(onClick = open) { Icon(Icons.Rounded.Fullscreen, stringResource(R.string.player_open_fullscreen)) }
            }
        } else {
            IconButton(onClick = {
                cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("collapsed")
                actions.onCollapse()
            }) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.minimal_collapse)) }
        }
        IconButton(onClick = { ui.showShareSheet = true }) { Icon(Icons.Rounded.Share, stringResource(R.string.player_share_episode)) }
    }
    }
}

@Composable
private fun TabletTabletopArtwork(model: FullPlayerBodyModel, dependencies: FullPlayerDependencies, display: FullPlayerDisplay, actions: FullPlayerActions, flows: FullPlayerFlows, ui: FullPlayerUiState, maximumSize: Float) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp).boxloreCardWidth(BoxloreCardFamily.Hero), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TabletPlayerArtwork(model, dependencies, display, actions, flows, ui, minOf(maximumSize, maxHeight.value, maxWidth.value))
        }
        Spacer(Modifier.height(8.dp))
        TabletPlayerMetadata(model, display, actions, compact = false)
    }
}

@Composable
private fun TabletPlayerArtwork(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    size: Float,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val heroWidth = if (model.state.playbackRoute.isRemote) min(400f, maxWidth.value - 24f) else size
        val heroHeight = when {
            model.state.playbackRoute.isRemote -> 240f
            model.isVideo -> size * 9f / 16f
            else -> size
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FullPlayerHeroSection(
            FullPlayerHeroModel(model.state, model.episode, model.podcast, model.nextEpisode, model.isVideo, HeroDimensions(heroWidth.dp, heroHeight.dp)),
            flows.position,
            dependencies.playbackRepository,
            display,
            actions,
            ui,
            isCompact = true,
        )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun TabletPlayerControls(
    model: FullPlayerBodyModel,
    dependencies: FullPlayerDependencies,
    display: FullPlayerDisplay,
    actions: FullPlayerActions,
    flows: FullPlayerFlows,
    ui: FullPlayerUiState,
    resources: TabletPlayerResources,
    regions: TabletPlayerRegions,
    scrollContent: Boolean,
) {
    val compactHeading = display.isContextPane && !model.isVideo && !model.state.playbackRoute.isRemote
    val playbackControls: @Composable () -> Unit = {
        FullPlayerControls(
            FullPlayerControlModel(model.state, model.episode, model.podcast, display.colorScheme, model.download.isDownloaded, model.download.isDownloading, true),
            dependencies,
            flows,
            ui,
            resources.body.scope,
            inlineSupportingContent = true,
            primaryControlHeight = if (display.isContextPane || regions.arrangement == TabletPlayerArrangement.HorizontalFold) 80.dp else 104.dp,
        )
    }
    Column(
        Modifier.fillMaxWidth().nestedScroll(display.sheetNestedScrollConnection)
            .then(if (scrollContent) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)
            .boxloreCardWidth(BoxloreCardFamily.Hero).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (regions.arrangement == TabletPlayerArrangement.Columns) {
            MeasuredTabletPlayerColumn(
                viewportHeightDp = regions.controls.height - 16f,
                maximumArtworkDp = regions.artworkSizeDp,
                artwork = { size -> TabletPlayerArtwork(model, dependencies, display, actions, flows, ui, size) },
                chrome = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TabletPlayerMetadata(model, display, actions, compact = false)
                        Spacer(Modifier.height(12.dp))
                        playbackControls()
                    }
                },
            )
        } else {
            if (compactHeading) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(112.dp)) { TabletPlayerArtwork(model, dependencies, display, actions, flows, ui, 112f) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { TabletPlayerMetadata(model, display, actions, compact = true) }
                }
            } else {
                if (regions.artwork == null) {
                    TabletPlayerArtwork(model, dependencies, display, actions, flows, ui, regions.artworkSizeDp)
                    Spacer(Modifier.height(12.dp))
                }
                if (regions.arrangement != TabletPlayerArrangement.HorizontalFold) TabletPlayerMetadata(model, display, actions, compact = false)
            }
            if (regions.arrangement != TabletPlayerArrangement.HorizontalFold) Spacer(Modifier.height(12.dp))
            playbackControls()
        }
    }
}

/** Reserve the real text and controls before choosing artwork, including the safe bottom inset. */
@Composable
internal fun MeasuredTabletPlayerColumn(viewportHeightDp: Float, maximumArtworkDp: Float, artwork: @Composable (Float) -> Unit, chrome: @Composable () -> Unit) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val controls = subcompose("controls", chrome).single().measure(loose)
        val gap = 12.dp.roundToPx()
        val remaining = (viewportHeightDp * density - controls.height - gap).coerceAtLeast(96.dp.toPx())
        val artworkSize = min(maximumArtworkDp, remaining / density)
        val hero = subcompose("artwork") { artwork(artworkSize) }.single().measure(loose)
        layout(constraints.maxWidth, hero.height + gap + controls.height) {
            hero.placeRelative((constraints.maxWidth - hero.width) / 2, 0)
            controls.placeRelative(0, hero.height + gap)
        }
    }
}

@Composable
private fun TabletPlayerMetadata(model: FullPlayerBodyModel, display: FullPlayerDisplay, actions: FullPlayerActions, compact: Boolean) {
    val alignment = if (compact) TextAlign.Start else TextAlign.Center
    val titleSource = remember { MutableInteractionSource() }
    val showSource = remember { MutableInteractionSource() }
    Text(
        model.episode.title.replace("+", " "),
        Modifier.fillMaxWidth().tabletInputFeedback(titleSource).clickable(titleSource, indication = null) {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("episode_info")
            actions.onCollapse()
            actions.onEpisodeInfoClick(model.episode)
        },
        color = display.colorScheme.onSurface,
        style = (if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(fontWeight = GoogleSansWeight.bold),
        maxLines = if (compact) 3 else 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = alignment,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        model.podcast.title.replace("+", " "),
        Modifier.fillMaxWidth().tabletInputFeedback(showSource).clickable(showSource, indication = null) {
            cx.aswin.boxlore.core.analytics.PlayerSessionAggregator.logAction("podcast_info")
            actions.onCollapse()
            actions.onPodcastInfoClick(model.podcast)
        },
        color = display.colorScheme.onSurfaceVariant,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = alignment,
    )
}
