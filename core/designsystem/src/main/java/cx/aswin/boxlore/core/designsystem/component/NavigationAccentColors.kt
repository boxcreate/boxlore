package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal data class NavigationAccentColors(val indicator: Color, val content: Color)

/** Regular tabs use the active accent itself, including exact custom colors. */
internal fun navigationAccentColors(scheme: ColorScheme): NavigationAccentColors =
    NavigationAccentColors(indicator = scheme.primary, content = scheme.onPrimary)
