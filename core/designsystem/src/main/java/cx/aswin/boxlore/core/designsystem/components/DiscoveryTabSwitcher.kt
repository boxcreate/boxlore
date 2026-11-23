package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.tabletInputFeedback
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class DiscoveryTab(val label: String, val icon: ImageVector? = null, val badgeCount: Int = 0)

val DiscoveryTabSwitcherMinimumHeight = 48.dp

/** Shared with page-owned overlay clearance, including enlarged system text. */
@Composable
fun discoveryTabSwitcherHeight(): Dp =
    (with(LocalDensity.current) { 20.sp.toDp() } + 20.dp).coerceAtLeast(DiscoveryTabSwitcherMinimumHeight)

/**
 * Pure presentation: pages own selection, preferences, placement and navigation. The floating pill
 * is 280dp wide and grows to fit long or translated tab names (never closer than 16dp to the window's
 * sides); the docked one fills its row, at most 560dp wide and centred on tablets. When a name still
 * does not fit, the tabs drop their icons first, then names shrink a little, and an ellipsis is the
 * last resort. One indicator moves between the tabs' measured places.
 */
@Composable
fun DiscoveryTabSwitcher(
    tabs: List<DiscoveryTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
) {
    require(tabs.isNotEmpty())
    val selected = selectedIndex.coerceIn(tabs.indices)
    val height = discoveryTabSwitcherHeight()
    val docked = !floating && LocalBoxloreWindowLayout.current.usesTabletLayout
    DiscoveryExpressiveTheme {
        val position by animateFloatAsState(
            targetValue = selected.toFloat(),
            animationSpec = spring(dampingRatio = 1f, stiffness = 450f),
            label = "discovery_tab_selection",
        )
        val pill: @Composable (Modifier) -> Unit = { pillModifier ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = if (floating) 6.dp else 0.dp,
                modifier = pillModifier.height(height),
            ) {
                DiscoveryTabRow(tabs, selected, { position }, onTabSelected)
            }
        }
        when {
            docked -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                pill(Modifier.widthIn(max = DockedSwitcherMaxWidth).fillMaxWidth())
            }
            floating -> pill(modifier.floatingSwitcherWidth())
            else -> pill(modifier.fillMaxWidth())
        }
    }
}

