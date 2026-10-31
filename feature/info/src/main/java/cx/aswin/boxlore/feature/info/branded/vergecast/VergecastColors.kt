package cx.aswin.boxlore.feature.info.branded.vergecast

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/** The Verge's colours, from the approved design boards. */
internal object VergecastColors {
    val Ultraviolet = Color(0xFF5100FD)
    val DeepUltraviolet = Color(0xFF3200A6)
    val Lime = Color(0xFFDEFF15)
    val Ink = Color(0xFF0E0E10)
    val CardText = Color(0xFFE8E0FF)

    val Dark = VergecastPalette(
        page = Ink,
        text = Color.White,
        textMuted = Color(0xFFB6B3C4),
        accent = Lime,
        onAccent = Ink,
        accentOnPage = Lime,
        chip = Color(0xFF1F1E26),
        chipText = Color(0xFFECEBF2),
        control = Color(0xFF26252D),
        card = Color(0xFF18171D),
        cardControl = Color(0xFF26252D),
        placeholder = Color(0xFF232229),
        bar = Color(0xFF18171D),
        divider = Color(0xFF2B2A33),
        navigation = Color(0xFF24232B),
        navigationText = Color(0xFFDDDBE6),
        copyright = copyrightStyle(
            icon = Color(0xFF85829A),
            popup = Color(0xFF2B2A33),
            title = Color(0xFFECEBF2),
            body = Color(0xFFC4C1D2),
            note = Color(0xFF9A97AA),
            shadow = Color(0x80000000),
            ring = Color.Transparent,
        ),
        wordmark = R.drawable.vergecast_wordmark,
        publisherLogo = R.drawable.vergecast_verge_logo,
        sticker = R.drawable.vergecast_spotlight_sticker,
    )

    val Light = VergecastPalette(
        page = Color.White,
        text = Color(0xFF121116),
        textMuted = Color(0xFF55536A),
        accent = Ultraviolet,
        onAccent = Color.White,
        accentOnPage = Ultraviolet,
        chip = Color(0xFFF0EFF5),
        chipText = Color(0xFF121116),
        control = Color(0xFFF0EFF5),
        card = Color(0xFFF0EFF5),
        cardControl = Color(0xFFE4E2EC),
        placeholder = Color(0xFFE8E6EF),
        bar = Color(0xFFF5F4F9),
        divider = Color(0xFFE2E0EA),
        navigation = Color(0xFFEBEAF1),
        navigationText = Color(0xFF3B3946),
        copyright = copyrightStyle(
            icon = Color(0xFF6B6880),
            popup = Color.White,
            title = Color(0xFF121116),
            body = Color(0xFF3B3946),
            note = Color(0xFF6B6880),
            shadow = Color(0x38140A3C),
            ring = Color(0x0F140A3C),
        ),
        wordmark = R.drawable.vergecast_wordmark_light,
        publisherLogo = R.drawable.vergecast_verge_logo_light,
        sticker = R.drawable.vergecast_spotlight_sticker_light,
    )
}

/** The © popup: 233dp wide, its caret 111dp in under the ©, the button centred on the logo row. */
@Suppress("LongParameterList")
private fun copyrightStyle(icon: Color, popup: Color, title: Color, body: Color, note: Color, shadow: Color, ring: Color) = BrandedCopyrightStyle(
    icon = icon,
    popup = popup,
    title = title,
    body = body,
    note = note,
    shadow = shadow,
    ring = ring,
    popupWidth = 233.dp,
    caretCentre = 111.dp,
    gap = 4.5.dp,
    overhangTop = 6.dp,
    overhangBottom = 6.dp,
)

/** The palette for the theme the page is drawn in. */
@Composable
@ReadOnlyComposable
internal fun vergecastPalette(): VergecastPalette = if (LocalEffectiveDarkTheme.current) VergecastColors.Dark else VergecastColors.Light

internal data class VergecastPalette(
    val page: Color,
    val text: Color,
    val textMuted: Color,
    /** Filled controls: Subscribe, switched-on alerts and auto-download, the navbar's selected tab. */
    val accent: Color,
    val onAccent: Color,
    /** Accent text and icons on the page itself; lime fades on white, so light mode uses ultraviolet. */
    val accentOnPage: Color,
    val chip: Color,
    val chipText: Color,
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
    val copyright: BrandedCopyrightStyle,
    @DrawableRes val wordmark: Int,
    @DrawableRes val publisherLogo: Int,
    @DrawableRes val sticker: Int,
)
