package cx.aswin.boxlore.feature.home.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEpisodeListScrollTest {
    private var forward = true
    private var backward = true
    private val connection = HomeEpisodeListScrollConnection({ forward }, { backward })

    @Test fun `a list drag consumes every remaining vertical pixel even after reaching an edge`() {
        connection.beginGesture()
        assertEquals(Offset.Zero, connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput))
        forward = false
        assertEquals(Offset(0f, -18f), connection.onPostScroll(Offset(0f, -22f), Offset(7f, -18f), NestedScrollSource.UserInput))
        assertEquals(Offset(0f, 20f), connection.onPostScroll(Offset.Zero, Offset(3f, 20f), NestedScrollSource.UserInput))
    }

    @Test fun `the child keeps fling leftovers as well as drag leftovers`() = runBlocking {
        connection.beginGesture()
        connection.onPreScroll(Offset(0f, -40f), NestedScrollSource.UserInput)
        assertEquals(Velocity.Zero, connection.onPreFling(Velocity(5f, -900f)))
        assertEquals(Offset(0f, -30f), connection.onPostScroll(Offset.Zero, Offset(8f, -30f), NestedScrollSource.SideEffect))
        assertEquals(Velocity(0f, -500f), connection.onPostFling(Velocity.Zero, Velocity(5f, -500f)))
    }

    @Test fun `a fresh outward gesture at either edge passes to Home`() = runBlocking {
        for (direction in listOf(-1f, 1f)) {
            forward = direction > 0
            backward = direction < 0
            connection.beginGesture()
            connection.onPreScroll(Offset(0f, 40f * direction), NestedScrollSource.UserInput)
            assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(0f, 40f * direction), NestedScrollSource.UserInput))
            assertEquals(Velocity.Zero, connection.onPostFling(Velocity.Zero, Velocity(0f, 900f * direction)))
        }
    }

    @Test fun `an inward gesture at an edge is owned by the list`() {
        forward = false
        backward = true
        connection.beginGesture()
        connection.onPreScroll(Offset(0f, 40f), NestedScrollSource.UserInput)
        assertEquals(Offset(0f, 12f), connection.onPostScroll(Offset.Zero, Offset(0f, 12f), NestedScrollSource.UserInput))
    }

    @Test fun `short lists pass vertical scrolling to Home and never consume horizontal motion`() {
        forward = false
        backward = false
        connection.beginGesture()
        connection.onPreScroll(Offset(15f, 0f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(15f, 0f), NestedScrollSource.UserInput))
        connection.onPreScroll(Offset(0f, -25f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(0f, -25f), NestedScrollSource.UserInput))
    }

    @Test fun `a new gesture resets ownership after an interrupted drag`() {
        connection.beginGesture()
        connection.onPreScroll(Offset(0f, -25f), NestedScrollSource.UserInput)
        forward = false
        connection.beginGesture()
        connection.onPreScroll(Offset(0f, -25f), NestedScrollSource.UserInput)
        assertEquals(Offset.Zero, connection.onPostScroll(Offset.Zero, Offset(0f, -25f), NestedScrollSource.UserInput))
    }
}
