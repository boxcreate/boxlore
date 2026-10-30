package cx.aswin.boxlore.feature.history.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.history.R
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Where the thumb sits for a scroll position, and which item a thumb position reaches. */
object TimelineScrollerLogic {
    /** The thumb's top as a fraction of the travel, from the first visible item (0 at the top, 1 at the end). */
    fun fraction(firstVisibleIndex: Int, totalItems: Int, visibleItems: Int): Float {
        val scrollable = (totalItems - visibleItems).coerceAtLeast(1)
        return (firstVisibleIndex.toFloat() / scrollable).coerceIn(0f, 1f)
    }

    /** The item to show at the top when the thumb is dragged to [fraction]. */
    fun indexAt(fraction: Float, totalItems: Int, visibleItems: Int): Int {
        val scrollable = (totalItems - visibleItems).coerceAtLeast(0)
        return (fraction.coerceIn(0f, 1f) * scrollable).roundToInt().coerceIn(0, (totalItems - 1).coerceAtLeast(0))
    }

    /** Worth a thumb: the list is at least three screens long. */
    fun isLongEnough(totalItems: Int, visibleItems: Int): Boolean = visibleItems > 0 && totalItems >= visibleItems * 3
}

/**
 * The Timeline's fast scroller (board A1): a 6×56 primary thumb 3dp from the right edge that shows
 * while the list moves, and while dragged a primaryContainer bubble with the date at the top of
 * the list. [dateAt] gives the date of an item index (null for the items above the first day).
 */
@Composable
internal fun TimelineFastScroller(listState: LazyListState, dateAt: (Int) -> LocalDate?, modifier: Modifier = Modifier,) {
    val layout by remember { derivedStateOf { listState.layoutInfo } }
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo.size
    if (!TimelineScrollerLogic.isLongEnough(total, visible)) return
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(listState.isScrollInProgress, dragging) {
        if (listState.isScrollInProgress || dragging) {
            shown = true
        } else {
            delay(1_500)
            shown = false
        }
    }
    val fraction = if (dragging) dragFraction else TimelineScrollerLogic.fraction(listState.firstVisibleItemIndex, total, visible)
    val description = stringResource(R.string.history_scroll_to_date)
    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(160.dp)) {
        val density = LocalDensity.current
        val thumbHeight = 56.dp
        val travelPx = with(density) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
        val thumbTopPx = (fraction * travelPx).roundToInt()
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(0, thumbTopPx) },
        ) {
            Box(
                modifier =
                Modifier
                    .padding(end = 3.dp)
                    .size(width = 24.dp, height = thumbHeight)
                    .semantics { contentDescription = description }
                    .pointerInput(total, visible, travelPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragFraction = TimelineScrollerLogic.fraction(listState.firstVisibleItemIndex, total, visible)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            dragFraction = (dragFraction + dragAmount / travelPx).coerceIn(0f, 1f)
                            val index = TimelineScrollerLogic.indexAt(dragFraction, total, visible)
                            scope.launch { listState.scrollToItem(index) }
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .size(width = 6.dp, height = thumbHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        val date = dateAt(listState.firstVisibleItemIndex)
        if (dragging && date != null) {
            Box(
                modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbTopPx + with(density) { 8.dp.roundToPx() }) }
                    .padding(end = 18.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(TimelineTime.shortDate(date, currentLocale()), style = TimelineType.label, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
