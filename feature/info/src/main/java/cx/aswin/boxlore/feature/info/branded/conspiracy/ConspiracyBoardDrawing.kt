package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyBoardLayout.Anchor
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyBoardLayout.Holds

/** The paper, tiled across wide windows, drawn up behind the status bar, with the room's light on it. */
internal fun DrawScope.drawPaper(paper: ImageBitmap, statusBar: Dp, dark: Boolean, palette: ConspiracyPalette) {
    val unit = 1.dp.toPx()
    val top = -maxOf(statusBar.toPx(), ConspiracyBoardLayout.STATUS_BAR * unit)
    val bottom = (ConspiracyBoardLayout.PAPER_BOTTOM - ConspiracyBoardLayout.STATUS_BAR) * unit
    val tile = ConspiracyBoardLayout.WIDTH * unit
    drawRect(palette.page, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
    var left = ConspiracyBoardLayout.shift(Anchor.CENTRE, size.width / unit) * unit
    while (left > 0f) left -= tile
    while (left < size.width) {
        drawImage(
            paper,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(tile.toInt() + 1, (bottom - top).toInt()),
        )
        left += tile
    }
    // The room's light: a warm pool in the middle at night, and the board's edges falling away.
    val boxTop = -ConspiracyBoardLayout.STATUS_BAR * unit
    val boxHeight = 344f * unit
    clipRect(top = top, bottom = size.height) {
        if (dark) ellipse(Color(0x1AFFECC8), Color(0x00FFECC8), 0f, 0.6f, Offset(size.width / 2f, boxTop + boxHeight * 0.36f), size.width * 0.7f, boxHeight * 0.6f)
        val edge = if (dark) Color(0x80000000) else Color(0x1F3C3223)
        ellipse(edge.copy(alpha = 0f), edge, if (dark) 0.55f else 0.6f, 1f, Offset(size.width / 2f, boxTop + boxHeight * 0.4f), size.width * 1.2f, boxHeight)
    }
}

/** An elliptical radial gradient from [inner] at [from] to [outer] at [to] of its radii. */
private fun DrawScope.ellipse(inner: Color, outer: Color, from: Float, to: Float, centre: Offset, radiusX: Float, radiusY: Float) {
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = centre) {
        drawCircle(
            Brush.radialGradient(0f to inner, from to inner, to to outer, 1f to outer, center = centre, radius = radiusX),
            radius = radiusX * 4f,
            center = centre,
        )
    }
}

internal fun DrawScope.drawTopFade(statusBar: Dp, colour: Color) {
    val unit = 1.dp.toPx()
    val start = -ConspiracyBoardLayout.STATUS_BAR * unit
    val end = (ConspiracyBoardLayout.TOP_FADE - ConspiracyBoardLayout.STATUS_BAR) * unit
    val top = -maxOf(statusBar.toPx(), ConspiracyBoardLayout.STATUS_BAR * unit)
    drawRect(
        Brush.verticalGradient(0f to colour, 1f to colour.copy(alpha = 0f), startY = start, endY = end),
        topLeft = Offset(0f, top),
        size = Size(size.width, end - top),
    )
}

