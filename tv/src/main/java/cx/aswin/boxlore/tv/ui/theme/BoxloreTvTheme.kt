package cx.aswin.boxlore.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import cx.aswin.boxlore.tv.R

/**
 * boxlore's dark colour roles from the brand kit, for a screen watched in a dim room. Cards and
 * panels are solid; the iris primary marks the action the remote is on.
 */
object TvColors {
    val background = Color(0xFF121318)
    val surfaceLow = Color(0xFF1A1B22)
    val surface = Color(0xFF20212A)
    val surfaceHigh = Color(0xFF272832)
    val surfaceHighest = Color(0xFF32333D)
    val onSurface = Color(0xFFE6E6EE)
    val onSurfaceVariant = Color(0xFFC8C5D3)

    /** The quietest text, such as episode notes: still 4.5:1 on the focused surface. */
    val onSurfaceMuted = Color(0xFF9F9CAA)
    val outline = Color(0xFF938F9F)
    val outlineVariant = Color(0xFF494652)
    val primary = Color(0xFF9C8CFF)
    val onPrimary = Color(0xFF21164B)
    val primaryContainer = Color(0xFF3F347B)
    val onPrimaryContainer = Color(0xFFECE7FF)
    val secondaryContainer = Color(0xFF354258)
    val onSecondaryContainer = Color(0xFFE2E8F3)
    val tertiaryContainer = Color(0xFF274C41)
    val onTertiaryContainer = Color(0xFFD0EFE3)
    val error = Color(0xFFF2B8B5)
    val scrim = Color(0xFF000000)

    /** The light outline the remote's item gets: the on-surface colour, readable on any artwork. */
    val focusOutline = onSurface
}

/**
 * The TV screen is 960 × 540 dp. Text and controls stay inside the 5% safe area (48 dp at the
 * sides, 27 dp top and bottom) so no TV crops them; content starts right of the collapsed rail.
 */
object TvSpace {
    val safeSide = 48.dp
    val safeTop = 27.dp
    val contentStart = 124.dp
    val sectionGap = 22.dp
}

private val googleSansFlex =
    FontFamily(
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold).map { weight ->
            Font(
                resId = R.font.google_sans_flex_variable,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.Setting("ROND", ROUNDNESS)),
            )
        },
    )

private const val ROUNDNESS = 100f

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = googleSansFlex, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** The Material 3 scale in Google Sans Flex, as on the phone, at TV sizes (body never below 14 sp). */
private val tvTypography =
    Typography(
        displayLarge = style(57, 64, FontWeight.Medium),
        displayMedium = style(45, 52, FontWeight.Medium),
        displaySmall = style(36, 44),
        headlineLarge = style(32, 40, FontWeight.Medium),
        headlineMedium = style(28, 36, FontWeight.Medium),
        headlineSmall = style(24, 32, FontWeight.Medium),
        titleLarge = style(22, 28),
        titleMedium = style(18, 24, FontWeight.Medium),
        titleSmall = style(15, 20, FontWeight.Medium),
        bodyLarge = style(16, 24),
        bodyMedium = style(15, 22),
        bodySmall = style(14, 20),
        labelLarge = style(15, 20, FontWeight.Medium),
        labelMedium = style(14, 18),
        labelSmall = style(13, 16),
    )

@Composable
fun BoxloreTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
        darkColorScheme(
            primary = TvColors.primary,
            onPrimary = TvColors.onPrimary,
            primaryContainer = TvColors.primaryContainer,
            onPrimaryContainer = TvColors.onPrimaryContainer,
            secondaryContainer = TvColors.secondaryContainer,
            onSecondaryContainer = TvColors.onSecondaryContainer,
            tertiaryContainer = TvColors.tertiaryContainer,
            onTertiaryContainer = TvColors.onTertiaryContainer,
            background = TvColors.background,
            onBackground = TvColors.onSurface,
            surface = TvColors.surface,
            onSurface = TvColors.onSurface,
            surfaceVariant = TvColors.surfaceHighest,
            onSurfaceVariant = TvColors.onSurfaceVariant,
            error = TvColors.error,
            scrim = TvColors.scrim,
        ),
        typography = tvTypography,
        content = content,
    )
}
