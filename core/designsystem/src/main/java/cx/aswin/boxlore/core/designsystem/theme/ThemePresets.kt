package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** A complete background and accent theme with light/dark variants. Theme mode stays independent. */
@Immutable
data class ThemePreset(
    val key: String,
    val name: String,
    val description: String,
    val background: PresetColors,
    val primary: PresetColors,
    val secondary: PresetColors,
    val tertiary: PresetColors,
)

@Immutable
data class PresetColors(val light: Color, val dark: Color)

/** Stable keys use surface_style and theme_brand, including backup and fast-start caches. */
val ThemePresets: List<ThemePreset> = listOf(
    ThemePreset(
        "preset:aurora",
        "Aurora",
        "Violet, teal & rose",
        PresetColors(Color(0xFFF7F3FC), Color(0xFF15131D)),
        PresetColors(Color(0xFF6353A4), Color(0xFFCEC0FF)),
        PresetColors(Color(0xFF346B66), Color(0xFFA0D0C6)),
        PresetColors(Color(0xFF985368), Color(0xFFF4B2C8)),
    ),
    ThemePreset(
        "preset:tide",
        "Tide",
        "Ocean blue & warm sand",
        PresetColors(Color(0xFFEFF7F8), Color(0xFF101A1D)),
        PresetColors(Color(0xFF006874), Color(0xFF7BD0DD)),
        PresetColors(Color(0xFF486176), Color(0xFFB0C9DF)),
        PresetColors(Color(0xFF7B5D36), Color(0xFFEDC18F)),
    ),
    ThemePreset(
        "preset:moss",
        "Moss",
        "Forest green & soft clay",
        PresetColors(Color(0xFFF3F6EE), Color(0xFF131B13)),
        PresetColors(Color(0xFF42633C), Color(0xFFA7CE9C)),
        PresetColors(Color(0xFF5A654B), Color(0xFFBECBAD)),
        PresetColors(Color(0xFF806044), Color(0xFFE7C09E)),
    ),
    ThemePreset(
        "preset:dune",
        "Dune",
        "Golden sand & olive",
        PresetColors(Color(0xFFFBF5EA), Color(0xFF1D1811)),
        PresetColors(Color(0xFF77582E), Color(0xFFE8BF88)),
        PresetColors(Color(0xFF6B5E4C), Color(0xFFD5C4AC)),
        PresetColors(Color(0xFF51664B), Color(0xFFB8CFAE)),
    ),
    ThemePreset(
        "preset:ember",
        "Ember",
        "Terracotta & muted gold",
        PresetColors(Color(0xFFFFF3EC), Color(0xFF21140F)),
        PresetColors(Color(0xFF9A432D), Color(0xFFFFB59C)),
        PresetColors(Color(0xFF7C5645), Color(0xFFEABAA4)),
        PresetColors(Color(0xFF626238), Color(0xFFCCCC9D)),
    ),
    ThemePreset(
        "preset:rosewood",
        "Rosewood",
        "Dusty rose & warm bronze",
        PresetColors(Color(0xFFFCF2F4), Color(0xFF201318)),
        PresetColors(Color(0xFF8C4A60), Color(0xFFF4B3CA)),
        PresetColors(Color(0xFF715765), Color(0xFFDDBDCD)),
        PresetColors(Color(0xFF766039), Color(0xFFE7C592)),
    ),
    ThemePreset(
        "preset:iris",
        "Iris",
        "Soft lilac & peach",
        PresetColors(Color(0xFFF7F2FC), Color(0xFF191522)),
        PresetColors(Color(0xFF665290), Color(0xFFD2BCF4)),
        PresetColors(Color(0xFF5D607E), Color(0xFFC5C3EB)),
        PresetColors(Color(0xFF826050), Color(0xFFEFBDA8)),
    ),
    ThemePreset(
        "preset:glacier",
        "Glacier",
        "Glacial blue & eucalyptus",
        PresetColors(Color(0xFFF2F6FC), Color(0xFF121922)),
        PresetColors(Color(0xFF3E6289), Color(0xFFACC9F1)),
        PresetColors(Color(0xFF536679), Color(0xFFBBCBDD)),
        PresetColors(Color(0xFF546B61), Color(0xFFB2D1C2)),
    ),
    ThemePreset(
        "preset:lagoon",
        "Lagoon",
        "Jade, sea glass & honey",
        PresetColors(Color(0xFFEEF8F2), Color(0xFF0F1C18)),
        PresetColors(Color(0xFF236A57), Color(0xFF91D5BD)),
        PresetColors(Color(0xFF466567), Color(0xFFADCDD0)),
        PresetColors(Color(0xFF78613A), Color(0xFFE6C693)),
    ),
    ThemePreset(
        "preset:ink",
        "Ink",
        "Slate blue & warm taupe",
        PresetColors(Color(0xFFF3F4F7), Color(0xFF14181E)),
        PresetColors(Color(0xFF4B5D78), Color(0xFFB9C8E7)),
        PresetColors(Color(0xFF62606B), Color(0xFFCBC5D4)),
        PresetColors(Color(0xFF745846), Color(0xFFE4BCA4)),
    ),
)

