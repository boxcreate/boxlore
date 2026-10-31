package cx.aswin.boxlore.feature.info.branded.crimejunkie

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/** Fingerprint: Crime Junkie's ink splatter colours on a white or near-black page, from the approved design boards. */
internal object CrimeJunkieColors {
    /** The splatter's magenta, violet and blue, used for the fingerprint, the tag's dot and the ink in the flashlight. */
    val SplatMagenta = Color(0xFFC719D9)
    val SplatViolet = Color(0xFF6B14C8)
    val SplatBlue = Color(0xFF2A2FB0)

    /** The flashlight moment's dark room and the patch the beam lights, the same in both themes. */
    val Darkness = Color(0xFF140F1C)
    val Lit = Color(0xFF2C2340)
    val Beam = Color(0xFFFFF8E8)

    val Light = CrimeJunkiePalette(
        page = Color.White,
        ink = Color(0xFF140F1C),
        sub = Color(0xFF5E566B),
        control = Color(0xFFF0EBF8),
        checked = Color(0xFF5A12B5),
        onChecked = Color.White,
        chipLine = Color(0xFFCFC4E6),
        accent = Color(0xFF5A12B5),
        onAccent = Color.White,
        card = Color(0xFFF4F0FB),
        toggle = Color(0xFFE8E1F5),
        navigation = Color(0xFFF0EBF8),
        navigationText = Color(0xFF2B2338),
        tag = Color(0xFF140F1C),
        onTag = Color.White,
        mark = R.drawable.crimejunkie_mark,
        fingerprintAlpha = 0.42f,
        copyright = copyrightStyle(
            popup = Color.White,
            title = Color(0xFF140F1C),
            body = Color(0xFF3B3447),
            note = Color(0xFF6E6680),
            shadow = Color(0x33281450),
            ring = Color(0x1A140F1C),
            icon = Color(0xFF5E566B),
        ),
    )

    val Dark = CrimeJunkiePalette(
        page = Color(0xFF0E0B14),
        ink = Color(0xFFF4F1FA),
        sub = Color(0xFFB3AAC4),
        control = Color(0xFF221A30),
        checked = Color(0xFFC3A6FF),
        onChecked = Color(0xFF1A0F2E),
        chipLine = Color(0xFF4A3D63),
        accent = Color(0xFFC3A6FF),
        onAccent = Color(0xFF1A0F2E),
        card = Color(0xFF1A1424),
        toggle = Color(0xFF2A2139),
        navigation = Color(0xFF1E1729),
        navigationText = Color(0xFFDDD6EA),
        tag = Color(0xFFF4F1FA),
        onTag = Color(0xFF140F1C),
        mark = R.drawable.crimejunkie_mark_dark,
        fingerprintAlpha = 0.55f,
        copyright = copyrightStyle(
            popup = Color(0xFF241C32),
            title = Color(0xFFF4F1FA),
            body = Color(0xFFD9D1E8),
            note = Color(0xFFA89FB9),
            shadow = Color(0x99000000),
            ring = Color.Transparent,
            icon = Color(0xFFB3AAC4),
        ),
    )
}

/** The © ends the centred publisher line; its popup is 241dp wide with its caret under the ©. */
@Suppress("LongParameterList")
private fun copyrightStyle(popup: Color, title: Color, body: Color, note: Color, shadow: Color, ring: Color, icon: Color) = BrandedCopyrightStyle(
    icon = icon,
    popup = popup,
    title = title,
    body = body,
    note = note,
    shadow = shadow,
    ring = ring,
    popupWidth = 241.dp,
    caretCentre = 171.dp,
    gap = 6.dp,
    overhangTop = 8.dp,
    overhangBottom = 8.dp,
    iconSize = 15.dp,
)

/** The palette for the theme the page is drawn in. */
@Composable
@ReadOnlyComposable
internal fun crimeJunkiePalette(): CrimeJunkiePalette = if (LocalEffectiveDarkTheme.current) CrimeJunkieColors.Dark else CrimeJunkieColors.Light

internal data class CrimeJunkiePalette(
    val page: Color,
    /** Body text, and the Subscribe button before you follow. */
    val ink: Color,
    val sub: Color,
    val control: Color,
    /** Switched-on alerts and auto-download. */
    val checked: Color,
    val onChecked: Color,
    val chipLine: Color,
    /** Violet: the genre chip, the evidence tag's labels and eyelet, Play and the navbar's selected tab. */
    val accent: Color,
    val onAccent: Color,
    val card: Color,
    /** The card's own buttons. */
    val toggle: Color,
    val navigation: Color,
    val navigationText: Color,
    val tag: Color,
    val onTag: Color,
    /** The splatter mark drawn for this theme: black outlines on white, white outlines on the dark page. */
    @DrawableRes val mark: Int,
    val fingerprintAlpha: Float,
    val copyright: BrandedCopyrightStyle,
) {
    /** The evidence tag's buttons. */
    val actions: BrandedActionColors
        get() = BrandedActionColors(toggle = toggle, onToggle = ink, checked = checked, onChecked = onChecked, play = accent, onPlay = onAccent)
}