/** The yarn, the pins and the marker loop over the pieces, then the board fading into the page. */
@Composable
internal fun BoardThreads(clues: BoardClues, width: Float, clock: State<Float>, palette: ConspiracyPalette) {
    val shown = remember(clues) { pinnedPieces(clues) }
    Spacer(
        Modifier.fillMaxWidth().height((ConspiracyBoardLayout.BOTTOM - ConspiracyBoardLayout.STATUS_BAR).dp).drawWithCache {
            val unit = 1.dp.toPx()
            val origin = -ConspiracyBoardLayout.STATUS_BAR
            val yarns = ConspiracyBoardLayout.Yarns.mapIndexedNotNull { i, yarn ->
                if (yarn.needs !in shown) return@mapIndexedNotNull null
                YarnStrand(yarn.path, Offset(ConspiracyBoardLayout.shift(yarn.anchor, width), origin), unit, seed = i + 7) to yarn.drawAt
            }
            val paints = YarnPaints(unit, palette.yarnShadow)
            val pin = BoardPin(unit)
            val pins = ConspiracyBoardLayout.Pins.filter { it.holds in shown }
            val marker = if (Holds.CLIPPING in shown) {
                PathParser().parsePathString(ConspiracyBoardLayout.MARKER).toPath().apply {
                    transform(Matrix().apply { scale(unit, unit) })
                    translate(Offset(ConspiracyBoardLayout.shift(Anchor.CENTRE, width) * unit, origin * unit))
                }
            } else {
                null
            }
            val markerLength = marker?.let { android.graphics.PathMeasure(it.asAndroidPath(), false).length } ?: 0f
            val markerStroke = Stroke(2.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
            onDrawBehind {
                val t = clock.value
                if (marker != null) drawMarker(marker, markerLength, markerStroke.width, t)
                val sway = sway(t) * unit
                yarns.forEach { (strand, at) ->
                    strand.draw(this, YarnEasing.transform(progress(t, at, ConspiracyBoardLayout.YARN_MILLIS)), sway, paints)
                }
                pins.forEach { p ->
                    val at = Offset((p.at.x + ConspiracyBoardLayout.shift(p.anchor, width)) * unit, (p.at.y + origin) * unit)
                    drawPin(pin, at, progress(t, p.pushAt, ConspiracyBoardLayout.PUSH_MILLIS), unit)
                }
                val fadeTop = (ConspiracyBoardLayout.FADE_TOP + origin) * unit
                drawRect(
                    Brush.verticalGradient(0f to palette.page.copy(alpha = 0f), 1f to palette.page, startY = fadeTop, endY = size.height),
                    topLeft = Offset(0f, fadeTop),
                    size = Size(size.width, size.height - fadeTop),
                )
            }
        },
    )
}

/** Which pieces are on the board, so only their pins and yarn are drawn. */
internal fun pinnedPieces(clues: BoardClues): Set<Holds> = buildSet {
    add(Holds.SCRAP)
    if (clues.clipping != null) add(Holds.CLIPPING)
    if (clues.indexCard != null) add(Holds.INDEX_CARD)
    if (clues.sticky != null) add(Holds.STICKY)
    if (clues.peeks.isNotEmpty()) add(Holds.LEFT_PEEK)
    if (clues.peeks.size > 1) add(Holds.RIGHT_PEEK)
}

/** The red marker loop, drawn on round the newest clue. */
private fun DrawScope.drawMarker(marker: Path, length: Float, width: Float, t: Float) {
    val drawn = MarkerEasing.transform(progress(t, ConspiracyBoardLayout.MARKER_AT, ConspiracyBoardLayout.MARKER_MILLIS))
    if (drawn <= 0f) return
    drawPath(
        marker,
        ConspiracyColors.Marker.copy(alpha = 0.88f),
        style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(length * drawn, length + 1f))),
    )
}

/** A pin pushed in: it starts large and high, overshoots a little small, and settles. */
private fun DrawScope.drawPin(pin: BoardPin, at: Offset, p: Float, unit: Float) {
    if (p <= 0f) return
    val e = PushEasing.transform(p)
    val settle = 0.7f
    val (scale, alpha, lift) = if (e < settle) {
        val k = e / settle
        Triple(1.9f + (0.94f - 1.9f) * k, k, 1f - k)
    } else {
        val k = (e - settle) / (1f - settle)
        Triple(0.94f + (1f - 0.94f) * k, 1f, 0f)
    }
    translate(-3f * unit * scale * lift, -5f * unit * scale * lift) {
        scale(scale, pivot = at) {
            drawImage(pin.image, topLeft = at + pin.headOffset, alpha = alpha)
        }
    }
}

/** How far down the yarn has swayed, in dp: down 1.2dp and back in each sway, then still. */
private fun sway(t: Float): Float {
    val into = t - ConspiracyBoardLayout.SWAY_AT
    if (into <= 0f || t >= ConspiracyBoardLayout.END) return 0f
    val cycle = (into % ConspiracyBoardLayout.SWAY_MILLIS) / ConspiracyBoardLayout.SWAY_MILLIS
    val half = if (cycle < 0.5f) cycle / 0.5f else 1f - (cycle - 0.5f) / 0.5f
    return 1.2f * SwayEasing.transform(half)
}

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

private val PushEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val YarnEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val MarkerEasing = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)
private val SwayEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
