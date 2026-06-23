package cx.aswin.boxcast.feature.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.BoxLoreLoader
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun PlayerControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    colorScheme: ColorScheme,
    controlTint: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    height: androidx.compose.ui.unit.Dp = 80.dp
) {
    val interactionSourcePrev = remember { MutableInteractionSource() }
    val interactionSourcePlay = remember { MutableInteractionSource() }
    val interactionSourceNext = remember { MutableInteractionSource() }
    
    var lastClickedId by remember { mutableStateOf<Int?>(null) }
    
    // Auto-reset last clicked
    LaunchedEffect(lastClickedId) {
        if (lastClickedId != null) {
            kotlinx.coroutines.delay(220)
            lastClickedId = null
        }
    }
    
    val baseWeight = 1f
    val expansionWeight = 1.15f
    val compressionWeight = 0.85f
    
    fun getWeight(id: Int): Float = when (lastClickedId) {
        id -> expansionWeight
        null -> baseWeight
        else -> compressionWeight
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // REPLAY -10s (Circle like PixelPlayer)
        val weightPrev by animateFloatAsState(getWeight(0), label = "prevW")
        Box(
            modifier = Modifier
                .weight(weightPrev)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.15f))
                .clickable(interactionSource = interactionSourcePrev, indication = ripple(), onClick = {
                    lastClickedId = 0
                    onPrevious()
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Replay10,
                contentDescription = "Replay 10s",
                modifier = Modifier.size(32.dp),
                tint = controlTint
            )
        }

        // PLAY / PAUSE (Smooth Squircle like PixelPlayer)
        val weightPlay by animateFloatAsState(getWeight(1), label = "playW")
        
        // Debounce loader: only show if loading persists > 300ms (prevents flicker on quick seeks)
        var showLoader by remember { mutableStateOf(false) }
        LaunchedEffect(isLoading) {
            if (isLoading) {
                kotlinx.coroutines.delay(500)
                showLoader = true
            } else {
                showLoader = false
            }
        }
        
        // PixelPlayer corner animation: 60dp (circle) when paused <-> 26dp (squircle) when playing
        val playCorner by animateDpAsState(
            targetValue = if (isPlaying) 26.dp else 60.dp,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "playCorner"
        )
        val playShape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = playCorner,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = playCorner,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = playCorner,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = playCorner,
            smoothnessAsPercentBR = 60
        )
        
        Box(
            modifier = Modifier
                .weight(weightPlay)
                .fillMaxHeight()
                .clip(playShape)
                .background(controlTint) // Vibrant/Primary
                .clickable(interactionSource = interactionSourcePlay, indication = ripple(), onClick = {
                    lastClickedId = 1
                    onPlayPause()
                }),
            contentAlignment = Alignment.Center
        ) {
            if (showLoader) {
                BoxLoreLoader.CircularWavy(
                    modifier = Modifier.size(36.dp),
                    size = 36.dp,
                    color = colorScheme.onPrimary
                )
            } else {
                 Crossfade(targetState = isPlaying, label = "playPauseCrossfade") { playing ->
                     Icon(
                         imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                         contentDescription = if (playing) "Pause" else "Play",
                         modifier = Modifier.size(36.dp),
                         tint = colorScheme.onPrimary
                     )
                 }
            }
        }

        // FORWARD +30s (Circle like PixelPlayer)
        val weightNext by animateFloatAsState(getWeight(2), label = "nextW")
        Box(
            modifier = Modifier
                .weight(weightNext)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.15f))
                .clickable(interactionSource = interactionSourceNext, indication = ripple(), onClick = {
                    lastClickedId = 2
                    onNext()
                }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Forward30,
                contentDescription = "Forward 30s",
                modifier = Modifier.size(32.dp),
                tint = controlTint
            )
        }
    }
}
