package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs

internal const val LoreSwipeThresholdDp = 88

internal fun loreSwipeProgress(offsetX: Float, threshold: Float): Float =
    if (!offsetX.isFinite() || !threshold.isFinite() || threshold <= 0f) {
        0f
    } else {
        (abs(offsetX) / threshold).coerceIn(0f, 1f)
    }

/** One color clock for page halos and logo; card palettes retain their artwork identity. */
internal class LoreAccentTransition(initial: Color) {
    private val settled = Animatable(initial)
    private data class Swipe(
        val source: Color,
        val target: Color,
        val offset: State<Offset>,
        val threshold: Float,
    )
    private var swipe by mutableStateOf<Swipe?>(null)
    val color: State<Color> = derivedStateOf {
        swipe?.let { lerp(it.source, it.target, loreSwipeProgress(it.offset.value.x, it.threshold)) }
            ?: settled.value
    }

    fun beginSwipe(target: Color, offset: State<Offset>, threshold: Float) {
        // An interrupted return keeps its endpoints, including late palette arrivals.
        if (swipe?.offset === offset) return
        swipe = Swipe(color.value, target, offset, threshold)
    }

    suspend fun settle(target: Color) {
        val displayed = color.value
        settled.snapTo(displayed)
        swipe = null
        settled.animateTo(target, tween(durationMillis = 300))
    }
}
