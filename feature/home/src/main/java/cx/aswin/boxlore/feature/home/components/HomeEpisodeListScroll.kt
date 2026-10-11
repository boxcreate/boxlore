package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity

/** A gesture that starts inside a scrollable list keeps its drag and fling until release. */
internal class HomeEpisodeListScrollConnection(
    private val canScrollForward: () -> Boolean,
    private val canScrollBackward: () -> Boolean,
) : NestedScrollConnection {
    private var listOwnsGesture: Boolean? = null

    fun beginGesture() {
        listOwnsGesture = null
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f && listOwnsGesture == null) {
            listOwnsGesture = if (available.y < 0f) canScrollForward() else canScrollBackward()
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (listOwnsGesture == true) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val remaining = if (listOwnsGesture == true) Velocity(0f, available.y) else Velocity.Zero
        listOwnsGesture = null
        return remaining
    }
}

/** Observe gesture starts without consuming touches or interfering with episode/play actions. */
internal fun Modifier.observeEpisodeListGestures(connection: HomeEpisodeListScrollConnection): Modifier =
    pointerInput(connection) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            connection.beginGesture()
        }
    }
