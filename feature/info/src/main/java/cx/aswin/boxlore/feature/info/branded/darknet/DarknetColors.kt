package cx.aswin.boxlore.feature.info.branded.darknet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle
import cx.aswin.boxlore.feature.info.branded.rememberVariableFont

/** The show's ink: black, one red, and the paper white of its illustrations. Dark in both themes. */
internal object DarknetColors {
    val Page = Color(0xFF0B0A0A)
    val Header = Color(0xFF040303)
    val Text = Color(0xFFF2F0EE)
    val Muted = Color(0xFFB5AFAB)
    val Red = Color(0xFFE0201B)
    val OnRed = Color.White

    /** Red text and icons on the page: the chips' icons, release times, the card's "//". */
    val RedText = Color(0xFFFF4A3D)
    val Slash = Color(0xFFFF3B30)
    val Ink = Color(0xFFE50706)
    val Control = Color(0xFF1E1B1C)
    val OnControl = Color(0xFFEDE8E5)
    val Card = Color(0xFF141213)

    /** Behind artwork while it loads. */
    val Placeholder = Color(0xFF1A1718)
    val CardControl = Color(0xFF231F20)
    val ChipLine = Color(0xFF3B3536)
    val Navigation = Color(0xFF171415)
    val NavigationText = Color(0xFFE5DFDB)
    val Flyer = Color(0xFFF2F0EE)
    val Tape = Color(0xC7E1D7BE)

    val Actions = BrandedActionColors(toggle = Control, onToggle = OnControl, checked = Red, onChecked = OnRed, play = Red, onPlay = OnRed)

    /** The © popup: 260dp wide, its caret in the middle, under the © after the host's name. */
    val Copyright = BrandedCopyrightStyle(
        icon = Muted,
        popup = Color(0xFF1E1B1C),
        title = Text,
        body = Color(0xFFDCD6D2),
        note = Muted,
        shadow = Color(0xB3000000),
        ring = Color(0x1AFFFFFF),
        popupWidth = 260.dp,
        caretCentre = 130.dp,
        gap = 2.dp,
        overhangTop = 2.dp,
        overhangBottom = 2.dp,
        iconSize = 15.dp,
    )
}

/** Anton for the card's headline, as the show's flyers set it; Roboto Flex at 900 and 700 for its labels. */
@Immutable
internal class DarknetFonts(val anton: FontFamily, val black: FontFamily, val bold: FontFamily)

@Composable
internal fun rememberDarknetFonts(): DarknetFonts {
    val black = rememberVariableFont(DesignR.font.robotoflex_variable, 900)
    val bold = rememberVariableFont(DesignR.font.robotoflex_variable, 700)
    return remember(black, bold) { DarknetFonts(FontFamily(Font(R.font.branded_anton)), black, bold) }
}
