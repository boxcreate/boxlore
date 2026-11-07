package cx.aswin.boxlore.feature.info.branded.serial

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * Serial's colours: the plum of its cover art, the logo's black cards with white rims, the red of
 * Serial Productions' mark and the cream of the logo's S. Dark in both themes.
 */
internal object SerialColors {
    val Page = Color(0xFF170911)

    /** The cover art's plum, behind the deck. */
    val Plum = Color(0xFF40172F)
    val Text = Color(0xFFF6EEF2)
    val Muted = Color(0xFFCDB9C4)
    val Red = Color(0xFFEB1C24)
    val OnRed = Color(0xFF080205)

    /** Red text and icons on the page: the chips' icons, the card's season line, release times. */
    val RedText = Color(0xFFFF5A61)
    val Control = Color(0xFF2E1424)
    val OnControl = Color(0xFFF1E4EA)
    val Card = Color(0xFF241019)

    /** Behind artwork while it loads. */
    val Placeholder = Color(0xFF2E1424)
    val CardControl = Color(0xFF341A2A)
    val ChipLine = Color(0xFF4C2A3C)
    val Navigation = Color(0xFF241019)
    val NavigationText = Color(0xFFEADCE3)

    /** The logo's cards: black with a white rim. */
    val CardBlack = Color(0xFF0A0507)
    val Rim = Color.White

    /** The logo's S: cream, edged in red. */
    val Cream = Color(0xFFF2EBC1)
    val SRed = Color(0xFFFE041F)

    val Actions = BrandedActionColors(toggle = Control, onToggle = OnControl, checked = Red, onChecked = OnRed, play = Red, onPlay = OnRed)

    /** The © popup: 260dp wide, its caret in the middle, under the © at the end of the credits. */
    val Copyright = BrandedCopyrightStyle(
        icon = Muted,
        popup = Color(0xFF2E1424),
        title = Text,
        body = Color(0xFFE6D6DE),
        note = Muted,
        shadow = Color(0xB3080205),
        ring = Color(0x1AFFFFFF),
        popupWidth = 260.dp,
        caretCentre = 130.dp,
        gap = 2.dp,
        overhangTop = 3.dp,
        overhangBottom = 3.dp,
        iconSize = 16.8.dp,
    )
}
