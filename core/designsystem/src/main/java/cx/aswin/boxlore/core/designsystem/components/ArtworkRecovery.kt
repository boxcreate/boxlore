package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val LocalArtworkRecoveryGeneration = staticCompositionLocalOf { 0 }

/** Re-entering the foreground or reconnecting gives visible failed images a fresh bounded session. */
@Composable
fun rememberArtworkRecoveryGeneration(isOnline: Boolean): Int {
    var generation by remember { mutableIntStateOf(0) }
    var wasOnline by remember { mutableStateOf(isOnline) }
    LaunchedEffect(isOnline) {
        if (isOnline && !wasOnline) {
            ArtworkLoadPolicy.Shared.networkRestored()
            generation += 1
        }
        wasOnline = isOnline
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var stopped = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopped = true
            if (event == Lifecycle.Event.ON_START && stopped) {
                stopped = false
                generation += 1
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return generation
}
