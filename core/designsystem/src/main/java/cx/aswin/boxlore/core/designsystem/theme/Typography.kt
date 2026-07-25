package cx.aswin.boxlore.core.designsystem.theme

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
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

/** App-wide Google Sans Flex weight scale, intentionally lighter than the nominal Material names. */
object GoogleSansWeight {
    val regular = FontWeight.Normal
    val medium = FontWeight.Normal
    val semiBold = FontWeight.Medium
    val bold = FontWeight.SemiBold
    val extraBold = FontWeight.SemiBold
}

fun buildGoogleSansFamily(
    context: Context,
    roundness: Float,
    weight: Int = 400,
    opticalSize: Float? = 17f,
    width: Float? = null,
): FontFamily =
    FontFamily(
        GoogleSansFlexTypeface.create(
            context = context,
            weight = weight,
            roundness = roundness.toInt(),
            opticalSize = opticalSize,
            width = width,
        ),
    )

fun buildSectionHeaderFontFamily(
    context: Context,
    roundness: Float,
): FontFamily =
    buildGoogleSansFamily(
        context,
        roundness,
        weight = GoogleSansWeight.bold.weight,
        opticalSize = 24f,
    )

fun buildCondensedGoogleSansFamily(
    context: Context,
    roundness: Float,
): FontFamily =
    buildGoogleSansFamily(
        context,
        roundness,
        weight = GoogleSansWeight.bold.weight,
        opticalSize = null,
        width = 75f,
    )

@Composable
fun rememberGoogleSansFamily(
    weight: Int = 400,
    opticalSize: Float? = 17f,
    width: Float? = null,
): FontFamily {
    val context = LocalContext.current
    val roundness = LocalFontRoundness.current
    return remember(context, roundness, weight, opticalSize, width) {
        buildGoogleSansFamily(
            context = context,
            roundness = roundness,
            weight = weight,
            opticalSize = opticalSize,
            width = width,
        )
    }
}

fun buildBoxLoreTypography(
    context: Context,
    roundness: Float,
): Typography {
    return Typography(
        displayLarge = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 57, 60, -0.5f),
        displayMedium = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 45, 48, -0.3f),
        displaySmall = boxLoreTextStyle(context, roundness, GoogleSansWeight.medium.weight, 36, 40, -0.25f),
        headlineLarge = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 32, 36, -0.3f),
        headlineMedium = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 28, 32, -0.2f),
        headlineSmall = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 24, 28, -0.1f),
        titleLarge = boxLoreTextStyle(context, roundness, GoogleSansWeight.medium.weight, 22, 26, 0f),
        titleMedium = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 16, 22, 0.1f),
        titleSmall = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 14, 18, 0.1f),
        bodyLarge = boxLoreTextStyle(context, roundness, GoogleSansWeight.regular.weight, 16, 24, 0.3f),
        bodyMedium = boxLoreTextStyle(context, roundness, GoogleSansWeight.regular.weight, 14, 20, 0.2f),
        bodySmall = boxLoreTextStyle(context, roundness, GoogleSansWeight.regular.weight, 12, 16, 0.3f),
        labelLarge = boxLoreTextStyle(context, roundness, GoogleSansWeight.semiBold.weight, 14, 18, 0.1f),
        labelMedium = boxLoreTextStyle(context, roundness, GoogleSansWeight.medium.weight, 12, 16, 0.4f),
        labelSmall = boxLoreTextStyle(context, roundness, GoogleSansWeight.medium.weight, 11, 14, 0.4f),
    )
}

@Composable
fun rememberSectionHeaderFontFamily(): FontFamily {
    val roundness = LocalFontRoundness.current
    val context = LocalContext.current
    return remember(context, roundness) { buildSectionHeaderFontFamily(context, roundness) }
}

@Composable
fun rememberCondensedGoogleSansFamily(): FontFamily {
    val roundness = LocalFontRoundness.current
    val context = LocalContext.current
    return remember(context, roundness) { buildCondensedGoogleSansFamily(context, roundness) }
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
private fun boxLoreTextStyle(
    context: Context,
    roundness: Float,
    weight: Int,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: Float,
): TextStyle =
    TextStyle(
        fontFamily = buildGoogleSansFamily(context, roundness, weight = weight),
        fontWeight = FontWeight.Normal,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )
