package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.explore.logic.ExploreChromeScrollLogic
import kotlin.math.roundToInt

@Stable
internal class ExploreBrowseChromeState {
    private var offset by mutableFloatStateOf(0f)
    var collapseRange by mutableFloatStateOf(0f)
    val fraction: Float get() = ExploreChromeScrollLogic.fraction(offset, collapseRange)

    fun onScroll(consumedY: Float, enabled: Boolean) {
        offset = ExploreChromeScrollLogic.offsetAfterScroll(offset, consumedY, collapseRange, enabled)
    }

    fun expand() {
        offset = 0f
    }
}

/** Expanded backing retreats with the selectors; only the search pill remains floating. */
@Composable
internal fun ExploreFloatingHeader(
    state: ExploreBrowseChromeState,
    onExpandedHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    search: @Composable () -> Unit,
    selectors: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var searchHeight by remember { mutableIntStateOf(0) }
    var selectorsHeight by remember { mutableIntStateOf(0) }
    val hideSelectorSemantics by remember(state) { derivedStateOf { state.fraction >= 0.85f } }
    LaunchedEffect(searchHeight, selectorsHeight, density) {
        state.collapseRange = selectorsHeight.toFloat()
        onExpandedHeightChanged(with(density) { (searchHeight + selectorsHeight).toDp() } + 24.dp)
    }
    val backing = MaterialTheme.colorScheme.surfaceContainerLow
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(
            Modifier.fillMaxWidth().testTag("explore_header_container").clip(RoundedCornerShape(32.dp))
                .drawBehind {
                    drawRoundRect(
                        color = backing.copy(alpha = 1f - state.fraction),
                        cornerRadius = CornerRadius(32.dp.toPx()),
                    )
                },
        ) {
            Box(
                Modifier.fillMaxWidth().onSizeChanged { searchHeight = it.height }
                    .padding(6.dp)
                    .graphicsLayer {
                        shadowElevation = 4.dp.toPx() * state.fraction
                        shape = CircleShape
                    },
            ) { search() }
            Box(
                Modifier.fillMaxWidth()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = (placeable.height * (1f - state.fraction)).roundToInt()
                        layout(placeable.width, height) { placeable.placeRelative(0, 0) }
                    }
                    .then(if (hideSelectorSemantics) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer { alpha = ((1f - state.fraction) / 0.7f).coerceIn(0f, 1f) },
            ) {
                Column(Modifier.fillMaxWidth().onSizeChanged { selectorsHeight = it.height }) { selectors() }
            }
        }
    }
}
