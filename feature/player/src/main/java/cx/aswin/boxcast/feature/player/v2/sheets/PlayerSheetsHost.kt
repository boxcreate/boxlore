package cx.aswin.boxcast.feature.player.v2.sheets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import kotlin.math.roundToInt

enum class PlayerOverlaySheet {
    None,
    Queue,
    Chapters,
    Transcript,
    SpeedSleep,
}

/**
 * Hosts player overlay sheets, sliding them up from the bottom when the player is fully expanded.
 */
@Composable
fun PlayerSheetsHost(
    activeSheet: PlayerOverlaySheet,
    onDismiss: () -> Unit,
    isPlayerFullyExpanded: Boolean,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    queueContent: @Composable () -> Unit = {},
    chaptersContent: @Composable () -> Unit = {},
    transcriptContent: @Composable () -> Unit = {},
    speedSleepContent: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    val slideOffset = remember { Animatable(1f) }

    val isVisible = activeSheet != PlayerOverlaySheet.None && isPlayerFullyExpanded

    LaunchedEffect(activeSheet, isPlayerFullyExpanded) {
        val target = if (isVisible) 0f else 1f
        slideOffset.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = PlayerChromeGeometry.SheetAnimationDurationMs,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    if (activeSheet != PlayerOverlaySheet.None) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetHeightPx = with(density) { maxHeight.toPx() }
            val yOffsetPx = (slideOffset.value * sheetHeightPx).roundToInt()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, yOffsetPx) },
            ) {
                Crossfade(
                    targetState = activeSheet,
                    label = "playerOverlaySheet",
                ) { sheet ->
                    when (sheet) {
                        PlayerOverlaySheet.Queue -> queueContent()
                        PlayerOverlaySheet.Chapters -> chaptersContent()
                        PlayerOverlaySheet.Transcript -> transcriptContent()
                        PlayerOverlaySheet.SpeedSleep -> speedSleepContent()
                        PlayerOverlaySheet.None -> Unit
                    }
                }
            }
        }
    }
}