/** The floating pill: [FloatingSwitcherWidth] at least, up to the space it has less 16dp a side. */
private fun Modifier.floatingSwitcherWidth(): Modifier = layout { measurable, constraints ->
    val margin = FloatingSwitcherSideMargin.roundToPx() * 2
    val max = if (constraints.hasBoundedWidth) (constraints.maxWidth - margin).coerceAtLeast(constraints.minWidth) else constraints.maxWidth
    val min = FloatingSwitcherWidth.roundToPx().coerceIn(constraints.minWidth, max)
    val placeable = measurable.measure(constraints.copy(minWidth = min, maxWidth = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * The tabs and the indicator. Each tab is measured at its natural width; when they all fit an equal
 * share they keep equal widths (the look phones have always had), otherwise each gets its own width
 * plus an equal part of what is left. The row is as wide as its tabs need within its constraints.
 */
@Composable
private fun DiscoveryTabRow(tabs: List<DiscoveryTab>, selected: Int, position: () -> Float, onTabSelected: (Int) -> Unit) {
    val density = LocalDensity.current
    val iconRoom = with(density) { (TabIconSize + TabIconGap).roundToPx() }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.semiBold)
    val badgeStyle = MaterialTheme.typography.labelSmall
    // Each tab's natural width: its padding, icon, one-line name and badge, measured as drawn.
    val natural = remember(tabs, labelStyle, badgeStyle, density) {
        with(density) {
            tabs.map { tab ->
                val label = measurer.measure(tab.label, labelStyle, maxLines = 1, softWrap = false).size.width
                val icon = if (tab.icon != null) iconRoom else 0
                val badge = if (tab.badgeCount > 0) {
                    val count = measurer.measure(badgeLabel(tab.badgeCount), badgeStyle, maxLines = 1, softWrap = false).size.width
                    4.dp.roundToPx() + maxOf(BadgeMinSize.roundToPx(), count + BadgePadding.roundToPx() * 2)
                } else {
                    0
                }
                (TabPadding * 2).roundToPx() + icon + label + badge + 1
            }
        }
    }
    SubcomposeLayout(Modifier.selectableGroup()) { constraints ->
        val width = natural.sum().coerceIn(constraints.minWidth, constraints.maxWidth)
        val plan = planDiscoveryTabs(natural, tabs.map { it.icon != null }, iconRoom, width)
        val tabHeight = constraints.maxHeight
        val placeables = tabs.mapIndexed { index, tab ->
            subcompose(TabSlot.Tab(index)) {
                DiscoveryTabItem(
                    tab = tab,
                    selected = index == selected,
                    coverage = (1f - abs(position() - index)).coerceIn(0f, 1f),
                    showIcon = plan.showIcons,
                    onClick = { onTabSelected(index) },
                )
            }.single().measure(Constraints.fixed(plan.widths[index], tabHeight))
        }
        val indicator = subcompose(TabSlot.Indicator) {
            Box(Modifier.testTag("discovery_tab_indicator").background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
        }.single()
        val (x, indicatorWidth) = discoveryIndicatorSpan(position(), plan.offsets, plan.widths)
        val indicatorPlaceable = indicator.measure(Constraints.fixed(indicatorWidth, tabHeight))
        layout(width, tabHeight) {
            indicatorPlaceable.placeRelative(x, 0)
            placeables.forEachIndexed { index, placeable -> placeable.placeRelative(plan.offsets[index], 0) }
        }
    }
}

private sealed interface TabSlot {
    data class Tab(val index: Int) : TabSlot

    data object Indicator : TabSlot
}

/** How the tabs share a row: their widths and start offsets, and whether icons still show. */
internal data class DiscoveryTabPlan(val widths: List<Int>, val offsets: List<Int>, val showIcons: Boolean)

/**
 * Shares [width] px between tabs of [natural] widths. Equal shares when every tab fits one; else
 * each tab's own width plus an equal part of the rest. When even that is too wide the icons go
 * ([hasIcon] tabs lose [iconRoom] each), and if the names alone still don't fit, each tab shrinks in
 * proportion, so names shrink, then shorten.
 */
internal fun planDiscoveryTabs(natural: List<Int>, hasIcon: List<Boolean>, iconRoom: Int, width: Int): DiscoveryTabPlan {
    val count = natural.size
    val withIcons = natural.sum() <= width
    val needed = if (withIcons) natural else natural.mapIndexed { index, w -> if (hasIcon[index]) (w - iconRoom).coerceAtLeast(0) else w }
    val share = width / count
    val widths = when {
        needed.all { it <= share } -> List(count) { index -> share + if (index < width - share * count) 1 else 0 }
        needed.sum() <= width -> {
            val extra = width - needed.sum()
            needed.mapIndexed { index, w -> w + extra / count + if (index < extra % count) 1 else 0 }
        }
        else -> {
            val total = needed.sum().coerceAtLeast(1)
            val scaled = needed.map { it.toLong() * width / total }.map(Long::toInt)
            scaled.mapIndexed { index, w -> w + if (index < width - scaled.sum()) 1 else 0 }
        }
    }
    val offsets = widths.runningFold(0, Int::plus).dropLast(1)
    return DiscoveryTabPlan(widths, offsets, showIcons = withIcons)
}

/** The indicator's start and width for a [position] between tabs, between the measured tabs either side. */
internal fun discoveryIndicatorSpan(position: Float, offsets: List<Int>, widths: List<Int>): Pair<Int, Int> {
    val last = offsets.lastIndex
    val bounded = if (position.isFinite()) position.coerceIn(0f, last.toFloat()) else 0f
    val lower = floor(bounded).toInt().coerceIn(0, last)
    val upper = (lower + 1).coerceAtMost(last)
    val fraction = bounded - lower
    val x = offsets[lower] + (offsets[upper] - offsets[lower]) * fraction
    val w = widths[lower] + (widths[upper] - widths[lower]) * fraction
    return x.roundToInt() to w.roundToInt()
}

@Composable
private fun DiscoveryTabItem(tab: DiscoveryTab, selected: Boolean, coverage: Float, showIcon: Boolean, onClick: () -> Unit) {
    val content = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onPrimaryContainer, coverage)
    val interactionSource = remember { MutableInteractionSource() }
    val style = MaterialTheme.typography.labelLarge
    Row(
        Modifier.fillMaxHeight().clip(CircleShape).tabletInputFeedback(interactionSource, CircleShape).selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        )
            .semantics(mergeDescendants = true) { }.padding(horizontal = TabPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        tab.icon?.takeIf { showIcon }?.let {
            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(TabIconSize))
            Spacer(Modifier.width(TabIconGap))
        }
        Text(
            tab.label,
            modifier = Modifier.weight(1f, fill = false),
            color = content,
            style = style,
            fontWeight = GoogleSansWeight.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = WordSafeAutoSize(min = TabLabelMinSize, max = style.fontSize),
        )
        if (tab.badgeCount > 0) {
            Spacer(Modifier.width(4.dp))
            Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Text(badgeLabel(tab.badgeCount))
            }
        }
    }
}

private fun badgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()

private val FloatingSwitcherWidth = 280.dp
private val TabPadding = 10.dp
private val BadgeMinSize = 16.dp
private val BadgePadding = 4.dp
private val FloatingSwitcherSideMargin = 16.dp
private val DockedSwitcherMaxWidth = 560.dp
private val TabIconSize = 20.dp
private val TabIconGap = 6.dp
private val TabLabelMinSize = 11.sp
