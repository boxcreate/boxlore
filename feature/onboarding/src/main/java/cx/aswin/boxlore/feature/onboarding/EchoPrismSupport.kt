package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.designsystem.theme.SpaceMonoFamily
import cx.aswin.boxlore.core.designsystem.theme.UnboundedFamily
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/*
 * What the Prism card needs besides drawing: its type, a measurer that sets words exactly as the
 * card will, the display faces' arrival, and the clock behind its slow motion.
 */

@Composable
internal fun prismHookStyle(): TextStyle = MaterialTheme.typography.bodyMedium.withSize(EchoPrismLayout.HookSp)

/** The pitch keeps its line spacing at every size: 20 on 14. */
internal fun TextStyle.withSize(sizeSp: Float): TextStyle = copy(fontSize = sizeSp.sp, lineHeight = (sizeSp * PrismHookLineHeight).sp)

internal fun prismTitleStyle(sizeSp: Float): TextStyle = TextStyle(
    fontFamily = UnboundedFamily,
    fontWeight = FontWeight.ExtraBold,
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * PrismTitleLineHeight).sp,
    letterSpacing = (-0.3).sp,
    brush = PrismColors.Title,
)

/** Measures the name and pitch exactly as [PrismTitle] and [PrismHook] will set them. */
@Composable
internal fun rememberPrismTextMeasure(): PrismTextMeasure {
    val measurer = rememberTextMeasurer()
    val hookStyle = prismHookStyle()
    return remember(measurer, hookStyle) {
        object : PrismTextMeasure {
            override fun title(text: String, sizeSp: Float, widthPx: Int): PrismTextBox = measurer
                .measure(AnnotatedString(text), prismTitleStyle(sizeSp), constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
                .let { PrismTextBox(it.lineCount, it.size.height) }

            override fun hook(text: String, sizeSp: Float, widthPx: Int): PrismTextBox = measurer
                .measure(AnnotatedString(text), hookStyle.withSize(sizeSp), constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)))
                .let { PrismTextBox(it.lineCount, it.size.height) }
        }
    }
}

/** False until the card's display faces have loaded (or failed and fallen back). */
@Composable
internal fun rememberPrismFontsReady(): State<Boolean> {
    val resolver = LocalFontFamilyResolver.current
    return produceState(false, resolver) {
        runCatching {
            resolver.preload(UnboundedFamily)
            resolver.preload(SpaceMonoFamily)
        }
        value = true
    }
}

/**
 * Seconds since the card appeared, for the turning edge, starburst and sweep. It stays at 0 with
 * animations off and pauses while the app is in the background.
 */
@Composable
internal fun rememberPrismClock(): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        if ((currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (last != 0L) seconds.floatValue = (seconds.floatValue + (now - last) / 1_000_000_000f) % PrismClockWrapSeconds
                    last = now
                }
            }
        }
    }
    return seconds
}

/** The sweep's position (0-1), easing back and forth over [PrismSweepSeconds]. */
internal fun prismSweep(seconds: Float): Float {
    val t = (seconds % (PrismSweepSeconds * 2)) / PrismSweepSeconds
    val there = if (t <= 1f) t else 2f - t
    return 0.5f - 0.5f * cos(there * PI.toFloat())
}

private const val PrismTitleLineHeight = 1.14f
private const val PrismHookLineHeight = 20f / 14f
private const val PrismSweepSeconds = 5f
private const val PrismClockWrapSeconds = 3_500f
