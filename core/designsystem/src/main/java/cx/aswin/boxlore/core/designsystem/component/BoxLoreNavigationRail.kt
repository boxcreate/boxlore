package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
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
        label = "rail selection",
    )
    val scheme = MaterialTheme.colorScheme
    val accents = navigationAccentColors(scheme)
    NavigationRail(modifier, containerColor = scheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp).selectableGroup()) {
            if (selectedIndex >= 0) {
                Surface(
                    modifier = Modifier.size(width = 64.dp, height = 32.dp).offset { IntOffset(0, (8.dp.toPx() + position.value * 80.dp.toPx()).roundToInt()) }
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
                        Modifier.size(64.dp).selectable(selected, role = Role.Tab, onClick = { onNavigate(destination.route) }),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val iconResource = if (selected) destination.selectedIconRes else destination.unselectedIconRes
                        val icon = iconResource?.let { ImageVector.vectorResource(it) }
                            ?: if (selected) destination.selectedIcon else destination.unselectedIcon
                        val painter = rememberVectorPainter(icon)
                        Box(Modifier.size(width = 64.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                            // The tint follows the moving indicator in the draw phase, so the rail does not recompose per frame.
                            Box(Modifier.size(24.dp).drawBehind { drawRailIcon(painter, railIconTint(index, position.value, scheme, accents)) })
                        }
                        Text(destination.label, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

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
