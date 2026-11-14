package cx.aswin.boxlore.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import cx.aswin.boxlore.wear.R

/**
 * boxlore's dark colour roles on a true black ground, which is what a watch screen wants.
 * The values are the brand's dark theme; the dim roles are the same hues one step darker.
 */
internal val WatchColorScheme =
    ColorScheme(
        primary = Color(0xFF9C8CFF),
        primaryDim = Color(0xFF8576E0),
        primaryContainer = Color(0xFF3F347B),
        onPrimary = Color(0xFF21164B),
        onPrimaryContainer = Color(0xFFECE7FF),
        secondary = Color(0xFFA3B1CA),
        secondaryDim = Color(0xFF8C99B0),
        secondaryContainer = Color(0xFF354258),
        onSecondary = Color(0xFF253248),
        onSecondaryContainer = Color(0xFFE2E8F3),
        tertiary = Color(0xFF89C6B5),
        tertiaryDim = Color(0xFF75AB9C),
        tertiaryContainer = Color(0xFF274C41),
        onTertiary = Color(0xFF12392F),
        onTertiaryContainer = Color(0xFFD0EFE3),
        surfaceContainerLow = Color(0xFF1A1B22),
        surfaceContainer = Color(0xFF20212A),
        surfaceContainerHigh = Color(0xFF272832),
        onSurface = Color(0xFFE6E6EE),
        onSurfaceVariant = Color(0xFFC8C5D3),
        outline = Color(0xFF938F9F),
        outlineVariant = Color(0xFF494652),
        background = Color.Black,
        onBackground = Color(0xFFE6E6EE),
        error = Color(0xFFF2B8B5),
        errorDim = Color(0xFFD99E9B),
        errorContainer = Color(0xFF8C1D18),
        onError = Color(0xFF601410),
        onErrorContainer = Color(0xFFF9DEDC),
    )

/** Fully rounded lettering, the app's default. */
private const val ROUNDNESS = 100f

@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(weight: Int): Font =
    Font(
        resId = R.font.google_sans_flex_variable,
        weight = FontWeight(weight),
        variationSettings =
            FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("ROND", ROUNDNESS),
            ),
    )

internal val WatchFontFamily = FontFamily(googleSansFlex(400), googleSansFlex(500), googleSansFlex(600))

/**
 * Wear's type scale with every style moved onto Google Sans Flex. The stock styles each name the
 * system font, so passing a default family alone would leave them unchanged.
 */
private val WatchTypography =
    Typography().run {
        Typography(
            arcLarge = arcLarge.copy(fontFamily = WatchFontFamily),
            arcMedium = arcMedium.copy(fontFamily = WatchFontFamily),
            arcSmall = arcSmall.copy(fontFamily = WatchFontFamily),
            displayLarge = displayLarge.flex(),
            displayMedium = displayMedium.flex(),
            displaySmall = displaySmall.flex(),
            titleLarge = titleLarge.flex(),
            titleMedium = titleMedium.flex(),
            titleSmall = titleSmall.flex(),
            labelLarge = labelLarge.flex(),
            labelMedium = labelMedium.flex(),
            labelSmall = labelSmall.flex(),
            bodyLarge = bodyLarge.flex(),
            bodyMedium = bodyMedium.flex(),
            bodySmall = bodySmall.flex(),
            bodyExtraSmall = bodyExtraSmall.flex(),
            numeralExtraLarge = numeralExtraLarge.flex(),
            numeralLarge = numeralLarge.flex(),
            numeralMedium = numeralMedium.flex(),
            numeralSmall = numeralSmall.flex(),
            numeralExtraSmall = numeralExtraSmall.flex(),
        )
    }

private fun TextStyle.flex(): TextStyle = copy(fontFamily = WatchFontFamily)

@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WatchColorScheme,
        typography = WatchTypography,
        content = content,
    )
}
