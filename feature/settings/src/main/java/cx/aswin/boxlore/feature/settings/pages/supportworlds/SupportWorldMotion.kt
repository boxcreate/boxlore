package cx.aswin.boxlore.feature.settings.pages.supportworlds

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** True when the system animator scale is off, so worlds hold still instead of looping. */
@Composable
internal fun rememberSupportReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A 0→1 loop, or a constant 0 when [enabled] is false so idle worlds cost nothing. */
@Composable
internal fun rememberSupportLoop(
    enabled: Boolean,
    durationMillis: Int,
    label: String,
    reverse: Boolean = true,
    easing: Easing = FastOutSlowInEasing,
): State<Float> {
    if (!enabled) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = easing),
            repeatMode = if (reverse) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = label,
    )
}

/** Every world is dark, so status bar icons stay light while the page is shown. */
@Composable
internal fun SupportDarkSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { previous?.let { controller.isAppearanceLightStatusBars = it } }
    }
}

/** Debug builds get a world switcher. Checked at runtime because this module has no BuildConfig. */
@Composable
internal fun rememberIsDebuggableApp(): Boolean {
    val context = LocalContext.current
    return remember(context) { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
}

internal fun android.content.Context.findActivity(): Activity? {
    var current: android.content.Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
