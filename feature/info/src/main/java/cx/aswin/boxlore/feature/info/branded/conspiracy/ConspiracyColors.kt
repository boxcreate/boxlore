package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * The evidence board's colours, from the approved design boards: white paper by day, black by night,
 * the show's yellow only as an accent. The pinned scraps keep their paper colours in both themes.
 */
internal object ConspiracyColors {
    /** The show's yellow: Subscribe, the genre chip, switched-on alerts and the navbar's selected tab. */
    val Yellow = Color(0xFFE8C91C)
    val OnYellow = Color(0xFF231D02)
    val Ink = Color(0xFF2B2620)
    val PaperYellow = Color(0xFFF1D42A)
    val Stamp = Color(0xFFA71F17)

    /** The newspaper clipping, the typed index card and the sticky note pinned to the board. */
    val Clipping = Color(0xFFF1EDDE)
    val ClippingInk = Color(0xFF1F1C17)
    val IndexCard = Color(0xFFFBF8F1)
    val IndexCardRule = Color(0xFFC9483E)
    val IndexCardLine = Color(0x40466EAA)
    val Sticky = PaperYellow

    /** The red marker circled round the newest clue. */
    val Marker = Color(0xFFD32A1E)

    /** The latest episode's manila folder: grain, buttons, notes and the typed ink. */
    val Manila = Color(0xFFD3C39C)
    val ManilaToggle = Color(0xFFD3C49E)
    val ManilaBody = Color(0xFF4A4234)
    val Actions = BrandedActionColors(toggle = ManilaToggle, onToggle = Ink, checked = Ink, onChecked = PaperYellow, play = Ink, onPlay = PaperYellow)

    val Dark = ConspiracyPalette(
        page = Color(0xFF131210),
        text = Color(0xFFF0ECE2),
        textMuted = Color(0xFFB3AC9E),
        accentOnPage = Yellow,
        chipLine = Color(0xFF3C3832),
        control = Color(0xFF26231F),
        onControl = Color(0xFFEDE7DA),
        card = Color(0xFF1E1C19),
        cardControl = Color(0xFF2B2824),
        placeholder = Color(0xFF221F1C),
        bar = Color(0xFF1E1C19),
        divider = Color(0xFF2B2824),
        navigation = Color(0xFF1E1C19),
        navigationText = Color(0xFFE3DDD0),
        boardTopFade = Color(0xB30A0908),
        yarnShadow = Color(0x99000000),
        cardShadow = Color(0x8C000000),
        copyright = copyrightStyle(
            icon = Color(0xFFB3AC9E),
            popup = Color(0xFF26231F),
            title = Color(0xFFF0ECE2),
            body = Color(0xFFD6D0C4),
            note = Color(0xFFA9A295),
            shadow = Color(0x8C000000),
            ring = Color.Transparent,
        ),
    )

    val Light = ConspiracyPalette(
        page = Color(0xFFF3F1EC),
        text = Color(0xFF1E1B16),
        textMuted = Color(0xFF57524A),
        accentOnPage = Color(0xFF7A6500),
        chipLine = Color(0xFFCFC8BA),
        control = Color(0xFFE6E2D9),
        onControl = Ink,
        card = Color(0xFFE9E5DC),
        cardControl = Color(0xFFDCD7CC),
        placeholder = Color(0xFFE2DED4),
        bar = Color(0xFFE9E5DC),
        divider = Color(0xFFDCD7CC),
        navigation = Color(0xFFE9E5DC),
        navigationText = Ink,
        boardTopFade = Color(0xB3FFFFFF),
        yarnShadow = Color(0x52281409),
        cardShadow = Color(0x38000000),
        copyright = copyrightStyle(
            icon = Color(0xFF57524A),
            popup = Color.White,
            title = Color(0xFF1E1B16),
            body = Color(0xFF3D3832),
            note = Color(0xFF6B655B),
            shadow = Color(0x40281E0A),
            ring = Color(0x14281E0A),
        ),
    )
}

/** The © popup: 241dp wide, its caret 194dp in, under the © at the end of the publisher line. */
private fun copyrightStyle(icon: Color, popup: Color, title: Color, body: Color, note: Color, shadow: Color, ring: Color) = BrandedCopyrightStyle(
    icon = icon,
    popup = popup,
    title = title,
    body = body,
    note = note,
    shadow = shadow,
    ring = ring,
    popupWidth = 241.dp,
    caretCentre = 194.dp,
    gap = 2.dp,
    overhangTop = 2.dp,
    overhangBottom = 2.dp,
    iconSize = 15.dp,
)

/** The palette for the theme the page is drawn in. */
@Composable
@ReadOnlyComposable
internal fun conspiracyPalette(): ConspiracyPalette = if (LocalEffectiveDarkTheme.current) ConspiracyColors.Dark else ConspiracyColors.Light

/**
 * The board's faces: Special Elite for anything typed, Courier Prime for the folder's labels and
 * notes, Old Standard for the newspaper clipping and Caveat for the handwritten sticky note.
 */
internal class ConspiracyFonts(val typewriter: FontFamily, val courier: FontFamily, val newsprint: FontFamily, val handwriting: FontFamily)

@Composable
internal fun rememberConspiracyFonts(): ConspiracyFonts = remember {
    ConspiracyFonts(
        typewriter = FontFamily(Font(R.font.branded_special_elite)),
        courier = FontFamily(Font(R.font.conspiracy_courier_prime, FontWeight.Normal), Font(R.font.conspiracy_courier_prime_bold, FontWeight.Bold)),
        newsprint = FontFamily(Font(R.font.conspiracy_old_standard_bold, FontWeight.Bold)),
        handwriting = FontFamily(Font(R.font.conspiracy_caveat_bold, FontWeight.Bold)),
    )
}

internal data class ConspiracyPalette(
    val page: Color,
    val text: Color,
    val textMuted: Color,
    /** Accent text and icons on the page itself: the chips' icons and each card's release time. */
    val accentOnPage: Color,
    val chipLine: Color,
    /** Sort, search, Subscribed and the switched-off alerts. */
    val control: Color,
    val onControl: Color,
    val card: Color,
    /** Buttons inside episode cards, a step off the card colour. */
    val cardControl: Color,
    /** Behind artwork while it loads. */
    val placeholder: Color,
    val bar: Color,
    val divider: Color,
    val navigation: Color,
    val navigationText: Color,
    /** The board darkens (or brightens) towards the top bar, so its icons stay clear. */
    val boardTopFade: Color,
    val yarnShadow: Color,
    val cardShadow: Color,
    val copyright: BrandedCopyrightStyle,
)
