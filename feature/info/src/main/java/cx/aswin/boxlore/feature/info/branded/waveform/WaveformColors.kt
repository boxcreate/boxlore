package cx.aswin.boxlore.feature.info.branded.waveform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * Signal: a black page, lots of space and one red, from the approved design board. Waveform's
 * identity is dark only, so these colours hold in light mode too. Small text keeps 4.5:1 or better.
 */
internal object WaveformColors {
    val Page = Color(0xFF0A0A0A)
    val Text = Color(0xFFF5F5F5)

    /** The logo's slanted block and the progress marker. */
    val Red = Color(0xFFD61817)

    /** Red text, a step brighter than the block so small labels stay readable. */
    val RedText = Color(0xFFEC3F37)
    val Subtitle = Color(0xFFA3A3A3)
    val Description = Color(0xFF9A9A9A)
    val Publisher = Color(0xFF7C7C7C)
    val Muted = Color(0xFF8A8A8A)
    val ChipOutline = Color(0xFF2A2A2A)
    val ChipText = Color(0xFFE8E8E8)
    val Control = Color(0xFF161616)
    val Card = Color(0xFF121212)
    val CardBorder = Color(0xFF1E1E1E)
    val CardControl = Color(0xFF1C1C1C)
    val Placeholder = Color(0xFF1A1A1A)
    val TagBorder = Color(0xFF2E2E2E)
    val NavigationBorder = Color(0xFF222222)
    val NavigationText = Color(0xFFCFCFCF)
    val Track = Color(0xFF3A3A3A)
    val GlitchRed = Color(0xFFFF2020)
    val GlitchCyan = Color(0xFF18E0FF)

    /** The © ends the centred publisher line; its popup keeps 8dp from the right edge on a phone. */
    val Copyright = BrandedCopyrightStyle(
        icon = Publisher,
        popup = Color(0xFF1C1C1C),
        title = Text,
        body = Color(0xFFD4D4D4),
        note = Color(0xFF9A9A9A),
        shadow = Color(0x99000000),
        ring = Color(0xFF2A2A2A),
        popupWidth = 241.dp,
        caretCentre = 132.dp,
        gap = 6.dp,
        overhangTop = 8.dp,
        overhangBottom = 8.dp,
        iconSize = 14.dp,
    )
}

/** Barlow for the small spaced capitals, as in the show's own titles. */
@Composable
internal fun rememberWaveformBarlow(): FontFamily = remember {
    FontFamily(Font(R.font.waveform_barlow_semibold, FontWeight.SemiBold), Font(R.font.waveform_barlow_bold, FontWeight.Bold))
}
