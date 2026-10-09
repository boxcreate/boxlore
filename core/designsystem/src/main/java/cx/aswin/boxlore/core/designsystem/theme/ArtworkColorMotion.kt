package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal data class ColorSchemeTransition(val from: ColorScheme, val to: ColorScheme) {
    fun valueAt(fraction: Float): ColorScheme = interpolateArtworkColors(from, to, fraction)

    fun retarget(target: ColorScheme, fraction: Float): ColorSchemeTransition = ColorSchemeTransition(valueAt(fraction), target)
}

/** One clock for every role, retargeted from the visible palette rather than the original seed. */
@Composable
fun rememberAnimatedArtworkColors(target: ColorScheme, initialColors: ColorScheme = target, animationKey: Any? = null): ColorScheme {
    // A system light/dark switch stays immediate, avoiding unreadable mid-tone surfaces.
    val dark = LocalEffectiveDarkTheme.current
    val initial = if ((initialColors.background.luminance() < 0.5f) == dark) initialColors else target
    var transition by remember(dark, animationKey) { mutableStateOf(ColorSchemeTransition(initial, initial)) }
    val progress = remember(dark, animationKey) { Animatable(1f) }
    LaunchedEffect(target, progress) {
        if (transition.to !== target) {
            val next = transition.retarget(target, progress.value)
            progress.snapTo(0f)
            transition = next
            progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
    }
    return transition.valueAt(progress.value)
}

internal fun interpolateArtworkColors(from: ColorScheme, to: ColorScheme, fraction: Float): ColorScheme {
    val progress = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    if (progress == 0f) return from
    if (progress == 1f) return to
    return to.copy(
        primary = lerp(from.primary, to.primary, progress),
        onPrimary = lerp(from.onPrimary, to.onPrimary, progress),
        primaryContainer = lerp(from.primaryContainer, to.primaryContainer, progress),
        onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, progress),
        inversePrimary = lerp(from.inversePrimary, to.inversePrimary, progress),
        secondary = lerp(from.secondary, to.secondary, progress),
        onSecondary = lerp(from.onSecondary, to.onSecondary, progress),
        secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, progress),
        onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, progress),
        tertiary = lerp(from.tertiary, to.tertiary, progress),
        onTertiary = lerp(from.onTertiary, to.onTertiary, progress),
        tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, progress),
        onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, progress),
        background = lerp(from.background, to.background, progress),
        onBackground = lerp(from.onBackground, to.onBackground, progress),
        surface = lerp(from.surface, to.surface, progress),
        onSurface = lerp(from.onSurface, to.onSurface, progress),
        surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, progress),
        onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, progress),
        surfaceTint = lerp(from.surfaceTint, to.surfaceTint, progress),
        inverseSurface = lerp(from.inverseSurface, to.inverseSurface, progress),
        inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, progress),
        error = lerp(from.error, to.error, progress),
        onError = lerp(from.onError, to.onError, progress),
        errorContainer = lerp(from.errorContainer, to.errorContainer, progress),
        onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, progress),
        outline = lerp(from.outline, to.outline, progress),
        outlineVariant = lerp(from.outlineVariant, to.outlineVariant, progress),
        scrim = lerp(from.scrim, to.scrim, progress),
        surfaceBright = lerp(from.surfaceBright, to.surfaceBright, progress),
        surfaceDim = lerp(from.surfaceDim, to.surfaceDim, progress),
        surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, progress),
        surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, progress),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, progress),
        surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, progress),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, progress),
        primaryFixed = lerp(from.primaryFixed, to.primaryFixed, progress),
        primaryFixedDim = lerp(from.primaryFixedDim, to.primaryFixedDim, progress),
        onPrimaryFixed = lerp(from.onPrimaryFixed, to.onPrimaryFixed, progress),
        onPrimaryFixedVariant = lerp(from.onPrimaryFixedVariant, to.onPrimaryFixedVariant, progress),
        secondaryFixed = lerp(from.secondaryFixed, to.secondaryFixed, progress),
        secondaryFixedDim = lerp(from.secondaryFixedDim, to.secondaryFixedDim, progress),
        onSecondaryFixed = lerp(from.onSecondaryFixed, to.onSecondaryFixed, progress),
        onSecondaryFixedVariant = lerp(from.onSecondaryFixedVariant, to.onSecondaryFixedVariant, progress),
        tertiaryFixed = lerp(from.tertiaryFixed, to.tertiaryFixed, progress),
        tertiaryFixedDim = lerp(from.tertiaryFixedDim, to.tertiaryFixedDim, progress),
        onTertiaryFixed = lerp(from.onTertiaryFixed, to.onTertiaryFixed, progress),
        onTertiaryFixedVariant = lerp(from.onTertiaryFixedVariant, to.onTertiaryFixedVariant, progress),
    )
}
