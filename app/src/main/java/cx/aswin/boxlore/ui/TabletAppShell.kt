package cx.aswin.boxlore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationRail
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxlorePaneSpec
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreBottomChromeReserved
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneSpec
import cx.aswin.boxlore.core.designsystem.layout.foldSafeBrowsingRegion
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions
import cx.aswin.boxlore.navigation.LocalTabletDetailNavigation
import cx.aswin.boxlore.navigation.TabletDetailNavigation
import kotlin.math.roundToInt

internal data class TabletShellContent(
    val primary: @Composable () -> Unit,
    val details: @Composable () -> Unit,
    val player: @Composable (contextPane: Boolean) -> Unit,
    val keyboardActions: PlaybackKeyboardActions = PlaybackKeyboardActions({}, {}, {}),
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
) {
    val contextPlayerVisible: Boolean get() = context != null && !detailVisible
    fun miniPlayerVisible(immersive: Boolean): Boolean = playerVisible && !contextPlayerVisible && !immersive
}

/**
 * Pane bounds for the shell. Routes without navigation (onboarding, account, support) stay in one
 * half of a half-open fold unless [hiddenNavFullWindow] lets them use the whole window.
 */
@Suppress("LongParameterList")
internal fun tabletShellGeometry(
    window: BoxloreWindowLayout,
    navigationVisible: Boolean,
    playerAllowed: Boolean,
    hasEpisode: Boolean,
    hasDetail: Boolean,
    immersive: Boolean,
    playerDocked: Boolean = false,
    hiddenNavFullWindow: Boolean = false,
): TabletShellGeometry {
    val railVisible = navigationVisible && window.usesTabletLayout
    val contextAllowed = navigationVisible && playerAllowed
    val spec = boxlorePaneSpec(window, contextAllowed && ((hasEpisode && !playerDocked) || hasDetail), playerOnly = !hasDetail)
    val primary = primaryPaneBounds(window, spec, railVisible, hiddenNavFullWindow)
    val context = spec.context.takeIf { contextAllowed }
    val detailVisible = contextAllowed && hasDetail
    val playerAvailable = playerAllowed && hasEpisode && (railVisible || detailVisible)
    return TabletShellGeometry(
        primary,
        primary.copy(left = primary.left - spec.railWidthDp, right = primary.left),
        context,
        context ?: primary,
        playerCanvasBounds(window, primary, context.takeUnless { detailVisible }, immersive),
        railVisible,
        detailVisible,
        playerAvailable,
        playerAvailable,
    )
}

/** Browsing beside the rail; without navigation, one half of a half-open fold unless the route opts in to the whole window. */
private fun primaryPaneBounds(window: BoxloreWindowLayout, spec: BoxlorePaneSpec, railVisible: Boolean, hiddenNavFullWindow: Boolean): BoxloreLayoutBounds = when {
    railVisible -> spec.primary
    window.usesTabletLayout && !hiddenNavFullWindow -> window.foldSafeBrowsingRegion()
    else -> spec.primary.copy(left = spec.primary.left - spec.railWidthDp)
}

private fun playerCanvasBounds(window: BoxloreWindowLayout, primary: BoxloreLayoutBounds, context: BoxloreLayoutBounds?, immersive: Boolean): BoxloreLayoutBounds =
    if (immersive || (context == null && window.foldSafeRegions().size > 1)) {
        BoxloreLayoutBounds(0f, 0f, window.widthDp, window.heightDp)
    } else {
        context ?: primary
    }

/**
 * Retains both hosts at the same composition positions while only their physical bounds change.
 * [hiddenNavFullWindow] lets a route without navigation use the whole window of a half-open fold.
 */
