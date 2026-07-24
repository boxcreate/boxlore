package cx.aswin.boxlore.core.designsystem.theme

import android.os.Build
import android.util.Log
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.R

private const val TAG = "BoxLoreTypography"

// Google Fonts Provider for dynamic font loading
private val googleFontProvider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

@OptIn(ExperimentalTextApi::class)
fun buildGoogleSansFamily(roundness: Float): FontFamily =
    flexFontFamilyOrFallback(
        flexFace(weight = 300, opsz = 17f, roundness = roundness, fontWeight = FontWeight.Light),
        flexFace(weight = 400, opsz = 17f, roundness = roundness, fontWeight = FontWeight.Normal),
        flexFace(weight = 500, opsz = 17f, roundness = roundness, fontWeight = FontWeight.Medium),
        flexFace(weight = 600, opsz = 17f, roundness = roundness, fontWeight = FontWeight.SemiBold),
        flexFace(weight = 700, opsz = 17f, roundness = roundness, fontWeight = FontWeight.Bold),
    )

@OptIn(ExperimentalTextApi::class)
fun buildSectionHeaderFontFamily(roundness: Float): FontFamily =
    flexFontFamilyOrFallback(
        flexFace(weight = 800, opsz = 24f, roundness = roundness, fontWeight = FontWeight.ExtraBold),
    )

@OptIn(ExperimentalTextApi::class)
fun buildCondensedGoogleSansFamily(roundness: Float): FontFamily =
    flexFontFamilyOrFallback(
        Font(
            R.font.google_sans_flex_variable,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(700),
                    FontVariation.Setting("wdth", 75f),
                    FontVariation.Setting("ROND", roundness),
                ),
            weight = FontWeight.Bold,
        ),
    )

fun buildBoxLoreTypography(roundness: Float): Typography {
    val family = buildGoogleSansFamily(roundness)
    return Typography(
        displayLarge = boxLoreTextStyle(family, FontWeight.SemiBold, 57, 60, -0.5f),
        displayMedium = boxLoreTextStyle(family, FontWeight.SemiBold, 45, 48, -0.3f),
        displaySmall = boxLoreTextStyle(family, FontWeight.Medium, 36, 40, -0.25f),
        headlineLarge = boxLoreTextStyle(family, FontWeight.Bold, 32, 36, -0.3f),
        headlineMedium = boxLoreTextStyle(family, FontWeight.SemiBold, 28, 32, -0.2f),
        headlineSmall = boxLoreTextStyle(family, FontWeight.SemiBold, 24, 28, -0.1f),
        titleLarge = boxLoreTextStyle(family, FontWeight.Medium, 22, 26, 0f),
        titleMedium = boxLoreTextStyle(family, FontWeight.SemiBold, 16, 22, 0.1f),
        titleSmall = boxLoreTextStyle(family, FontWeight.SemiBold, 14, 18, 0.1f),
        bodyLarge = boxLoreTextStyle(family, FontWeight.Normal, 16, 24, 0.3f),
        bodyMedium = boxLoreTextStyle(family, FontWeight.Normal, 14, 20, 0.2f),
        bodySmall = boxLoreTextStyle(family, FontWeight.Normal, 12, 16, 0.3f),
        labelLarge = boxLoreTextStyle(family, FontWeight.SemiBold, 14, 18, 0.1f),
        labelMedium = boxLoreTextStyle(family, FontWeight.Medium, 12, 16, 0.4f),
        labelSmall = boxLoreTextStyle(family, FontWeight.Medium, 11, 14, 0.4f),
    )
}

/** Soft (ROND 50) family — use [buildGoogleSansFamily] / [LocalFontRoundness] when prefs matter. */
val GoogleSansFamily: FontFamily = buildGoogleSansFamily(GoogleSansFlexRoundness)

/** Soft section-header family — prefer [rememberSectionHeaderFontFamily] in Compose. */
val SectionHeaderFontFamily: FontFamily = buildSectionHeaderFontFamily(GoogleSansFlexRoundness)

/** Soft Material 3 scale — prefer [buildBoxLoreTypography] via [BoxLoreTheme]. */
val BoxLoreTypography: Typography = buildBoxLoreTypography(GoogleSansFlexRoundness)

@Composable
fun rememberSectionHeaderFontFamily(): FontFamily {
    val roundness = LocalFontRoundness.current
    return remember(roundness) { buildSectionHeaderFontFamily(roundness) }
}

@Composable
fun rememberCondensedGoogleSansFamily(): FontFamily {
    val roundness = LocalFontRoundness.current
    return remember(roundness) { buildCondensedGoogleSansFamily(roundness) }
}

// Keep Roboto Flex for legacy/fallback or specific variable axes needs
private val robotoFlex = GoogleFont("Roboto Flex")
val RobotoFlexFamily =
    FontFamily(
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.Light),
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.Normal),
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.Medium),
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.Bold),
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.ExtraBold),
        Font(googleFont = robotoFlex, fontProvider = googleFontProvider, weight = FontWeight.Black),
    ).also { Log.d(TAG, "Roboto Flex loaded via Google Fonts provider") }

// Logo Font with Variable Axes - Using bundled TTF for full axis control
@OptIn(ExperimentalTextApi::class)
val LogoFontFamily =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(
            Font(
                R.font.robotoflex_variable,
                variationSettings =
                    FontVariation.Settings(
                        FontVariation.weight(700),
                        FontVariation.width(110f),
                        FontVariation.Setting("GRAD", 50f),
                        FontVariation.Setting("opsz", 48f),
                    ),
            ),
        )
    } else {
        RobotoFlexFamily
    }

@OptIn(ExperimentalTextApi::class)
private fun flexFontFamilyOrFallback(vararg faces: Font): FontFamily =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(*faces)
    } else {
        FontFamily(Font(R.font.google_sans_flex_variable))
    }

private fun boxLoreTextStyle(
    family: FontFamily,
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: Float,
): TextStyle =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

@OptIn(ExperimentalTextApi::class)
private fun flexFace(
    weight: Int,
    opsz: Float,
    roundness: Float,
    fontWeight: FontWeight,
): Font =
    Font(
        R.font.google_sans_flex_variable,
        variationSettings =
            FontVariation.Settings(
                FontVariation.weight(weight),
                FontVariation.Setting("opsz", opsz),
                FontVariation.Setting("ROND", roundness),
            ),
        weight = fontWeight,
    )
