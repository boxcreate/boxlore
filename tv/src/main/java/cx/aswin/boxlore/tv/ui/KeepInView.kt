package cx.aswin.boxlore.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * How lists and rows scroll to the item the remote moves to: only as far as it takes to show the
 * whole item with a small margin, never further. Compose on a TV otherwise moves the item to a
 * third of the way in, which leaves cards and rows half off the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
object KeepInView : BringIntoViewSpec {
    /** The margin kept around the item, as a share of the list's size, so its focus outline and lift stay in view. */
    private const val MARGIN = 0.04f

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = scrollFor(offset, size, containerSize)

    /** Pure, for tests: how far to scroll for an item at [offset] of [size] in a list of [containerSize]. */
    fun scrollFor(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * MARGIN
        val start = offset - margin
        val end = offset + size + margin
        return when {
            start >= 0f && end <= containerSize -> 0f
            // Taller than the list: line up its start.
            size + 2 * margin >= containerSize -> start
            start < 0f -> start
            else -> end - containerSize
        }
    }
}
