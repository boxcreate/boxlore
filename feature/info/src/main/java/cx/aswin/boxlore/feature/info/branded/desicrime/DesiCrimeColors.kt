package cx.aswin.boxlore.feature.info.branded.desicrime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * The crime board's colours: a dark studio, the cork under a warm lamp, the show's saffron (the DESI
 * of its wordmark) as the accent, the cream of its CRIME, and the yellow of the sticky notes. Dark in
 * both themes.
 */
internal object DesiCrimeColors {
    val Page = Color(0xFF0E0A08)
    val Text = Color(0xFFF3E9D8)
    val Muted = Color(0xFFBEB3A3)
    val Saffron = Color(0xFFEE7112)
    val OnSaffron = Color(0xFF1A0B02)
    val Control = Color(0xFF2C2119)
    val OnControl = Color(0xFFF3E9D8)
    val Card = Color(0xFF1A1410)
    val CardControl = Color(0xFF33251B)
    val ChipLine = Color(0xFF4A3D32)
    val Navigation = Color(0xFF211913)
    val NavigationText = Color(0xFFEDE3D3)

    /** Ink on the sticky notes and the prints. */
    val NoteInk = Color(0xFF2A2016)
    val PrintInk = Color(0xFF2E2418)
    val CaseNumber = Color(0xFFB5520A)

    /** The sticky's "#212 · newest case": a red pen. */
    val RedPen = Color(0xFF98300A)
    val Underline = Color(0xFFC2330F)
    val NoteDetails = Color(0xFF4E3F2C)

    /** The card's frame round its strip of cork. */
    val Frame = Color(0xFF4A3020)
    val Cork = Color(0xFF3A2616)

    /** The sticky notes' yellow, a little deeper towards the foot. */
    val Sticky = Brush.verticalGradient(0f to Color(0xFFF2EC98), 0.55f to Color(0xFFE9E07F), 1f to Color(0xFFDCD06B))
    val StickyTop = Color(0x29786414)
    val Tape = Color(0x80F0E8CE)
    val Shadow = Color(0xFF0A0400)

    val Actions = BrandedActionColors(toggle = CardControl, onToggle = OnControl, checked = Saffron, onChecked = OnSaffron, play = Saffron, onPlay = OnSaffron)

    /** The © popup: 260dp wide, its caret near its right end, under the © after the hosts' names. */
    val Copyright = BrandedCopyrightStyle(
        icon = Muted,
        popup = Color(0xFF2C2119),
        title = Text,
        body = Color(0xFFE6DCCC),
        note = Muted,
        shadow = Color(0xB3060302),
        ring = Color(0x1AFFFFFF),
        popupWidth = 260.dp,
        caretCentre = 236.dp,
        gap = 2.dp,
        overhangTop = 3.dp,
        overhangBottom = 3.dp,
        iconSize = 16.8.dp,
    )
}

/** The hosts' handwriting on the board: Kalam, bold for notes and captions, regular for the small print. */
@Immutable
internal data class DesiCrimeFonts(val hand: FontFamily, val handBold: FontFamily)

@Composable
internal fun rememberDesiCrimeFonts(): DesiCrimeFonts = remember {
    DesiCrimeFonts(hand = FontFamily(Font(R.font.desicrime_kalam)), handBold = FontFamily(Font(R.font.desicrime_kalam_bold)))
}
