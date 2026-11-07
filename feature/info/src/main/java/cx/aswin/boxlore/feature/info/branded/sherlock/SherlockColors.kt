package cx.aswin.boxlore.feature.info.branded.sherlock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * The Strand's colours: a dark study lit by a desk lamp, the issue's pale blue cover (the page's
 * accent), its cream paper and the engraver's blue-black ink. Dark in both themes.
 */
internal object SherlockColors {
    val Page = Color(0xFF150C0C)
    val Text = Color(0xFFF1E9DC)
    val Muted = Color(0xFFC4B8AB)

    /** The Strand cover's pale blue: Subscribe, the genre chip, Play. */
    val Blue = Color(0xFFBFD3D8)
    val OnBlue = Color(0xFF13202A)
    val Control = Color(0xFF2B1F1D)
    val OnControl = Color(0xFFF1E9DC)
    val Card = Color(0xFF211716)

    /** The latest episode's panel, behind its page. */
    val Panel = Color(0xFF1F1816)
    val CardControl = Color(0xFF33251F)
    val ChipLine = Color(0xFF4D3F3A)
    val Navigation = Color(0xFF261B1A)
    val NavigationText = Color(0xFFECE2D6)

    /** The engraving's blue-black ink: the plate's rule, the drop cap. */
    val Ink = Color(0xFF18212E)

    /** Printed text on the page: titles, the body, and the brown of the italic lines. */
    val Print = Color(0xFF1E1712)
    val Body = Color(0xFF2E241C)
    val Italic = Color(0xFF4A3A2C)
    val Rule = Color(0xFF6A5848)
    val DropCapLetter = Color(0xFFF6EEDC)
    val DropCapOrnament = Color(0xFFE9DEC4)

    /** The page's cream, from its gutter to its open side. */
    val PaperGutter = Color(0xFFD9CCB0)
    val PaperEdge = Color(0xFFEEE4CE)
    val Paper = Color(0xFFF5EDDB)
    val PaperOuter = Color(0xFFF1E7D2)

    /** Under the issue and the card's page. */
    val Shadow = Color(0x73000000)

    val Actions = BrandedActionColors(toggle = Control, onToggle = OnControl, checked = Blue, onChecked = OnBlue, play = Blue, onPlay = OnBlue)

    /** The © popup: 260dp wide, its caret near its right end, under the © at the desk's right edge. */
    val Copyright = BrandedCopyrightStyle(
        icon = Muted,
        popup = Color(0xFF2B1F1D),
        title = Text,
        body = Color(0xFFE4D9CB),
        note = Muted,
        shadow = Color(0xB3060303),
        ring = Color(0x1AFFFFFF),
        popupWidth = 260.dp,
        caretCentre = 236.dp,
        gap = 2.dp,
        overhangTop = 3.dp,
        overhangBottom = 3.dp,
        iconSize = 16.8.dp,
    )
}

/** The Strand's period type: IM Fell English, its italic, and its small capitals. */
@Immutable
internal data class StrandFonts(val roman: FontFamily, val italic: FontFamily, val smallCaps: FontFamily)

@Composable
internal fun rememberStrandFonts(): StrandFonts = remember {
    StrandFonts(
        roman = FontFamily(Font(R.font.sherlock_fell)),
        italic = FontFamily(Font(R.font.sherlock_fell_italic, style = FontStyle.Italic)),
        smallCaps = FontFamily(Font(R.font.sherlock_fell_sc)),
    )
}
