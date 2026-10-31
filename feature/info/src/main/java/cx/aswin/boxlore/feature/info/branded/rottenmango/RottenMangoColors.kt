package cx.aswin.boxlore.feature.info.branded.rottenmango

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/** Rotten Mango's colours, from her red room in the show's official art. The identity is dark only. */
internal object RottenMangoColors {
    /** The room's dark floor, which the photo fades into. */
    val Page = Color(0xFF110405)
    val Ink = Color(0xFFF7EAD8)
    val Sub = Color(0xFFD2BCAD)

    /** The gold of her velvet couch: the genre chip, switched-on buttons, Play and the navbar's selected tab. */
    val Gold = Color(0xFFE6BB62)
    val OnGold = Color(0xFF2A1503)
    val Control = Color(0xFF2B1416)
    val ChipLine = Color(0xFF5A3230)
    val Navigation = Color(0xFF211012)
    val NavigationText = Color(0xFFE8D8CD)

    /** The episode cards in the list. */
    val Card = Color(0xFF1F0C0E)
    val TagRing = Color(0xFF8A6830)
    val Mango = Color(0xFFD42A26)

    /** The latest episode's frame: the labels and title typed on the dark inside. */
    val FrameLabel = Color(0xFFA69686)
    val FrameRed = Color(0xFFDC5F53)
    val FrameTitle = Color(0xFFEFE5D5)
    val PlateText = Color(0xFF17100A)

    /** The brass at the bottom of the plate's lettering, its darkest point behind the text. */
    val PlateBrass = Color(0xFF9C8656)
    val PlateDot = Color(0xFF2E2412)

    /** The button's old television: off, the screen's glow and the picture. */
    val TapeBlack = Color(0xFF060304)
    val TapeGlow = Color(0xFF2C0E10)
    val TapeDim = Color(0xFF0D0607)
    val TapeText = Color(0xFFF6EFE4)

    val Copyright = BrandedCopyrightStyle(
        icon = Sub,
        popup = Color(0xFF2A1416),
        title = Ink,
        body = Color(0xFFEAD9CB),
        note = Color(0xFFB9A294),
        shadow = Color(0xA6000000),
        ring = Color.Transparent,
        popupWidth = 257.dp,
        caretCentre = 188.dp,
        gap = 6.dp,
        overhangTop = 8.dp,
        overhangBottom = 8.dp,
        iconSize = 15.dp,
    )

    /** The latest episode's buttons, as on the episode cards. */
    val Actions = BrandedActionColors(toggle = Control, onToggle = Ink, checked = Gold, onChecked = OnGold, play = Gold, onPlay = OnGold)
}

/** Special Elite, the typewriter face the frame types its label, title and plate in. */
@Composable
internal fun rememberRottenMangoTypewriter(): FontFamily = remember { FontFamily(Font(R.font.branded_special_elite)) }

/** VT323, the old terminal face "BADA BING" and "BADA BOOM" play in. */
@Composable
internal fun rememberRottenMangoTape(): FontFamily = remember { FontFamily(Font(R.font.rottenmango_vt323)) }
