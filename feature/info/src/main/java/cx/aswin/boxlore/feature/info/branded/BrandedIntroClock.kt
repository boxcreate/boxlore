package cx.aswin.boxlore.feature.info.branded

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** A header intro's clock in milliseconds, read while drawing; [again] plays it once more. */
@Stable
internal class BrandedIntroClock(private val clock: Animatable<Float, *>, val again: () -> Unit) {
    fun millis(): Float = clock.value
}

/**
 * A header's intro, [endMillis] long. It starts once the header's art is [ready] and the page has
 * settled, the first time the page opens; it stands at the end after that (also after the page is
 * recreated), and always with animations off. A tap may play it again ([BrandedIntroClock.again]).
 */
@Composable
internal fun rememberBrandedIntro(ready: Boolean, endMillis: Float): BrandedIntroClock {
    val still = remember { !ValueAnimator.areAnimatorsEnabled() }
    var played by rememberSaveable { mutableStateOf(false) }
    val clock = remember { Animatable(if (played) endMillis else 0f) }
    var replays by remember { mutableIntStateOf(0) }
    LaunchedEffect(ready, still, replays) {
        if (!ready) return@LaunchedEffect
        if (still) {
            clock.snapTo(endMillis)
            played = true
            return@LaunchedEffect
        }
        if (replays == 0) {
            if (played) return@LaunchedEffect
            awaitPageSettled()
        }
        played = true
        clock.snapTo(0f)
        clock.animateTo(endMillis, tween(endMillis.toInt(), easing = LinearEasing))
    }
    return remember(clock, still) { BrandedIntroClock(clock) { if (!still) replays++ } }
}

/** How far [t] is through a step that starts at [start] and lasts [duration], from 0 to 1. */
internal fun introProgress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)
