package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.playback.CastMediaEligibility
import cx.aswin.boxlore.feature.player.R
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
                        TabletPlayerControls(model, dependencies, display, actions, flows, ui, resources, regions)
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
    header: @Composable () -> Unit,
    artwork: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    support: @Composable () -> Unit,
) {
    val empty = BoxloreLayoutBounds(origin.left, origin.top, origin.left, origin.top)
    val bounds = listOf(regions.header, regions.artwork ?: empty, regions.controls, regions.support)
    Layout(content = {
        Box(Modifier.clipToBounds()) { header() }
        Box(Modifier.clipToBounds().testTag("tablet_player_artwork_region"), contentAlignment = Alignment.Center) {
            if (regions.artwork != null) artwork()
        }
        Box(Modifier.clipToBounds().testTag("tablet_player_controls_region")) { controls() }
        Box(Modifier.clipToBounds().testTag("tablet_player_support_region")) { support() }
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FullPlayerHeroSection(
            FullPlayerHeroModel(model.state, model.episode, model.podcast, model.nextEpisode, model.isVideo, HeroDimensions(size.dp, if (model.isVideo) (size * 9f / 16f).dp else size.dp)),
            flows.position,
            dependencies.playbackRepository,
            display,
            actions,
            ui,
            isCompact = true,
        )
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
) {
    Column(
        Modifier.fillMaxSize().nestedScroll(display.sheetNestedScrollConnection).verticalScroll(rememberScrollState())
            .boxloreCardWidth(BoxloreCardFamily.Hero).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (regions.artwork == null) {
            TabletPlayerArtwork(model, dependencies, display, actions, flows, ui, regions.artworkSizeDp)
            Spacer(Modifier.height(12.dp))
        }
        PlayerMetadata(model.episode, model.podcast, display.colorScheme, actions)
        Spacer(Modifier.height(12.dp))
        FullPlayerControls(
            FullPlayerControlModel(model.state, model.episode, model.podcast, display.colorScheme, model.download.isDownloaded, model.download.isDownloading, true),
            dependencies,
            flows,
            ui,
            resources.body.scope,
            inlineSupportingContent = true,
        )
    }
}
