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

    private fun smooth(value: Float): Float {
        val bounded = value.coerceIn(0f, 1f)
        return bounded * bounded * (3f - 2f * bounded)
    }
}
