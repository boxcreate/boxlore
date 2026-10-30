package cx.aswin.boxlore.feature.history.story

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import cx.aswin.boxlore.core.designsystem.echo.EchoAccents
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import kotlin.math.abs

/**
 * The monthly story's colours (boards A7–A12). They were drawn for boxlore's own theme; on another
 * theme each keeps its tone and moves to the theme's hue, with its colourfulness scaled to the theme's
 * (the primary family follows the theme's primary, the green family its tertiary). On boxlore's own
 * colours they are exactly the boards'.
 */
@Immutable
data class StoryPalette(
    /** Hello and wrap-up cards. */
    val container: Color,
    val onContainer: Color,
    /** The time card. */
    val vivid: Color,
    /** The night listening-type card, and text on light bubbles. */
    val deep: Color,
    val blob: Color,
    /** The records card. */
    val primary: Color,
    val onPrimary: Color,
    val podium: Color,
    val shareBlob: Color,
    val clockRing: Color,
    val clockWedge: Color,
    /** Text on the time card's white bubble. */
    val onWhite: Color,
    /** The top-show card. */
    val green: Color,
    val onGreen: Color,
    val greenBlob: Color,
    val greenInk: Color,
    val mint: Color,
    /** Echo's accents where its own indigo would clash: deep purple on the time card, seafoam on green. */
    val echoDeep: Color,
    val echoSea: Color,
    /** Whether the theme is boxlore's own hue, so Echo keeps its indigo. */
    val ownHue: Boolean,
) {
    /** Echo's own colours where its indigo suits the card; on another theme hue, the card's primary. */
    val echoOwn: EchoAccents get() = if (ownHue) EchoAccents.Original else EchoAccents.Custom(vivid.toArgb(), primary.toArgb())
    val echoOnVivid: EchoAccents get() = EchoAccents.Custom(echoDeep.toArgb(), primary.toArgb())
    val echoOnGreen: EchoAccents get() = EchoAccents.Custom(echoSea.toArgb(), echoSea.toArgb())

    companion object {
        /** The boards' colours, for boxlore's own theme in [dark] or light. */
        fun forTheme(primary: Int, tertiary: Int, dark: Boolean): StoryPalette {
            val primaryTurn = StoryTurn.of(if (dark) DarkPrimary else LightPrimary, primary)
            val tertiaryTurn = StoryTurn.of(if (dark) DarkTertiary else LightTertiary, tertiary)
            val p = primaryTurn::apply
            val t = tertiaryTurn::apply
            return StoryPalette(
                container = p(0xFF3F347B),
                onContainer = p(0xFFECE7FF),
                vivid = p(0xFF5145D8),
                deep = p(0xFF261D58),
                blob = p(0xFF4D4194),
                primary = p(0xFF9C8CFF),
                onPrimary = p(0xFF21164B),
                podium = p(0xFFB3A7FF),
                shareBlob = p(0xFFD6CEFF),
                clockRing = p(0xFF6A5CC4),
                clockWedge = p(0xFF544A99),
                onWhite = p(0xFF3A2FB0),
                green = t(0xFF274C41),
                onGreen = t(0xFFD0EFE3),
                greenBlob = t(0xFF33604F),
                greenInk = t(0xFF123C32),
                mint = t(0xFF89C6B5),
                echoDeep = p(0xFF2E2490),
                echoSea = t(0xFF36B28F),
                ownHue = primaryTurn.isOwnHue,
            )
        }

        // boxlore's own primary and tertiary, as the boards use them.
        private const val DarkPrimary = 0xFF9C8CFF
        private const val LightPrimary = 0xFF5145D8
        private const val DarkTertiary = 0xFF89C6B5
        private const val LightTertiary = 0xFF397B6D
    }
}

@Composable
internal fun rememberStoryPalette(): StoryPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEffectiveDarkTheme.current
    val primary = scheme.primary.toArgb()
    val tertiary = scheme.tertiary.toArgb()
    return remember(primary, tertiary, dark) { StoryPalette.forTheme(primary, tertiary, dark) }
}

/** Moves a board colour from boxlore's hue to a theme's: the hue turns, the colourfulness scales, the tone stays. */
internal class StoryTurn private constructor(private val hueShift: Double, private val chromaScale: Double) {
    /** Within a few degrees of boxlore's own hue, Echo keeps its indigo. */
    val isOwnHue: Boolean get() = abs(hueShift) < OWN_HUE_DEGREES

    private val unchanged = abs(hueShift) < SAME_DEGREES && abs(chromaScale - 1.0) < SAME_CHROMA

    fun apply(boardColor: Long): Color {
        if (unchanged) return Color(boardColor)
        val hct = Hct.fromInt(boardColor.toInt())
        return Color(Hct.from((hct.hue + hueShift).mod(FULL_TURN), hct.chroma * chromaScale, hct.tone).toInt())
    }

    companion object {
        private const val FULL_TURN = 360.0
        private const val SAME_DEGREES = 0.5
        private const val SAME_CHROMA = 0.02
        private const val OWN_HUE_DEGREES = 12.0
        private const val MAX_CHROMA_SCALE = 1.6

        fun of(boardColor: Long, themeColor: Int): StoryTurn {
            val board = Hct.fromInt(boardColor.toInt())
            val theme = Hct.fromInt(themeColor)
            val shift = ((theme.hue - board.hue + FULL_TURN * 1.5).mod(FULL_TURN)) - FULL_TURN / 2
            val scale = if (board.chroma <= 0.0) 1.0 else (theme.chroma / board.chroma).coerceIn(0.0, MAX_CHROMA_SCALE)
            return StoryTurn(shift, scale)
        }
    }
}
