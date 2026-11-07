package cx.aswin.boxlore.feature.info.branded.serial

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable

/**
 * Keyframes as the design boards run them: each stretch between two keyframes eased on its own.
 * [points] are (share of the animation, value) pairs from 0 to 1.
 */
internal class SerialKeys(private val easing: Easing, private vararg val points: Pair<Float, Float>) {
    fun at(p: Float): Float {
        if (p <= points.first().first) return points.first().second
        for (i in 1 until points.size) {
            val (end, to) = points[i]
            if (p <= end) {
                val (start, from) = points[i - 1]
                return from + (to - from) * easing.transform((p - start) / (end - start))
            }
        }
        return points.last().second
    }
}

/** Where a card is at one moment: moved, turned (degrees), scaled and faded. */
@Immutable
internal data class SerialCardPose(val x: Float, val y: Float, val rotation: Float, val scale: Float, val alpha: Float) {
    companion object {
        val Rest = SerialCardPose(0f, 0f, 0f, 1f, 1f)
        val Hidden = SerialCardPose(0f, 0f, 0f, 1f, 0f)
    }
}

/**
 * The deal on opening, in milliseconds from its start. The oldest card lands first and each season
 * drops onto the one before with a small bump, 150ms apart, the newest last; then Serial's own card
 * slides half out of the deck behind it and settles at a tilt.
 */
internal object SerialDeal {
    const val CARD_MILLIS = 380f
    const val GAP_MILLIS = 150f
    const val FIRST_AT = 150f
    const val PEEK_AT = 1_450f
    const val PEEK_MILLIS = 520f
    const val END = PEEK_AT + PEEK_MILLIS

    /** The card's own tilt once the deal is over, about a point a fifth across and four fifths down. */
    const val PEEK_TILT = 9f

    private val Ease = CubicBezierEasing(0.25f, 0.8f, 0.3f, 1f)
    private val CardFade = SerialKeys(Ease, 0f to 0f, 0.35f to 1f, 1f to 1f)
    private val CardX = SerialKeys(Ease, 0f to 14f, 0.72f to 0f, 0.86f to 0f, 1f to 0f)
    private val CardY = SerialKeys(Ease, 0f to -46f, 0.72f to 2f, 0.86f to -1f, 1f to 0f)
    private val CardTurn = SerialKeys(Ease, 0f to 4f, 0.72f to -0.6f, 0.86f to 0f, 1f to 0f)
    private val CardScale = SerialKeys(Ease, 0f to 1.05f, 0.72f to 0.985f, 0.86f to 1.004f, 1f to 1f)
    private val PeekFade = SerialKeys(Ease, 0f to 0f, 0.3f to 1f, 1f to 1f)
    private val PeekX = SerialKeys(Ease, 0f to -50f, 0.75f to 3f, 1f to 0f)
    private val PeekTurn = SerialKeys(Ease, 0f to 0f, 0.75f to 10f, 1f to PEEK_TILT)

    /** When the card [depth] places down from the top lands: the deepest first, the top card last. */
    fun cardAt(depth: Int, cards: Int): Float = FIRST_AT + (cards - 1 - depth) * GAP_MILLIS

    /** The card [depth] places down from the top, [t] milliseconds into the deal. */
    fun card(depth: Int, cards: Int, t: Float): SerialCardPose {
        val p = (t - cardAt(depth, cards)) / CARD_MILLIS
        if (p <= 0f) return SerialCardPose.Hidden
        if (p >= 1f) return SerialCardPose.Rest
        return SerialCardPose(CardX.at(p), CardY.at(p), CardTurn.at(p), CardScale.at(p), CardFade.at(p))
    }

    /** Serial's own card, [t] milliseconds into the deal. */
    fun peek(t: Float): SerialCardPose {
        val p = (t - PEEK_AT) / PEEK_MILLIS
        if (p <= 0f) return SerialCardPose.Hidden
        if (p >= 1f) return SerialCardPose(0f, 0f, PEEK_TILT, 1f, 1f)
        return SerialCardPose(PeekX.at(p), 0f, PeekTurn.at(p), 1f, PeekFade.at(p))
    }
}
