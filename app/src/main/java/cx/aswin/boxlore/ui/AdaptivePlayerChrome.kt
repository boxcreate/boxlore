package cx.aswin.boxlore.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.ui.logic.AdaptivePlayerScrollLogic
import cx.aswin.boxlore.ui.logic.browsingConsumedDelta

internal data class AdaptivePlayerChrome(
    val progress: State<Float>,
    val scrollConnection: NestedScrollConnection,
    val onSheetInteractionChanged: (Boolean) -> Unit,
)

@Composable
internal fun rememberAdaptivePlayerChrome(
    enabled: Boolean,
    episodeId: String?,
    route: String,
): AdaptivePlayerChrome {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val controller = remember(threshold) { AdaptivePlayerChromeController(threshold) }
    SideEffect { controller.configure(enabled, episodeId, route) }
    val progress = animateFloatAsState(
        targetValue = if (enabled && controller.isCompact) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 240f),
        label = "adaptivePlayerChrome",
    )
    return remember(progress, controller) {
        AdaptivePlayerChrome(progress, controller.scrollConnection, controller::onSheetInteractionChanged)
    }
}

@Stable
internal class AdaptivePlayerChromeController(threshold: Float) {
    private val scroll = AdaptivePlayerScrollLogic(threshold)
    private var enabled = false
    private var episodeId: String? = null
    private var route: String? = null
    private var playerBusy = false
    var isCompact by mutableStateOf(false)
        private set

    fun configure(enabled: Boolean, episodeId: String?, route: String) {
        if (!enabled || this.episodeId != episodeId) {
            scroll.reset()
            isCompact = false
        } else if (this.route != route) {
            scroll.endGesture()
        }
        this.enabled = enabled
        this.episodeId = episodeId
        this.route = route
    }

    fun onSheetInteractionChanged(busy: Boolean) {
        playerBusy = busy
        if (busy) scroll.endGesture()
    }

    val scrollConnection = object : NestedScrollConnection {
        private var preAvailable: Offset? = null
        private var preSource: NestedScrollSource? = null

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            preAvailable = available
            preSource = source
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val before = preAvailable.takeIf { preSource == source }
            preAvailable = null
            preSource = null
            isCompact = scroll.onScroll(
                consumedX = browsingConsumedDelta(before?.x, consumed.x, available.x),
                consumedY = browsingConsumedDelta(before?.y, consumed.y, available.y),
                userInput = source == NestedScrollSource.UserInput,
                enabled = enabled,
                playerBusy = playerBusy,
            )
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            preAvailable = null
            preSource = null
            scroll.endGesture()
            return Velocity.Zero
        }
    }
}
