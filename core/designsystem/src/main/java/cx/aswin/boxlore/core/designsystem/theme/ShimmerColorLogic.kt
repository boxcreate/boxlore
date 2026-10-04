package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Alpha needed over the existing base to reach the requested highlight, without a seam. */
internal fun shimmerOverlayColor(base: Color, highlight: Color): Color {
    if (base.alpha >= 1f || highlight.alpha <= base.alpha) return Color.Transparent
    val alpha = (highlight.alpha - base.alpha) / (1f - base.alpha)
    fun channel(baseChannel: Float, highlightChannel: Float): Float =
        ((highlightChannel * highlight.alpha - baseChannel * base.alpha * (1f - alpha)) / alpha).coerceIn(0f, 1f)
    return Color(
        red = channel(base.red, highlight.red),
        green = channel(base.green, highlight.green),
        blue = channel(base.blue, highlight.blue),
        alpha = alpha,
    )
}
