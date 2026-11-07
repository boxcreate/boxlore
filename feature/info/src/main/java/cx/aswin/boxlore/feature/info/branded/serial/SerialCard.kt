package cx.aswin.boxlore.feature.info.branded.serial

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min

/**
 * Serial's own card from its logo, drawn rather than pictured: black with a white rim, the cream S
 * edged in red, and a cream copy of the S just below it. The S is traced from the show's art, in a
 * card 434 by 524; it is fitted into a card of any size, centred, at its own shape.
 */
internal object SerialMark {
    const val WIDTH = 434f
    const val HEIGHT = 524f

    /** The red edge's width, inside the S's outline. */
    const val EDGE = 9.97f

    /** Where the cream copy sits, from the S. */
    val Copy = Offset(-1f, 4f)

    /** The S's outline. */
    const val OUTLINE =
        "M203.4 160.4L206.0 160.0L217.9 160.1L222.1 160.9L229.9 161.1L234.1 161.9L238.8 162.2L257.8 166.2L273.9 171.1" +
        "L296.3 180.7L311.6 189.4L326.9 200.0L327.6 201.1L327.0 203.0L313.6 222.6L301.9 238.9L300.8 239.7L298.0 239.0" +
        "L280.6 228.4L264.3 219.7L249.6 214.4L240.8 212.2L226.9 210.1L198.1 210.1L180.2 213.2L170.1 216.1L159.0 221.0" +
        "L151.8 225.8L144.8 232.8L142.0 237.0L140.2 242.2L139.1 248.1L139.1 250.9L140.4 257.6L144.4 264.6L145.8 266.2" +
        "L149.4 268.6L155.4 271.6L163.2 273.8L169.2 274.8L229.0 275.0L234.1 275.9L243.9 276.1L248.1 276.9L252.9 277.1" +
        "L265.8 279.2L278.8 282.2L291.6 286.4L298.7 289.3L308.6 294.4L319.2 301.8L328.2 310.8L333.6 318.4L339.6 330.4" +
        "L343.6 343.4L344.9 351.1L345.9 364.9L345.1 369.1L344.8 375.8L343.6 381.6L340.6 390.6L334.6 402.6L330.2 409.2" +
        "L321.7 418.7L314.6 424.6L302.0 433.0L291.3 438.3L279.9 442.9L270.8 445.8L248.8 450.8L236.2 452.2L232.9 452.9" +
        "L222.1 453.1L217.0 454.0L206.1 453.9L201.9 453.1L190.1 452.9L185.9 452.1L174.2 450.8L151.4 445.6L134.4 439.6" +
        "L118.7 432.3L104.4 423.6L91.8 414.2L86.7 409.3L82.8 406.2L71.3 394.8L70.6 393.2L85.3 378.3L88.7 375.7" +
        "L102.3 362.3L104.1 361.2L105.6 361.4L108.6 363.4L121.2 372.8L123.8 375.2L134.4 382.6L140.0 386.0L154.7 393.3" +
        "L169.4 398.6L178.2 400.8L198.1 403.9L228.9 403.9L233.1 403.1L237.8 402.8L256.6 398.6L265.6 395.6L276.6 390.6" +
        "L283.7 385.7L292.0 377.0L294.6 371.6L295.9 365.9L295.9 358.1L294.6 352.4L292.6 346.4L288.2 339.8L286.2 337.8" +
        "L279.7 333.3L273.6 330.4L267.6 328.4L258.8 326.2L251.9 325.1L246.1 324.9L241.0 324.0L172.1 323.9L167.9 323.1" +
        "L162.1 322.9L155.2 321.8L146.4 319.6L137.4 316.6L127.7 312.3L121.4 308.6L112.8 302.2L107.3 296.7L103.4 291.6" +
        "L99.0 285.0L95.4 277.6L92.2 267.8L90.2 256.8L90.2 241.2L92.2 231.2L95.4 221.4L101.4 209.4L107.3 201.3" +
        "L117.3 191.3L123.4 186.4L133.0 180.0L148.4 172.4L162.4 167.4L174.2 164.2L192.2 161.2L200.9 160.9 Z"

    val path: Path by lazy { PathParser().parsePathString(OUTLINE).toPath() }
}

/** Draws Serial's card [card] in size from this scope's origin, with corners of [corner] and a white rim [rim] wide, in pixels. */
internal fun DrawScope.drawSerialCard(card: Size, corner: Float, rim: Float) {
    drawRoundRect(SerialColors.CardBlack, size = card, cornerRadius = CornerRadius(corner))
    drawRoundRect(
        SerialColors.Rim,
        topLeft = Offset(rim / 2f, rim / 2f),
        size = Size(card.width - rim, card.height - rim),
        cornerRadius = CornerRadius((corner - rim / 2f).coerceAtLeast(0f)),
        style = Stroke(rim),
    )
    val fit = min(card.width / SerialMark.WIDTH, card.height / SerialMark.HEIGHT)
    val s = SerialMark.path
    translate((card.width - SerialMark.WIDTH * fit) / 2f, (card.height - SerialMark.HEIGHT * fit) / 2f) {
        scale(fit, pivot = Offset.Zero) {
            translate(SerialMark.Copy.x, SerialMark.Copy.y) { drawPath(s, SerialColors.Cream) }
            drawPath(s, SerialColors.Cream)
            clipPath(s) { drawPath(s, SerialColors.SRed, style = Stroke(SerialMark.EDGE * 2f)) }
        }
    }
}

/** One of the logo's plain cards: black with a white rim, [card] in size from this scope's origin, in pixels. */
internal fun DrawScope.drawPlainCard(card: Size, corner: Float, rim: Float, alpha: Float = 1f) {
    drawRoundRect(SerialColors.CardBlack, size = card, cornerRadius = CornerRadius(corner), alpha = alpha)
    drawRoundRect(
        SerialColors.Rim,
        topLeft = Offset(rim / 2f, rim / 2f),
        size = Size(card.width - rim, card.height - rim),
        cornerRadius = CornerRadius((corner - rim / 2f).coerceAtLeast(0f)),
        style = Stroke(rim),
        alpha = alpha,
    )
}
