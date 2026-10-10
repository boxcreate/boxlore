package cx.aswin.boxlore.feature.explore.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag as paneTestTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.explore.ExploreUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Keeps the real Explore pane and its scroll state underneath the topic during Back. */
@Composable
internal fun ExploreTopicTransition(
    state: ExploreUiState.Success,
    onBack: () -> Unit,
    content: @Composable (ExploreUiState.Success, Boolean, () -> Unit) -> Unit,
) {
    val hasTopic = state.currentVibe != null
    var baseState by remember { mutableStateOf(state.withoutTopic()) }
    var retainedTopic by remember { mutableStateOf(state.takeIf { hasTopic }) }
    val progress = remember { Animatable(if (hasTopic) 0f else 1f) }
    val scope = rememberCoroutineScope()
    val latestOnBack by rememberUpdatedState(onBack)
    val defaultDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    var direction by remember { mutableFloatStateOf(defaultDirection) }
    var closing by remember { mutableStateOf(false) }

    SideEffect {
        if (hasTopic) retainedTopic = state else baseState = state
    }
    LaunchedEffect(hasTopic) {
        if (hasTopic) {
            closing = false
            direction = defaultDirection
            progress.animateTo(0f, topicSlideSpec())
        } else {
            if (progress.value < 1f) progress.animateTo(1f, topicSlideSpec())
            retainedTopic = null
            closing = false
        }
    }
    val requestBack: () -> Unit = {
        if (hasTopic && !closing) {
            closing = true
            scope.launch {
                if (progress.value < 1f) progress.animateTo(1f, topicSlideSpec())
                latestOnBack()
            }
        }
    }
    TopicPredictiveBackHandler(hasTopic, closing, progress, onDirection = { direction = it }, onCommit = requestBack)

    val topicState = if (hasTopic) state else retainedTopic
    val baseActive = topicState == null
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val fraction = progress.value
                translationX = -direction * size.width * 0.18f * (1f - fraction)
                alpha = 0.8f + 0.2f * fraction
            }.inactivePane(!baseActive, "explore_base_layer"),
        ) {
            content(if (hasTopic) baseState.withoutTopic() else state, baseActive, requestBack)
        }
        topicState?.let { topic ->
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val fraction = progress.value
                    translationX = direction * size.width * fraction
                    scaleX = 1f - 0.035f * fraction
                    scaleY = scaleX
                    shape = RoundedCornerShape((24f * fraction.coerceAtMost(1f)).dp)
                    clip = fraction > 0f
                }.inactivePane(!hasTopic || closing, "explore_topic_layer"),
            ) {
                content(topic, hasTopic && !closing, requestBack)
            }
        }
    }
}

@Composable
private fun TopicPredictiveBackHandler(
    enabled: Boolean,
    closing: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    onDirection: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                if (!closing) {
                    onDirection(if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f)
                    progress.snapTo(event.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
                }
            }
            onCommit()
        } catch (_: CancellationException) {
            if (!closing) scope.launch { progress.animateTo(0f, topicSlideSpec()) }
        }
    }
}

private fun topicSlideSpec() = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)

private fun ExploreUiState.Success.withoutTopic() = copy(
    currentVibe = null,
    searchQuery = "",
    isSearching = false,
    searchResults = emptyList(),
    alsoFoundResults = emptyList(),
)

/** Preview panes are visible but cannot activate hidden buttons, gestures or accessibility actions. */
private fun Modifier.inactivePane(inactive: Boolean, tag: String): Modifier =
    if (!inactive) {
        testTag(tag)
    } else {
        clearAndSetSemantics { paneTestTag = tag }.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }
    }