fun findThemePreset(themeBrand: String): ThemePreset? = ThemePresets.firstOrNull { it.key == themeBrand }

/** Apply authored accent roles without replacing the user's selected background treatment. */
internal fun ColorScheme.withThemePreset(preset: ThemePreset, isDark: Boolean): ColorScheme {
    fun PresetColors.color(): Color = if (isDark) dark else light
    fun Color.container(): Color = if (isDark) {
        lerp(Color(0xFF111316), this, 0.25f)
    } else {
        lerp(this, Color.White, 0.87f)
    }

    val primary = preset.primary.color()
    val secondary = preset.secondary.color()
    val tertiary = preset.tertiary.color()
    val onAccent = if (isDark) Color(0xFF17191D) else Color.White
    val onContainer = if (isDark) Color(0xFFE8EAED) else Color(0xFF211D15)
    return copy(
        primary = primary,
        onPrimary = onAccent,
        primaryContainer = primary.container(),
        onPrimaryContainer = onContainer,
        inversePrimary = if (isDark) preset.primary.light else preset.primary.dark,
        secondary = secondary,
        onSecondary = onAccent,
        secondaryContainer = secondary.container(),
        onSecondaryContainer = onContainer,
        tertiary = tertiary,
        onTertiary = onAccent,
        tertiaryContainer = tertiary.container(),
        onTertiaryContainer = onContainer,
        surfaceTint = primary,
    )
}

/** Preset surfaces remain selected when the user later personalises their accent or wallpaper colors. */
internal fun ColorScheme.withPresetBackground(preset: ThemePreset, isDark: Boolean): ColorScheme {
    val background = if (isDark) preset.background.dark else preset.background.light
    val tint = if (isDark) preset.primary.dark else preset.primary.light
    val foreground = if (isDark) Color(0xFFE8EAED) else Color(0xFF211D15)
    val variant = if (isDark) Color(0xFFB6BCC4) else Color(0xFF5F5A50)
    fun container(amount: Float): Color = lerp(background, tint, amount)
    return copy(
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceDim = if (isDark) background else container(0.12f),
        surfaceBright = if (isDark) container(0.16f) else background,
        surfaceContainerLowest = if (isDark) lerp(background, Color.Black, 0.22f) else Color.White,
        surfaceContainerLow = container(0.04f),
        surfaceContainer = container(0.07f),
        surfaceContainerHigh = container(0.10f),
        surfaceContainerHighest = container(0.14f),
        surfaceVariant = container(0.10f),
        onSurfaceVariant = variant,
        outline = lerp(background, foreground, 0.5f),
        outlineVariant = lerp(background, foreground, 0.22f),
    )
}
