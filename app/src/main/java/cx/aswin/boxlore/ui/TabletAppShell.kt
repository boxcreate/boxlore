package cx.aswin.boxlore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationRail
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneSpec
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import cx.aswin.boxlore.navigation.LocalTabletDetailNavigation
import cx.aswin.boxlore.navigation.TabletDetailNavigation
import kotlin.math.roundToInt

internal data class TabletShellContent(
    val primary: @Composable () -> Unit,
    val details: @Composable () -> Unit,
    val player: @Composable (contextPane: Boolean) -> Unit,
)

internal data class TabletShellGeometry(
    val primary: BoxloreLayoutBounds,
    val rail: BoxloreLayoutBounds,
    val context: BoxloreLayoutBounds?,
    val detail: BoxloreLayoutBounds,
    val player: BoxloreLayoutBounds,
    val railVisible: Boolean,
    val detailVisible: Boolean,
    val playerAvailable: Boolean,
    val playerVisible: Boolean,
)

internal fun tabletShellGeometry(
    window: BoxloreWindowLayout,
    navigationVisible: Boolean,
    playerAllowed: Boolean,
    hasEpisode: Boolean,
    hasDetail: Boolean,
    immersive: Boolean,
): TabletShellGeometry {
    val railVisible = navigationVisible && window.usesTabletLayout
    val contextAllowed = navigationVisible && playerAllowed
    val spec = boxlorePaneSpec(window, contextAllowed && (hasEpisode || hasDetail))
    val full = BoxloreLayoutBounds(0f, 0f, window.widthDp, window.heightDp)
    val primary = if (railVisible) spec.primary else full
    val context = spec.context.takeIf { contextAllowed }
    val detailVisible = contextAllowed && hasDetail
    val playerAvailable = playerAllowed && hasEpisode && (railVisible || detailVisible)
    return TabletShellGeometry(
        primary,
        primary.copy(left = primary.left - spec.railWidthDp, right = primary.left),
        context,
        context ?: primary,
        playerCanvasBounds(window, primary, context, immersive),
        railVisible,
        detailVisible,
        playerAvailable,
        playerAvailable && playerCanSurface(detailVisible, context, immersive),
    )
}

private fun playerCanSurface(detail: Boolean, context: BoxloreLayoutBounds?, immersive: Boolean): Boolean = !detail || context == null || immersive

private fun playerCanvasBounds(window: BoxloreWindowLayout, primary: BoxloreLayoutBounds, context: BoxloreLayoutBounds?, immersive: Boolean): BoxloreLayoutBounds =
    if (immersive || (context == null && window.foldSafeRegions().size > 1)) {
        BoxloreLayoutBounds(0f, 0f, window.widthDp, window.heightDp)
    } else {
        context ?: primary
    }

/** Retains both hosts at the same composition positions while only their physical bounds change. */
@Composable
internal fun TabletAppShell(
    navigation: TabletDetailNavigation,
    navigationVisible: Boolean,
    playerAllowed: Boolean,
    hasEpisode: Boolean,
    activeTab: String,
    onNavigateTab: (String) -> Unit,
    content: TabletShellContent,
) {
    val window = LocalBoxloreWindowLayout.current
    val initialGeometry = tabletShellGeometry(window, navigationVisible, playerAllowed, hasEpisode, navigation.hasDetail, navigation.immersive)
    val phoneFooter = if (!window.usesTabletLayout && initialGeometry.detailVisible) {
        navigationChromeMetrics(LocalNavigationStyle.current).bottomNavigationClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    val geometry = initialGeometry.copy(detail = initialGeometry.detail.copy(bottom = (initialGeometry.detail.bottom - phoneFooter.value).coerceAtLeast(initialGeometry.detail.top)))
    val playerState = rememberSaveableStateHolder()
    val presentation = cx.aswin.boxlore.feature.player.v2.playerPresentationController()
    LaunchedEffect(window.usesTabletLayout, presentation?.expanded) {
        if (!window.usesTabletLayout && presentation?.expanded == false) navigation.immersive = false
    }
    SideEffect {
        navigation.detailActive = geometry.detailVisible
        navigation.splitAllowed = navigationVisible && playerAllowed && window.usesSplitLayout
        if (window.usesTabletLayout) navigation.hostEnabled = true
    }
    CompositionLocalProvider(
        LocalTabletDetailNavigation provides navigation,
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreNavigationRailVisible provides geometry.railVisible,
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePlayerPaneVisible provides (geometry.context != null),
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreDockedPlayerBounds provides geometry.primary,
    ) {
        TabletShellPhysicalLayout(
            geometry,
            primary = content.primary,
            rail = {
                if (geometry.railVisible) BoxLoreNavigationRail(activeTab, onNavigateTab, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
            },
            details = { TabletShellDetails(navigation, geometry.detailVisible, hasEpisode, content.details) },
            player = {
                if (geometry.playerVisible) {
                    playerState.SaveableStateProvider("tablet_player") { content.player(geometry.context != null && !navigation.immersive) }
                }
            },
        )
    }
}

@Composable
private fun TabletShellDetails(navigation: TabletDetailNavigation, visible: Boolean, hasEpisode: Boolean, content: @Composable () -> Unit) {
    if (!navigation.hostEnabled) return
    val hostState = rememberSaveableStateHolder()
    PaneLifecycle(visible) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        if (visible) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = navigation::closeDetails) {
                        Text(stringResource(if (hasEpisode) R.string.tablet_return_to_player else R.string.tablet_close_details))
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (visible || !navigation.hasDetail) {
                hostState.SaveableStateProvider("detail_host", content = content)
            }
        }
    }
    }
}

@Composable
private fun TabletShellPhysicalLayout(
    geometry: TabletShellGeometry,
    primary: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    details: @Composable () -> Unit,
    player: @Composable () -> Unit,
) {
    Layout(content = {
        ShellRegion(geometry.primary, "primary_pane", primary)
        Box(Modifier.clipToBounds()) { rail() }
        ShellRegion(geometry.detail, "detail_pane", details)
        ShellRegion(geometry.player, "tablet_player_pane", player)
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val viewport = BoxloreLayoutBounds(0f, 0f, constraints.maxWidth / density, constraints.maxHeight / density)
        val primaryArea = geometry.primary.takeIf { it.width > 0f } ?: viewport
        val areas = listOf(primaryArea, geometry.rail.takeIf { geometry.railVisible }, geometry.detail.takeIf { geometry.detailVisible }, geometry.player.takeIf { geometry.playerVisible })
        val children = measurables.mapIndexed { index, measurable ->
            val area = areas[index]
            // Keep the empty context graph ready for a first detail navigation.
            val width = area?.width?.times(density)?.roundToInt() ?: 1
            val height = area?.height?.times(density)?.roundToInt() ?: 1
            measurable.measure(Constraints.fixed(width.coerceAtLeast(1), height.coerceAtLeast(1)))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            children.forEachIndexed { index, child ->
                if (index != 3 || geometry.playerVisible) {
                    areas[index]?.let { area -> child.place((area.left * density).roundToInt(), (area.top * density).roundToInt()) }
                }
            }
        }
    }
}

@Composable
private fun ShellRegion(bounds: BoxloreLayoutBounds, tag: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBoxlorePaneWidth provides bounds.width, LocalBoxlorePaneBounds provides bounds) {
        Box(Modifier.clipToBounds().testTag(tag)) { content() }
    }
}
