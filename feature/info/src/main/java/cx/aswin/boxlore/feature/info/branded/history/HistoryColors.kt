package cx.aswin.boxlore.feature.info.branded.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * The gallery's colours, from the approved design boards: the show's claret velvet as the wall in
 * both themes, a claret night page and an ivory day page below it.
 */
internal object HistoryColors {
    val Ivory = Color(0xFFF2E8E1)
    val Claret = Color(0xFF5C0F2C)

    /** The top bar's icons and the © over the velvet wall. */
    val OnWall = Color(0xFFF6ECE6)

    /** The wall before its picture is ready, and an empty frame before its picture arrives. */
    val WallInTheDark = Color(0xFF2A0812)
    val EmptyCanvas = Color(0xFF1E120C)

    /** The wall's bottom edge on the day page. */
    val WallEdge = Color(0xFF5C0F2C)

    /** The bronze mouldings, their aged mounts and the latest episode's nameplate. */
    val Bronze = listOf(
        0f to Color(0xFF4A3417),
        0.16f to Color(0xFF9A7A45),
        0.30f to Color(0xFF5E4320),
        0.46f to Color(0xFFB8955C),
        0.60f to Color(0xFF6A4C24),
        0.78f to Color(0xFFA2814C),
        1f to Color(0xFF3E2B12),
    )
    val Mount = Color(0xFFE8DCC2)
    val MountLine = Color(0xFFA88850)
    val PlateInk = Color(0xFF2E1F0B)

    /** Text on the piece of the wall that holds the latest episode. */
    val CardKicker = Color(0xFFF2C9D6)
    val CardTitle = Color(0xFFFFF6F2)
    val CardText = Color(0xFFF1DCE3)
    val CardToggle = Color(0xFF2A0E19)
    val OnCardToggle = Color(0xFFF6ECE6)

    val Dark = HistoryPalette(
        page = Color(0xFF1C0911),
        text = Color(0xFFF6ECE6),
        textMuted = Color(0xFFD9C2C9),
        rose = Color(0xFFEBA4BA),
        primary = Ivory,
        onPrimary = Claret,
        control = Color(0xFF3A1424),
        onControl = Color(0xFFF1DEE4),
        card = Color(0xFF2A0E19),
        cardControl = Color(0xFF3A1424),
        placeholder = Color(0xFF30111D),
        chipLine = Color(0xFF62283B),
        navigation = Color(0xFF2A0E19),
        navigationText = Color(0xFFEAD7DD),
        copyright = copyrightStyle(popup = Color(0xFF3A1424), title = Color(0xFFF6ECE6), body = Color(0xFFE6D4DA), note = Color(0xFFD9C2C9)),
    )

    val Light = HistoryPalette(
        page = Color(0xFFF6F1ED),
        text = Color(0xFF2A0E17),
        textMuted = Color(0xFF5E4651),
        rose = Color(0xFF8A1E45),
        primary = Color(0xFF7E1A3E),
        onPrimary = Color(0xFFFFF6F2),
        control = Color(0xFFEBDFDB),
        onControl = Color(0xFF4A1226),
        card = Color(0xFFECE2DD),
        cardControl = Color(0xFFE0D2CD),
        placeholder = Color(0xFFE6DAD5),
        chipLine = Color(0xFFCDB7BF),
        navigation = Color(0xFFECE2DD),
        navigationText = Color(0xFF4A1226),
        copyright = copyrightStyle(popup = Color(0xFF2A0E17), title = Color(0xFFF6ECE6), body = Color(0xFFE6D4DA), note = Color(0xFFD9C2C9)),
    )
}

/** The © popup, 260dp wide with its caret in the middle, under the © on the wall. */
private fun copyrightStyle(popup: Color, title: Color, body: Color, note: Color) = BrandedCopyrightStyle(
    icon = HistoryColors.OnWall,
    popup = popup,
    title = title,
    body = body,
    note = note,
    shadow = Color(0x991E040A),
    ring = Color.Transparent,
    popupWidth = 260.dp,
    caretCentre = 130.dp,
    gap = 2.dp,
    overhangTop = 2.dp,
    overhangBottom = 2.dp,
    iconSize = 15.dp,
)

@Composable
@ReadOnlyComposable
internal fun historyPalette(): HistoryPalette = if (LocalEffectiveDarkTheme.current) HistoryColors.Dark else HistoryColors.Light

/** The gallery's faces: Cinzel's carved capitals for labels and the nameplate, Cormorant Garamond for titles. */
internal class HistoryFonts(val cinzel: FontFamily, val cormorant: FontFamily)

@Composable
internal fun rememberHistoryFonts(): HistoryFonts = remember {
    HistoryFonts(
        cinzel = FontFamily(Font(R.font.trih_cinzel_bold, FontWeight.Bold)),
        cormorant = FontFamily(
            Font(R.font.trih_cormorant_bold, FontWeight.Bold),
            Font(R.font.trih_cormorant_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
        ),
    )
}

internal data class HistoryPalette(
    val page: Color,
    val text: Color,
    val textMuted: Color,
    /** Accent text and icons on the page: the chips' icons and each card's release time. */
    val rose: Color,
    /** Subscribe, the genre chip, switched-on alerts and Play. */
    val primary: Color,
    val onPrimary: Color,
    val control: Color,
    val onControl: Color,
    val card: Color,
    val cardControl: Color,
    val placeholder: Color,
    val chipLine: Color,
    val navigation: Color,
    val navigationText: Color,
    val copyright: BrandedCopyrightStyle,
)
