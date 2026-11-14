package cx.aswin.boxlore.wear.ui.shows

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.model.WatchShow
import cx.aswin.boxlore.wear.ui.components.Artwork
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the crown has to be still before the honeycomb snaps to the nearest show. */
private const val CROWN_REST_MS = 140L

private val SnapSpring = spring<Offset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * Your shows as a honeycomb of artwork. Drag in any direction or turn the crown to move the
 * sheet; when the finger lifts, the nearest show settles in the centre. A tap opens the show.
 */
@Composable
internal fun HoneycombShows(
    shows: List<WatchShow>,
    onShow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = remember(shows.size) { HoneycombMath.spiral(shows.size) }
    val pan = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    var crownRest by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val radiusPx = min(constraints.maxWidth, constraints.maxHeight) / 2f

        fun moveBy(dx: Float, dy: Float) {
            val target = HoneycombMath.clampPan(cells, pan.value.x - dx / radiusPx, pan.value.y - dy / radiusPx)
            scope.launch { pan.snapTo(Offset(target.x, target.y)) }
        }

        fun settle() {
            val target = HoneycombMath.snap(cells, pan.value.x, pan.value.y)
            scope.launch { pan.animateTo(Offset(target.x, target.y), SnapSpring) }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(cells) {
                        detectDragGestures(onDragEnd = ::settle, onDragCancel = ::settle) { change, drag ->
                            change.consume()
                            moveBy(drag.x, drag.y)
                        }
                    }
                    .onRotaryScrollEvent { event ->
                        moveBy(0f, -event.verticalScrollPixels)
                        // Snap only once the crown has come to rest, not after every small turn.
                        crownRest?.cancel()
                        crownRest =
                            scope.launch {
                                delay(CROWN_REST_MS)
                                settle()
                            }
                        true
                    }
                    .focusRequester(focusRequester)
                    .focusable(),
        ) {
            val bubbles = HoneycombMath.bubbles(cells, pan.value.x, pan.value.y)
            bubbles.forEach { bubble ->
                val show = shows[bubble.index]
                key(show.id) {
                    Bubble(
                        show = show,
                        diameterPx = bubble.diameter * radiusPx,
                        offset = IntOffset((bubble.x * radiusPx).roundToInt(), (bubble.y * radiusPx).roundToInt()),
                        onClick = { onShow(show.id) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            // Artwork alone does not always say which show it is, so the one in the middle is named.
            bubbles.maxByOrNull { it.diameter }?.let { centred ->
                ShowCaption(shows[centred.index].title, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
private fun Bubble(
    show: WatchShow,
    diameterPx: Float,
    offset: IntOffset,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val diameter = with(LocalDensity.current) { diameterPx.toDp() }
    val label =
        if (show.newEpisodes > 0) {
            "${show.title}, ${pluralStringResource(R.plurals.wear_new_episode_count, show.newEpisodes, show.newEpisodes)}"
        } else {
            show.title
        }
    Box(modifier = modifier.offset { offset }.size(diameter)) {
        Artwork(
            url = show.artworkUrl,
            shape = CircleShape,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable(onClick = onClick)
                    .semantics { contentDescription = label },
        )
        if (show.newEpisodes > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(diameter * 0.24f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize(0.68f).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
            }
        }
    }
}

/** The name of the show in the middle. Each bubble already says its own name to a screen reader. */
@Composable
private fun ShowCaption(title: String, modifier: Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .padding(bottom = 9.dp)
                .widthIn(max = 132.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .clearAndSetSemantics { },
    )
}
