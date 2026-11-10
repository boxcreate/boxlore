package cx.aswin.boxlore.feature.widgets.logic

/** A widget's size in dp, as the launcher reports it. */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    val shortSide: Int get() = minOf(widthDp, heightDp)
}

/**
 * The two sizes a launcher reports for a widget: its narrowest width with its tallest height (portrait)
 * and its widest width with its shortest height (landscape). Launchers report these late, loosely or
 * not at all, so they only guide what a widget shows; the layouts scale themselves to the real space.
 */
data class WidgetSizes(val portrait: WidgetSize, val landscape: WidgetSize) {
    /** The most space the widget may have: pictures drawn for this stay sharp either way round. */
    val largest: WidgetSize get() = WidgetSize(maxOf(portrait.widthDp, landscape.widthDp), maxOf(portrait.heightDp, landscape.heightDp))

    companion object {
        /** Values at or below zero are missing: some launchers leave them out until the first resize. */
        fun of(minWidthDp: Int, maxWidthDp: Int, minHeightDp: Int, maxHeightDp: Int, fallback: WidgetSize): WidgetSizes {
            val narrow = firstPositive(minWidthDp, maxWidthDp, fallback.widthDp)
            val wide = firstPositive(maxWidthDp, minWidthDp, fallback.widthDp)
            val short = firstPositive(minHeightDp, maxHeightDp, fallback.heightDp)
            val tall = firstPositive(maxHeightDp, minHeightDp, fallback.heightDp)
            return WidgetSizes(
                portrait = WidgetSize(minOf(narrow, wide), maxOf(short, tall)),
                landscape = WidgetSize(maxOf(narrow, wide), minOf(short, tall)),
            )
        }

        private fun firstPositive(vararg values: Int): Int = values.firstOrNull { it > 0 } ?: 1
    }
}
