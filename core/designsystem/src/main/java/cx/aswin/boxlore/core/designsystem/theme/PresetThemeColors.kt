package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette

/** Slightly richer wallpaper primary; the system's supporting hues, surfaces and errors remain. */
internal fun refineWallpaperColors(base: ColorScheme, isDark: Boolean): ColorScheme {
    // A fixed role anchors the same palette in both modes instead of sampling two different tones.
    val source = Hct.fromInt(base.primaryFixedDim.toArgb())
    val palette = TonalPalette.fromHueAndChroma(source.hue, (source.chroma * 1.12).coerceAtMost(64.0))
    fun tone(value: Int) = Color(palette.tone(value))
    return base.copy(
        primary = tone(if (isDark) 76 else 40),
        onPrimary = tone(if (isDark) 20 else 100),
        primaryContainer = tone(if (isDark) 30 else 90),
        onPrimaryContainer = tone(if (isDark) 90 else 20),
        inversePrimary = tone(if (isDark) 40 else 76),
        surfaceTint = tone(if (isDark) 76 else 40),
        primaryFixed = tone(90),
        primaryFixedDim = tone(80),
        onPrimaryFixed = tone(10),
        onPrimaryFixedVariant = tone(30),
    )
}

/** Complete neutral hierarchy for Pure, independent of the palette that supplies its accents. */
internal fun pureSurfaceColors(base: ColorScheme, isDark: Boolean): ColorScheme {
    fun neutral(light: Long, dark: Long) = Color(if (isDark) dark else light)
    val surface = if (isDark) Color.Black else Color.White
    return base.copy(
        background = surface,
        surface = surface,
        onBackground = neutral(0xFF1B1B1F, 0xFFE8E8EC),
        onSurface = neutral(0xFF1B1B1F, 0xFFE8E8EC),
        surfaceVariant = neutral(0xFFE4E4E8, 0xFF303034),
        onSurfaceVariant = neutral(0xFF48484F, 0xFFC7C7CE),
        outline = neutral(0xFF76767D, 0xFF92929A),
        outlineVariant = neutral(0xFFC9C9D0, 0xFF44444C),
        inverseSurface = neutral(0xFF303034, 0xFFE8E8EC),
        inverseOnSurface = neutral(0xFFF3F3F6, 0xFF303034),
        surfaceDim = neutral(0xFFDCDCE1, 0xFF000000),
        surfaceBright = neutral(0xFFFFFFFF, 0xFF35353A),
        surfaceContainerLowest = surface,
        surfaceContainerLow = neutral(0xFFFAFAFC, 0xFF0E0E10),
        surfaceContainer = neutral(0xFFF4F4F7, 0xFF17171B),
        surfaceContainerHigh = neutral(0xFFEDEDF1, 0xFF212126),
        surfaceContainerHighest = neutral(0xFFE5E5EB, 0xFF2C2C32),
    )
}
