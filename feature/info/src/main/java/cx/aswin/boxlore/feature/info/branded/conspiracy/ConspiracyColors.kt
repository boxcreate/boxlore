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

/** The case file's colours, from the approved design boards. Paper stays paper in both themes. */
internal object ConspiracyColors {
    val Mustard = Color(0xFFDEC100)
    val Ink = Color(0xFF2B2620)
    val PaperYellow = Color(0xFFF1D42A)
    val FromLabel = Color(0xFF4D4100)
    val Strip = Color(0xFFF5ECC2)
    val StripText = Color(0xFF231D02)
    val CardText = Color(0xFF3A3000)
    val Stamp = Color(0xFFA71F17)
    val Polaroid = Color(0xFFF4EFE0)
    val CopyrightOnPaper = Color(0xFF5A4D05)

    /** The file's buttons: a shade of ink on the mustard paper, ink once switched on, and ink Play. */
    val ButtonTint = Color(0xFFC5AB05)
    val Actions = BrandedActionColors(toggle = ButtonTint, onToggle = Ink, checked = Ink, onChecked = PaperYellow, play = Ink, onPlay = PaperYellow)

    val Dark = ConspiracyPalette(
        page = Color(0xFF12100A),
        text = Color(0xFFF3ECD0),
        textMuted = Color(0xFFBDB495),
        accent = Mustard,
        onAccent = Ink,
        accentOnPage = Color(0xFFE8C91C),
        chip = Color(0xFF26221A),
        chipText = Color(0xFFF3ECD0),
        genreChip = Mustard,
        onGenreChip = Ink,
        control = Color(0xFF29251A),
        card = Color(0xFF1D1A11),
        cardControl = Color(0xFF2E2A1F),
        placeholder = Color(0xFF24201A),
        bar = Color(0xFF1D1A11),
        divider = Color(0xFF2E2A1F),
        navigation = Color(0xFF27231A),
        navigationText = Color(0xFFE3DBBB),
        tagShadow = Color(0x73000000),
        fileShadow = Color(0x80000000),
        polaroidShadow = Color(0x59000000),
        cardShadow = Color(0x73000000),
        copyright = copyrightStyle(
            popup = Color(0xFF2E2A1F),
            title = Color(0xFFF3ECD0),
            body = Color(0xFFD9D0AC),
            note = Color(0xFFA69D7C),
            shadow = Color(0x8C000000),
            ring = Color.Transparent,
        ),
    )

    val Light = ConspiracyPalette(
        page = Color(0xFFFBF8EC),
        text = Ink,
        textMuted = Color(0xFF5C5440),
        accent = Ink,
        onAccent = PaperYellow,
        accentOnPage = Color(0xFF7A6500),
        chip = Color(0xFFEFE9D2),
        chipText = Ink,
        genreChip = Ink,
        onGenreChip = PaperYellow,
        control = Color(0xFFEFE9D2),
        card = Color(0xFFF1ECD6),
        cardControl = Color(0xFFE6DFC4),
        placeholder = Color(0xFFE9E3CC),
        bar = Color(0xFFF3EEDB),
        divider = Color(0xFFE2DBC2),
        navigation = Color(0xFFECE5CA),
        navigationText = Color(0xFF3D3624),
        tagShadow = Color(0x473C2D00),
        fileShadow = Color(0x38503C00),
        polaroidShadow = Color(0x4D000000),
        cardShadow = Color(0x33503C00),
        copyright = copyrightStyle(
            popup = Color.White,
            title = Ink,
            body = Color(0xFF3D3624),
            note = Color(0xFF6B6248),
            shadow = Color(0x403C2D00),
            ring = Color(0x143C2D00),
        ),
    )
}

/** The © popup: 241dp wide, its caret 194dp in under the ©; the button hangs 6dp below the "From" line. */
private fun copyrightStyle(popup: Color, title: Color, body: Color, note: Color, shadow: Color, ring: Color) = BrandedCopyrightStyle(
    icon = ConspiracyColors.CopyrightOnPaper,
    popup = popup,
    title = title,
    body = body,
    note = note,
    shadow = shadow,
    ring = ring,
    popupWidth = 241.dp,
    caretCentre = 194.dp,
    gap = 6.dp,
    overhangTop = 0.dp,
    overhangBottom = 6.dp,
)

/** The palette for the theme the page is drawn in. */
@Composable
@ReadOnlyComposable
internal fun conspiracyPalette(): ConspiracyPalette = if (LocalEffectiveDarkTheme.current) ConspiracyColors.Dark else ConspiracyColors.Light

/** The show's two typewriter faces: Special Elite for typed headings, Courier Prime for case notes. */
internal class ConspiracyFonts(val typewriter: FontFamily, val courier: FontFamily)

@Composable
internal fun rememberConspiracyFonts(): ConspiracyFonts = remember {
    ConspiracyFonts(
        typewriter = FontFamily(Font(R.font.branded_special_elite)),
        courier = FontFamily(Font(R.font.conspiracy_courier_prime, FontWeight.Normal), Font(R.font.conspiracy_courier_prime_bold, FontWeight.Bold)),
    )
}

internal data class ConspiracyPalette(
    val page: Color,
    val text: Color,
    val textMuted: Color,
    /** Filled controls: Subscribe, switched-on alerts and auto-download, the navbar's selected tab. */
    val accent: Color,
    val onAccent: Color,
    /** Accent text and icons on the page itself. */
    val accentOnPage: Color,
    val chip: Color,
    val chipText: Color,
    val genreChip: Color,
    val onGenreChip: Color,
    val control: Color,
    val card: Color,
    /** Buttons inside episode cards, a step off the card colour. */
    val cardControl: Color,
    /** Behind artwork while it loads. */
    val placeholder: Color,
    val bar: Color,
    val divider: Color,
    val navigation: Color,
    val navigationText: Color,
    val tagShadow: Color,
    val fileShadow: Color,
    val polaroidShadow: Color,
    val cardShadow: Color,
    val copyright: BrandedCopyrightStyle,
)
