package cx.aswin.boxlore.feature.info.branded.daily

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle
import cx.aswin.boxlore.feature.info.branded.VariableWeights
import cx.aswin.boxlore.feature.info.branded.rememberVariableFont
import cx.aswin.boxlore.feature.info.branded.rememberVariableWeights

/** The Daily as a front page: newsprint and ink by day, a warm night edition in the dark, and the show's dawn. */
internal object DailyColors {
    /** The show's dawn, from the cover's sky: deep blue at the top to yellow at the horizon. */
    val Dawn = listOf(
        0f to Color(0xFF0054B7),
        0.17f to Color(0xFF006AB8),
        0.33f to Color(0xFF0084C5),
        0.5f to Color(0xFF01A2D9),
        0.67f to Color(0xFF00C2EE),
        0.75f to Color(0xFF37D0CE),
        0.83f to Color(0xFF81DD9A),
        0.92f to Color(0xFFC0E86E),
        1f to Color(0xFFF2F050),
    )

    fun dawn(): Brush = Brush.verticalGradient(*Dawn.toTypedArray())

    val Light = DailyPalette(
        page = Color(0xFFF6F3EB),
        ink = Color(0xFF121212),
        sub = Color(0xFF55534D),
        control = Color(0xFFE7E2D5),
        card = Color(0xFFEDE9DD),
        chipLine = Color(0xFF121212),
        kicker = Color(0xFF0054B7),
        tagMark = Color(0xFF00C2EE),
        soft = Color(0xFFD8D2C2),
        navigation = Color(0xFFE7E2D5),
        navigationText = Color(0xFF2A2925),
        copyright = BrandedCopyrightStyle(
            icon = Color(0xFF55534D),
            popup = Color.White,
            title = Color(0xFF121212),
            body = Color(0xFF3D3B36),
            note = Color(0xFF6B6860),
            shadow = Color(0x38281E14),
            ring = Color(0x1F121212),
            popupWidth = 241.dp,
            caretCentre = 227.dp,
            gap = 6.dp,
            overhangTop = 2.dp,
            overhangBottom = 2.dp,
            iconSize = 15.dp,
        ),
    )

    /** The night edition: warm charcoal and cream ink, softer than black and white. */
    val Dark = DailyPalette(
        page = Color(0xFF1B1A18),
        ink = Color(0xFFE8E3D8),
        sub = Color(0xFFA9A398),
        control = Color(0xFF2B2A27),
        card = Color(0xFF232220),
        chipLine = Color(0xFF5C5850),
        kicker = Color(0xFF7CC3E6),
        tagMark = Color(0xFF0054B7),
        soft = Color(0xFF353330),
        navigation = Color(0xFF262522),
        navigationText = Color(0xFFD8D3C8),
        copyright = BrandedCopyrightStyle(
            icon = Color(0xFFA9A398),
            popup = Color(0xFF2A2926),
            title = Color(0xFFE8E3D8),
            body = Color(0xFFD6D1C5),
            note = Color(0xFFA9A398),
            shadow = Color(0x99000000),
            ring = Color.Transparent,
            popupWidth = 241.dp,
            caretCentre = 227.dp,
            gap = 6.dp,
            overhangTop = 2.dp,
            overhangBottom = 2.dp,
            iconSize = 15.dp,
        ),
    )

    /** The palette for the theme the page is drawn in. */
    val current: DailyPalette
        @Composable
        @ReadOnlyComposable
        get() = if (LocalEffectiveDarkTheme.current) Dark else Light
}

/** One edition's colours. Ink is the accent: buttons, the genre chip, Play and the navbar's selected tab. */
internal data class DailyPalette(
    val page: Color,
    val ink: Color,
    val sub: Color,
    val control: Color,
    /** The episode cards in the list. */
    val card: Color,
    val chipLine: Color,
    /** "Latest episode", and each card's release time. */
    val kicker: Color,
    /** boxlore's mark in the top-bar tag: a blue from the dawn that holds up on the ink tag (or the cream one at night). */
    val tagMark: Color,
    /** The fine rules around the lead story's buttons, and a day still to come in the week's boxes. */
    val soft: Color,
    val navigation: Color,
    val navigationText: Color,
    val copyright: BrandedCopyrightStyle,
) {
    /** The lead story's buttons, as on the episode cards. */
    val actions: BrandedActionColors
        get() = BrandedActionColors(toggle = control, onToggle = ink, checked = ink, onChecked = page, play = ink, onPlay = page)
}

/** Newsreader at [weight], set for [opticalSize] like the board's serif, which sizes its optical axis to the text. */
@Composable
internal fun rememberDailySerif(weight: Int, opticalSize: Float): FontFamily = rememberVariableFont(R.font.daily_newsreader, weight, "opsz" to opticalSize)

/** Libre Franklin, the Times' sans for datelines and labels, at the weights the page uses. */
@Composable
internal fun rememberDailyFranklin(): VariableWeights = rememberVariableWeights(R.font.daily_libre_franklin, 500, 600, 700)
