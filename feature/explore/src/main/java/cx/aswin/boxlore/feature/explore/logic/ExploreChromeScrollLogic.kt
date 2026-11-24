package cx.aswin.boxlore.feature.explore.logic

/** Presentation-only scroll policy; it never consumes scroll or changes feed state. */
internal object ExploreChromeScrollLogic {
    fun offsetAfterScroll(offset: Float, consumedY: Float, range: Float, enabled: Boolean): Float {
        if (!enabled || !range.isFinite() || range <= 0f) return 0f
        val boundedOffset = if (offset.isFinite()) offset.coerceIn(0f, range) else 0f
        if (!consumedY.isFinite()) return boundedOffset
        return (boundedOffset - consumedY).coerceIn(0f, range)
    }

    fun fraction(offset: Float, range: Float): Float =
        if (offset.isFinite() && range.isFinite() && range > 0f) (offset / range).coerceIn(0f, 1f) else 0f

    /**
     * Space under search results: clear of the keyboard when it is up (its inset already includes
     * the navigation bar), otherwise of the bottom chrome, plus a little breathing room.
     */
    fun searchBottomPaddingDp(imeBottomDp: Float, chromeBottomDp: Float): Float = maxOf(imeBottomDp, chromeBottomDp) + SEARCH_END_ROOM_DP

    private const val SEARCH_END_ROOM_DP = 24f
}
