package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeTonalSpot

/** Versioned, opaque theme_brand encoding. Old named/preset/hex keys remain readable. */
data class CustomThemeSeeds(val primary: Color, val secondary: Color? = null, val tertiary: Color? = null) {
    fun encode(): String = listOf("custom", "1", primary.toThemeBrandHex(), secondary?.toThemeBrandHex() ?: "auto", tertiary?.toThemeBrandHex() ?: "auto").joinToString(":")

    companion object {
        fun decode(key: String): CustomThemeSeeds? {
            val parts = key.split(":")
            if (parts.size != 5 || parts[0] != "custom" || parts[1] != "1") return null
            fun color(value: String): Color? = value.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }?.drop(1)?.toLongOrNull(16)?.let { Color(it or 0xFF000000) }
            val primary = color(parts[2]) ?: return null
            if (parts.drop(3).any { it != "auto" && color(it) == null }) return null
            return CustomThemeSeeds(primary, color(parts[3]), color(parts[4]))
        }
    }
}

/** Material Color Utilities supplies coordinated roles and contrast-aware foregrounds. */
fun generatePersonalColorScheme(seeds: CustomThemeSeeds, isDark: Boolean, surfaceStyle: String = SurfaceStyles.STANDARD): ColorScheme {
    val source = Hct.fromInt(seeds.primary.toArgb())
    val base = SchemeTonalSpot(source, isDark, 0.0)
    fun palette(color: Color): TonalPalette = Hct.fromInt(color.toArgb()).let { TonalPalette.fromHueAndChroma(it.hue, it.chroma.coerceAtMost(64.0)) }
    val scheme = DynamicScheme(
        source, base.variant, isDark, 0.0, palette(seeds.primary), seeds.secondary?.let(::palette) ?: base.secondaryPalette,
        seeds.tertiary?.let(::palette) ?: base.tertiaryPalette, base.neutralPalette, base.neutralVariantPalette
    )
    val roles = MaterialDynamicColors()
    val colors = lightColorScheme(
        primary = Color(roles.primary().getArgb(scheme)),
        onPrimary = Color(roles.onPrimary().getArgb(scheme)),
        primaryContainer = Color(roles.primaryContainer().getArgb(scheme)),
        onPrimaryContainer = Color(roles.onPrimaryContainer().getArgb(scheme)),
        inversePrimary = Color(roles.inversePrimary().getArgb(scheme)),
        secondary = Color(roles.secondary().getArgb(scheme)),
        onSecondary = Color(roles.onSecondary().getArgb(scheme)),
        secondaryContainer = Color(roles.secondaryContainer().getArgb(scheme)),
        onSecondaryContainer = Color(roles.onSecondaryContainer().getArgb(scheme)),
        tertiary = Color(roles.tertiary().getArgb(scheme)),
        onTertiary = Color(roles.onTertiary().getArgb(scheme)),
        tertiaryContainer = Color(roles.tertiaryContainer().getArgb(scheme)),
        onTertiaryContainer = Color(roles.onTertiaryContainer().getArgb(scheme)),
        background = Color(roles.background().getArgb(scheme)),
        onBackground = Color(roles.onBackground().getArgb(scheme)),
        surface = Color(roles.surface().getArgb(scheme)),
        onSurface = Color(roles.onSurface().getArgb(scheme)),
        surfaceVariant = Color(roles.surfaceVariant().getArgb(scheme)),
        onSurfaceVariant = Color(roles.onSurfaceVariant().getArgb(scheme)),
        surfaceTint = Color(roles.surfaceTint().getArgb(scheme)),
        inverseSurface = Color(roles.inverseSurface().getArgb(scheme)),
        inverseOnSurface = Color(roles.inverseOnSurface().getArgb(scheme)),
        error = Color(roles.error().getArgb(scheme)),
        onError = Color(roles.onError().getArgb(scheme)),
        errorContainer = Color(roles.errorContainer().getArgb(scheme)),
        onErrorContainer = Color(roles.onErrorContainer().getArgb(scheme)),
        outline = Color(roles.outline().getArgb(scheme)),
        outlineVariant = Color(roles.outlineVariant().getArgb(scheme)),
        scrim = Color(roles.scrim().getArgb(scheme)),
        surfaceBright = Color(roles.surfaceBright().getArgb(scheme)),
        surfaceDim = Color(roles.surfaceDim().getArgb(scheme)),
        surfaceContainer = Color(roles.surfaceContainer().getArgb(scheme)),
        surfaceContainerHigh = Color(roles.surfaceContainerHigh().getArgb(scheme)),
        surfaceContainerHighest = Color(roles.surfaceContainerHighest().getArgb(scheme)),
        surfaceContainerLow = Color(roles.surfaceContainerLow().getArgb(scheme)),
        surfaceContainerLowest = Color(roles.surfaceContainerLowest().getArgb(scheme)),
        primaryFixed = Color(roles.primaryFixed().getArgb(scheme)),
        primaryFixedDim = Color(roles.primaryFixedDim().getArgb(scheme)),
        onPrimaryFixed = Color(roles.onPrimaryFixed().getArgb(scheme)),
        onPrimaryFixedVariant = Color(roles.onPrimaryFixedVariant().getArgb(scheme)),
        secondaryFixed = Color(roles.secondaryFixed().getArgb(scheme)),
        secondaryFixedDim = Color(roles.secondaryFixedDim().getArgb(scheme)),
        onSecondaryFixed = Color(roles.onSecondaryFixed().getArgb(scheme)),
        onSecondaryFixedVariant = Color(roles.onSecondaryFixedVariant().getArgb(scheme)),
        tertiaryFixed = Color(roles.tertiaryFixed().getArgb(scheme)),
        tertiaryFixedDim = Color(roles.tertiaryFixedDim().getArgb(scheme)),
        onTertiaryFixed = Color(roles.onTertiaryFixed().getArgb(scheme)),
        onTertiaryFixedVariant = Color(roles.onTertiaryFixedVariant().getArgb(scheme)),
    )
    return applySurfaceStyle(colors, isDark, surfaceStyle)
}

/** Detail pages retain pure surfaces and legacy mode locks, but use artwork-tinted surfaces otherwise. */
fun artworkSurfaceStyle(surfaceStyle: String): String = when (surfaceStyle) {
    SurfaceStyles.AMOLED, SurfaceStyles.PURE_WHITE, SurfaceStyles.DYNAMIC_OLED_WHITE -> surfaceStyle
    else -> SurfaceStyles.STANDARD
}

data class ArtworkColorCandidate(val argb: Int, val population: Int)

/** Avoid white margins, black lettering and tiny bright details; monochrome art keeps the app palette. */
fun selectArtworkSeed(candidates: List<ArtworkColorCandidate>): Int? {
    val total = candidates.sumOf { it.population.coerceAtLeast(0) }.coerceAtLeast(1)
    return candidates.filter { it.population.toDouble() / total >= 0.01 }.map { it to Hct.fromInt(it.argb) }
        .filter { (_, hct) -> hct.chroma >= 8.0 && hct.tone in 12.0..92.0 }
        .maxByOrNull { (candidate, hct) -> candidate.population.toDouble() / total * 70.0 + hct.chroma.coerceAtMost(64.0) * 0.3 }?.first?.argb
}
