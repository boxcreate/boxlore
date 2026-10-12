package cx.aswin.boxlore.feature.player.v2

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

internal data class MinimalControlAccent(val container: Color, val content: Color)

/** Keep the artwork's own accent family; distinguish small active controls by tone. */
internal fun minimalControlAccent(colors: ColorScheme): MinimalControlAccent =
    MinimalControlAccent(colors.tertiaryContainer, colors.onTertiaryContainer)
