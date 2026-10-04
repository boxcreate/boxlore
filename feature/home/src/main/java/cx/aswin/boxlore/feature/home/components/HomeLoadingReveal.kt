package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal const val HomeLoadingHandoffMillis = 180

/** Fade the loading cover over opaque content, so a stalled frame cannot expose a hole. */
@Composable
internal fun HomeLoadingReveal(
    ready: Boolean,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val coverAlpha = remember { Animatable(if (ready) 0f else 1f) }
    var showCover by remember { mutableStateOf(!ready) }
    LaunchedEffect(ready) {
        if (ready) {
            coverAlpha.animateTo(0f, tween(HomeLoadingHandoffMillis))
            showCover = false
        } else {
            coverAlpha.snapTo(1f)
            showCover = true
        }
    }
    Box(modifier) {
        if (ready) {
            content()
            if (showCover) {
                Box(
                    Modifier.matchParentSize().graphicsLayer { alpha = coverAlpha.value }
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Box(Modifier.fillMaxSize()) { placeholder() }
                }
            }
        } else {
            placeholder()
        }
    }
}