@Suppress("LongParameterList")
@Composable
internal fun TabletAppShell(
    navigation: TabletDetailNavigation,
    navigationVisible: Boolean,
    playerAllowed: Boolean,
    hasEpisode: Boolean,
    activeTab: String,
    onNavigateTab: (tab: String, revealedPrimary: Boolean) -> Unit,
    content: TabletShellContent,
    hiddenNavFullWindow: Boolean = false,
) {
    val window = LocalBoxloreWindowLayout.current
    val initialGeometry = tabletShellGeometry(
        window,
        navigationVisible,
        playerAllowed,
        hasEpisode,
        navigation.hasDetail,
        navigation.immersive,
        navigation.playerDocked,
        hiddenNavFullWindow,
    )
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
    SideEffect { navigation.updateShellState(geometry, window, navigationVisible && playerAllowed) }
    CompositionLocalProvider(
        LocalTabletDetailNavigation provides navigation,
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreNavigationRailVisible provides geometry.railVisible,
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePlayerPaneVisible provides geometry.contextPlayerVisible,
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible provides geometry.miniPlayerVisible(navigation.immersive),
        cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreDockedPlayerBounds provides geometry.primary,
    ) {
        TabletPlaybackKeyboard(window.usesTabletLayout && playerAllowed && hasEpisode, content.keyboardActions) { keyboardModifier ->
        TabletShellPhysicalLayout(
            geometry,
            window,
            bottomChromeReserved = phoneFooter > 0.dp,
            modifier = keyboardModifier,
            primary = content.primary,
            rail = {
                if (geometry.railVisible) {
                    BoxLoreNavigationRail(
                        activeTab,
                        { tab -> onNavigateTab(tab, navigation.revealPrimary()) },
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            },
            details = { TabletShellDetails(navigation, geometry.detailVisible, content.details) },
            player = {
                if (geometry.playerVisible) {
                    playerState.SaveableStateProvider("tablet_player") { content.player(geometry.contextPlayerVisible && !navigation.immersive) }
                }
            },
        )
        }
    }
}

private fun TabletDetailNavigation.updateShellState(geometry: TabletShellGeometry, window: BoxloreWindowLayout, contextAllowed: Boolean) {
    detailActive = geometry.detailVisible
    detailCoversPrimary = geometry.detailVisible && geometry.context == null
    playerPaneAvailable = contextAllowed && boxlorePaneSpec(window, true, playerOnly = true).context != null
    splitAllowed = contextAllowed && (window.usesSplitLayout || window.usesHingeSplit)
    if (window.usesTabletLayout) hostEnabled = true
}

@Composable
private fun TabletShellDetails(navigation: TabletDetailNavigation, visible: Boolean, content: @Composable () -> Unit) {
    if (!navigation.hostEnabled) return
    val hostState = rememberSaveableStateHolder()
    PaneLifecycle(visible) {
        Box(Modifier.fillMaxSize()) {
            if (visible || !navigation.hasDetail) {
                hostState.SaveableStateProvider("detail_host", content = content)
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun TabletShellPhysicalLayout(
    geometry: TabletShellGeometry,
    window: BoxloreWindowLayout,
    bottomChromeReserved: Boolean,
    modifier: Modifier = Modifier,
    primary: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    details: @Composable () -> Unit,
    player: @Composable () -> Unit,
) {
    Layout(content = {
        ShellRegion(geometry.primary, window, "primary_pane", content = primary)
        Box(Modifier.clipToBounds().consumeInsetsAwayFromWindowEdges(geometry.rail, window)) { rail() }
        ShellRegion(geometry.detail, window, "detail_pane", bottomChromeReserved) {
            Box(if (geometry.context != null) Modifier.clip(MaterialTheme.shapes.extraLarge) else Modifier) { details() }
        }
        ShellRegion(geometry.player, window, "tablet_player_pane") {
            Box(if (geometry.context != null && !geometry.detailVisible && geometry.player == geometry.context) Modifier.clip(MaterialTheme.shapes.extraLarge) else Modifier) { player() }
        }
    }, modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { measurables, constraints ->
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
private fun ShellRegion(
    bounds: BoxloreLayoutBounds,
    window: BoxloreWindowLayout,
    tag: String,
    bottomChromeReserved: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBoxlorePaneWidth provides bounds.width,
        LocalBoxlorePaneBounds provides bounds,
        LocalBoxloreBottomChromeReserved provides bottomChromeReserved,
    ) {
        Box(Modifier.clipToBounds().consumeInsetsAwayFromWindowEdges(bounds, window).testTag(tag)) { content() }
    }
}

/**
 * Only pane edges on the window edge keep system-bar insets. Descendants that pad with
 * `windowInsetsPadding`, `statusBarsPadding` or `navigationBarsPadding` then pad from their pane:
 * the lower half in tabletop gets no status-bar gap, and a pane beside another none on that side.
 */
@Composable
private fun Modifier.consumeInsetsAwayFromWindowEdges(bounds: BoxloreLayoutBounds, window: BoxloreWindowLayout): Modifier {
    val sides = listOfNotNull(
        WindowInsetsSides.Top.takeIf { bounds.top > EDGE_TOLERANCE_DP },
        WindowInsetsSides.Left.takeIf { bounds.left > EDGE_TOLERANCE_DP },
        WindowInsetsSides.Right.takeIf { window.isSpecified && bounds.right < window.widthDp - EDGE_TOLERANCE_DP },
        WindowInsetsSides.Bottom.takeIf { window.isSpecified && bounds.bottom < window.heightDp - EDGE_TOLERANCE_DP },
    ).reduceOrNull { all, side -> all + side } ?: return this
    return consumeWindowInsets(WindowInsets.safeDrawing.only(sides))
}

private const val EDGE_TOLERANCE_DP = 0.5f
