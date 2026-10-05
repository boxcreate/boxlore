package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

internal data class NavigationAccentColors(val indicator: Color, val content: Color)

/** Regular tabs use the active accent itself, including exact custom colors. */
internal fun navigationAccentColors(scheme: ColorScheme): NavigationAccentColors =
    NavigationAccentColors(indicator = scheme.primary, content = scheme.onPrimary)

/** Lore's solid base changes from primary to primaryContainer as the shared indicator arrives. */
internal fun loreNavigationContentColor(scheme: ColorScheme, indicatorIndex: Float): Color {
    val blend = floatingNavigationLoreAuroraBlend(indicatorIndex)
    if (blend == 0f) return navigationAccentColors(scheme).content
    if (blend == 1f) return scheme.onPrimaryContainer
    val background = scheme.primaryContainer.copy(alpha = blend).compositeOver(scheme.primary)
    return if (navigationContrastRatio(background, scheme.onPrimary) >= navigationContrastRatio(background, scheme.onPrimaryContainer)) {
        scheme.onPrimary
    } else {
        scheme.onPrimaryContainer
    }
}

private fun navigationContrastRatio(background: Color, foreground: Color): Float {
    val backgroundLuminance = background.luminance()
    val foregroundLuminance = foreground.luminance()
    return (maxOf(backgroundLuminance, foregroundLuminance) + 0.05f) /
        (minOf(backgroundLuminance, foregroundLuminance) + 0.05f)
}
