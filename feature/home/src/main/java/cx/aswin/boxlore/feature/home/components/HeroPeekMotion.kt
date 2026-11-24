package cx.aswin.boxlore.feature.home.components

/** Gesture-driven fades; neither heading rotates or travels during a swipe. */
internal object HeroPeekMotion {
    fun expansion(size: Float, minimum: Float, maximum: Float): Float {
        if (!size.isFinite() || !minimum.isFinite() || !maximum.isFinite()) return 1f
        if (maximum <= minimum) return 1f
        return ((size - minimum) / (maximum - minimum)).coerceIn(0f, 1f)
    }

    fun peekAlpha(expansion: Float): Float = 1f - smooth(expansion / 0.12f)

    fun contentAlpha(expansion: Float): Float = smooth((expansion - 0.12f) / 0.36f)

    /**
     * A tablet-row card's words by how much of it is on screen: none until 85% shows, fading in over the
     * last 15% of the slide, so a card cut by the row's edge never shows chopped words or half a button.
     */
    fun onScreenAlpha(fraction: Float): Float = if (fraction >= FullyOnScreen) 1f else smooth((fraction - 0.85f) / 0.15f)

    /** Rounding in the row's layout leaves a fully shown card a hair short of 1. */
    private const val FullyOnScreen = 0.995f

    private fun smooth(value: Float): Float {
        val bounded = value.coerceIn(0f, 1f)
        return bounded * bounded * (3f - 2f * bounded)
    }
}
