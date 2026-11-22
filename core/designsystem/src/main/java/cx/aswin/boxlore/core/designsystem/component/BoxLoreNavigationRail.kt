package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Same four actions and one travelling selection surface as the floating phone bar. */
@Composable
fun BoxLoreNavigationRail(currentRoute: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val destinations = primaryNavDestinations + loreNavDestination
    val selectedIndex = navigationIndicatorSelectedIndex(currentRoute)
    val retained = remember { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }
    SideEffect { if (selectedIndex >= 0) retained.intValue = selectedIndex }
    val position = animateFloatAsState(
        navigationIndicatorTargetIndex(selectedIndex, retained.intValue).toFloat(),
        FloatingNavigationIndicatorMotion,
        // Animation label for tooling, never shown.
        label = "rail selection", // i18n-ignore
    )
    val scheme = MaterialTheme.colorScheme
    val accents = navigationAccentColors(scheme)
    // Where each icon sits in the rail, measured, so the indicator follows items that grow for long labels.
    val iconTops = remember { mutableStateListOf<Float>() }
    NavigationRail(modifier, containerColor = scheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup()) {
            if (selectedIndex >= 0) {
                Surface(
                    modifier = Modifier.size(width = 64.dp, height = RailIndicatorHeight).offset {
                        IntOffset(0, railIndicatorTop(position.value, iconTops, RailIconInset.toPx(), RailItemPitch.toPx(), RailIndicatorInset.toPx()).roundToInt())
                    }
                        .clearAndSetSemantics { testTag = "navigation_rail_indicator" },
                    shape = RoundedCornerShape(50),
                    color = accents.indicator,
                    border = BorderStroke(0.5.dp, scheme.primary.copy(alpha = 0.12f)),
                ) { FloatingNavigationIndicatorAurora(position, selectedIndex == 3) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                destinations.forEachIndexed { index, destination ->
                    val selected = isNavDestinationSelected(currentRoute, destination.route)
                    Column(
                        Modifier.width(RailItemSize).heightIn(min = RailItemSize).selectable(selected, role = Role.Tab, onClick = { onNavigate(destination.route) }),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val iconResource = if (selected) destination.selectedIconRes else destination.unselectedIconRes
                        val icon = iconResource?.let { ImageVector.vectorResource(it) }
                            ?: if (selected) destination.selectedIcon else destination.unselectedIcon
                        val painter = rememberVectorPainter(icon)
                        Box(
                            Modifier.size(width = RailItemSize, height = RailIconBoxHeight).onPlaced { coordinates ->
                                val item = coordinates.parentLayoutCoordinates?.positionInParent()?.y ?: 0f
                                iconTops.setTop(index, item + coordinates.positionInParent().y, destinations.size)
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            // The tint follows the moving indicator in the draw phase, so the rail does not recompose per frame.
                            Box(Modifier.size(24.dp).drawBehind { drawRailIcon(painter, railIconTint(index, position.value, scheme, accents)) })
                        }
                        RailLabel(stringResource(destination.label), if (selected) scheme.onSurface else scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * A rail label that fits its 64dp cell in every language and text size: it shrinks a little (down
 * to 11sp) to stay on one line, and when that is not enough it wraps to a second line, the cell
 * growing to hold it. An ellipsis is the last resort.
 */
@Composable
private fun RailLabel(text: String, color: Color) {
    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widthPx = with(density) { RailItemSize.roundToPx() }
    val oneLine = remember(text, style, density, widthPx) {
        !measurer.measure(text, style.copy(fontSize = RailLabelMinSize), maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = widthPx)).didOverflowWidth
    }
    Text(
        text,
        color = color,
        style = style.copy(textAlign = TextAlign.Center, hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
        maxLines = if (oneLine) 1 else 2,
        softWrap = !oneLine,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = RailLabelMinSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}

/** Records [top] for item [index] only when it moved, so settled frames write nothing. */
private fun MutableList<Float>.setTop(index: Int, top: Float, count: Int) {
    while (size < count) add(Float.NaN)
    if (this[index] != top) this[index] = top
}

/**
 * The indicator's top for a [position] between items: between the measured icon tops when every
 * item has been placed, otherwise the fixed 64dp cells' path ([fallbackIconTop] plus one [pitch]
 * per item). [indicatorInset] centres the indicator on the icon box.
 */
internal fun railIndicatorTop(position: Float, iconTops: List<Float>, fallbackIconTop: Float, pitch: Float, indicatorInset: Float): Float {
    val bounded = if (position.isFinite()) position.coerceAtLeast(0f) else 0f
    val measured = iconTops.isNotEmpty() && iconTops.none { it.isNaN() }
    if (!measured) return fallbackIconTop + bounded * pitch + indicatorInset
    val last = iconTops.lastIndex
    val lower = floor(bounded).toInt().coerceIn(0, last)
    val upper = (lower + 1).coerceAtMost(last)
    val fraction = (bounded - lower).coerceIn(0f, 1f)
    return iconTops[lower] + (iconTops[upper] - iconTops[lower]) * fraction + indicatorInset
}

private val RailItemSize = 64.dp
private val RailLabelMinSize = 11.sp
private val RailIconBoxHeight = 36.dp
private val RailIndicatorHeight = 32.dp
private val RailIndicatorInset = (RailIconBoxHeight - RailIndicatorHeight) / 2

/** Where the icon box sits in a 64dp cell holding a one-line label, and the distance from one cell to the next. */
private val RailIconInset = 6.dp
private val RailItemPitch = 80.dp

private fun railIconTint(index: Int, indicatorIndex: Float, scheme: ColorScheme, accents: NavigationAccentColors): Color =
    if (abs(indicatorIndex - index) < 0.1f) {
        if (index == 3) loreNavigationContentColor(scheme, indicatorIndex) else accents.content
    } else {
        scheme.onSurfaceVariant
    }

/** Matches Icon's centered fit, including Lore's wider artwork. */
private fun DrawScope.drawRailIcon(painter: VectorPainter, tint: Color) {
    val intrinsic = painter.intrinsicSize
    val scale = minOf(size.width / intrinsic.width, size.height / intrinsic.height)
    val fitted = Size(intrinsic.width * scale, intrinsic.height * scale)
    translate((size.width - fitted.width) / 2f, (size.height - fitted.height) / 2f) {
        with(painter) { draw(fitted, colorFilter = ColorFilter.tint(tint)) }
    }
}
