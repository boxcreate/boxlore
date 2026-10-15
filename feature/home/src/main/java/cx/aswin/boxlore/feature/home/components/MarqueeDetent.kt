package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * How far Home scrolls (px into Marquee's hero, [heroHeight] tall) before the body's rounded edge, the
 * sheet's [overlap] above the hero's foot, meets the bottom of the top bar ([barHeight]).
 */
internal fun marqueeDetent(heroHeight: Int, overlap: Float, barHeight: Int): Int =
    (heroHeight - overlap - barHeight).toInt().coerceAtLeast(0)

/**
 * How much of an upward scroll of [availableY] (negative, as nested scroll reports it) to hold back so
 * Home stops at the [detent] when it has [scrolled] px so far: whatever would carry it past.
 */
internal fun marqueeDetentConsumption(availableY: Float, scrolled: Int, detent: Int): Float {
    if (availableY >= 0f) return 0f
    val room = (detent - scrolled).coerceAtLeast(0).toFloat()
    val wanted = -availableY
    return if (wanted > room) -(wanted - room) else 0f
}

/**
 * Stops Home's scroll where Marquee's body meets the top bar. A drag or fling that starts above that
 * point ([detent], with [scrolled] px so far) halts there, so the whole page rests with the body's edge
 * under the bar and nothing scrolls on beneath it; the next gesture goes through. Scrolling back down
 * never stops. Either provider returning null (no Marquee hero) leaves scrolling alone.
 */
internal class MarqueeDetentConnection(
    private val detent: () -> Int?,
    private val scrolled: () -> Int?,
) : NestedScrollConnection {
    private var inGesture = false
    private var flinging = false
    private var armed = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // A finger landing during a fling starts a new gesture even though the fling never finished.
        if (!inGesture || (flinging && source == NestedScrollSource.UserInput)) startGesture()
        if (!armed || available.y >= 0f) return Offset.Zero
        val stop = detent() ?: return Offset.Zero
        val now = scrolled() ?: return Offset.Zero
        return Offset(0f, marqueeDetentConsumption(available.y, now, stop))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        flinging = true
        val stop = detent()
        val now = scrolled()
        val atStop = stop != null && now != null && now >= stop
        // Already resting at the stop: an upward fling in the same gesture goes nowhere.
        return if (armed && available.y < 0f && atStop) available else Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        inGesture = false
        flinging = false
        armed = false
        return Velocity.Zero
    }

    private fun startGesture() {
        inGesture = true
        flinging = false
        val stop = detent()
        val now = scrolled()
        armed = stop != null && now != null && now < stop
    }
}
