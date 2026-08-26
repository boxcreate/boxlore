package cx.aswin.boxlore.core.designsystem.theme

import android.content.Context
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb

/** ARGB Material roles used by home-screen widgets and other non-Compose chrome. */
data class BoxLoreChromeColors(
    @ColorInt val surface: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val onSurfaceVariant: Int,
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val onPrimaryContainer: Int,
    @ColorInt val secondaryContainer: Int,
    @ColorInt val onSecondaryContainer: Int,
)

fun resolveBoxLoreChromeColors(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    themeBrand: String,
    surfaceStyle: String,
): BoxLoreChromeColors {
    val scheme =
        resolveBoxLoreColorScheme(
            context = context,
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            themeBrand = themeBrand,
            surfaceStyle = surfaceStyle,
        )
    return BoxLoreChromeColors(
        surface = scheme.surface.toArgb(),
        onSurface = scheme.onSurface.toArgb(),
        onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
        primary = scheme.primary.toArgb(),
        onPrimary = scheme.onPrimary.toArgb(),
        primaryContainer = scheme.primaryContainer.toArgb(),
        onPrimaryContainer = scheme.onPrimaryContainer.toArgb(),
        secondaryContainer = scheme.secondaryContainer.toArgb(),
        onSecondaryContainer = scheme.onSecondaryContainer.toArgb(),
    )
}
