package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's scroll stops where Marquee's body meets the top bar, and the next swipe goes through. */
class MarqueeDetentTest {
    private var scrolled = 0
    private val stop = 300
    private val connection = MarqueeDetentConnection(detent = { stop }, scrolled = { scrolled })

    /** Feeds an upward drag step: the list takes what the stop leaves and scrolls by it. */
    private fun drag(dy: Float, source: NestedScrollSource = NestedScrollSource.UserInput) {
        val held = connection.onPreScroll(Offset(0f, dy), source).y
        scrolled += (-(dy - held)).toInt()
    }

    private fun endGesture(velocity: Float = 0f) = runBlocking {
        connection.onPreFling(Velocity(0f, velocity))
        connection.onPostFling(Velocity.Zero, Velocity.Zero)
    }

    @Test fun `the stop sits where the body's edge meets the bar`() {
        assertEquals(376, marqueeDetent(heroHeight = 500, overlap = 24f, barHeight = 100))
        assertEquals(0, marqueeDetent(heroHeight = 100, overlap = 24f, barHeight = 100))
    }

    @Test fun `a swipe stops at the edge, holds there, and the next swipe goes through`() {
        scrolled = 250
        drag(-100f)
        assertEquals("the swipe stops at the edge", 300, scrolled)
        drag(-80f)
        assertEquals("and stays there for the rest of the swipe", 300, scrolled)
        endGesture()
        drag(-80f)
        assertEquals("the next swipe carries on", 380, scrolled)
    }

    @Test fun `a fling stops at the edge too, and an upward fling from the edge in the same swipe goes nowhere`() = runBlocking {
        scrolled = 100
        drag(-20f)
        assertEquals(Velocity.Zero, connection.onPreFling(Velocity(0f, -4000f)))
        // The fling's own steps arrive as side effects and hit the stop.
        drag(-150f, NestedScrollSource.SideEffect)
        drag(-150f, NestedScrollSource.SideEffect)
        assertEquals(300, scrolled)
        connection.onPostFling(Velocity.Zero, Velocity.Zero)

        scrolled = 200
        drag(-200f)
        assertEquals(300, scrolled)
        assertEquals("already resting at the edge", Velocity(0f, -3000f), connection.onPreFling(Velocity(0f, -3000f)))
    }

    @Test fun `a finger landing mid-fling starts a fresh swipe`() = runBlocking {
        scrolled = 290
        drag(-5f)
        connection.onPreFling(Velocity(0f, -4000f))
        drag(-50f, NestedScrollSource.SideEffect)
        assertEquals(300, scrolled)
        // The fling is cut short by a new touch, so no onPostFling arrives first.
        drag(-40f)
        assertEquals("a new swipe from the edge goes through", 340, scrolled)
    }

    @Test fun `scrolling back down never stops, and no hero means no stop`() {
        scrolled = 300
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, 120f), NestedScrollSource.UserInput))
        val none = MarqueeDetentConnection(detent = { null }, scrolled = { null })
        assertEquals(Offset.Zero, none.onPreScroll(Offset(0f, -500f), NestedScrollSource.UserInput))
    }
}
