package cx.aswin.boxlore.feature.info.branded.conspiracy

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.conspiracy.ConspiracyBoardLayout.Piece

/**
 * The evidence board: white paper by day and black by night, carrying the show's faded typed
 * script. The wordmark is pinned on a scrap of the show's yellow paper; round it, the latest episodes
 * by their headlines ([boardClues]), with more peeking in from the window's edges, tied together with
 * red yarn that runs only over empty board. When the page opens the scraps drop in, the pins go in,
 * the yarn draws on and a red marker circles the newest clue; the yarn sways twice, then rests.
 */
@Composable
internal fun EvidenceBoard(clues: BoardClues, title: String, palette: ConspiracyPalette, fonts: ConspiracyFonts, modifier: Modifier = Modifier) {
    val clock = rememberBoardClock()
    val dark = LocalEffectiveDarkTheme.current
    val paper = ImageBitmap.imageResource(if (dark) R.drawable.conspiracy_board_black else R.drawable.conspiracy_board_white)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(modifier.fillMaxWidth().height((ConspiracyBoardLayout.BOTTOM - ConspiracyBoardLayout.STATUS_BAR).dp)) {
        val width = maxWidth.value
        Spacer(Modifier.matchParentSize().drawBehind { drawPaper(paper, statusBar, dark, palette) })
        val peeks = ConspiracyBoardLayout.Peeks
        clues.peeks.getOrNull(0)?.let { BoardIndexCard(it, peeks[0], width, clock, fonts) }
        clues.peeks.getOrNull(1)?.let { BoardClipping(it, peeks[1], width, clock, fonts, peek = true) }
        clues.peeks.getOrNull(2)?.let { BoardIndexCard(it, peeks[2], width, clock, fonts) }
        Spacer(Modifier.matchParentSize().drawBehind { drawTopFade(statusBar, palette.boardTopFade) })
        BoardScrap(title, width, clock)
        clues.clipping?.let { BoardClipping(it, ConspiracyBoardLayout.Clipping, width, clock, fonts, peek = false) }
        clues.indexCard?.let { BoardIndexCard(it, ConspiracyBoardLayout.IndexCard, width, clock, fonts) }
        clues.sticky?.let { BoardSticky(it, width, clock, fonts) }
        BoardThreads(clues, width, clock, palette)
    }
}

/** Milliseconds since the board opened, for its intro; at rest after the first time and with animations off. */
@Composable
private fun rememberBoardClock(): State<Float> {
    var played by rememberSaveable { mutableStateOf(false) }
    val animate = remember { !played && ValueAnimator.areAnimatorsEnabled() }
    val clock = remember { Animatable(if (animate) 0f else ConspiracyBoardLayout.END) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        played = true
        clock.animateTo(ConspiracyBoardLayout.END, tween(ConspiracyBoardLayout.END.toInt(), easing = LinearEasing))
    }
    return clock.asState()
}

/** Places a piece on the board and drops it in on the board's clock. */
private fun Modifier.boardPiece(piece: Piece, width: Float, clock: State<Float>): Modifier = this
    .offset {
        IntOffset(
            ((piece.x + ConspiracyBoardLayout.shift(piece.anchor, width)) * density).toInt(),
            ((piece.y - ConspiracyBoardLayout.STATUS_BAR) * density).toInt(),
        )
    }
    .size(piece.width.dp, piece.height.dp)
    .graphicsLayer {
        rotationZ = piece.rotation
        val drop = DropEasing.transform(progress(clock.value, piece.dropAt, ConspiracyBoardLayout.DROP_MILLIS))
        alpha = drop
        translationY = -12.dp.toPx() * (1f - drop)
    }

/** A pinned scrap's two shadows: a soft one below and a tight one at its edge. */
private fun Modifier.scrapShadow(fill: Color): Modifier = this
    .boxShadow(fill = fill, color = Color(0x47000000), offsetY = 6.dp, blur = 12.dp, cornerRadius = 0.dp)
    .boxShadow(fill = fill, color = Color(0x40000000), offsetY = 1.dp, blur = 2.dp, cornerRadius = 0.dp)

/** The show's wordmark on a scrap of its own yellow paper, the board's heading. */
@Composable
private fun BoardScrap(title: String, width: Float, clock: State<Float>) {
    Box(
        Modifier
            .boardPiece(ConspiracyBoardLayout.Scrap, width, clock)
            .scrapShadow(ScrapYellow)
            .clipToBounds()
            .semantics {
                heading()
                contentDescription = title
            },
    ) {
        Image(painterResource(R.drawable.conspiracy_scrap), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Image(
            painterResource(R.drawable.conspiracy_wordmark_ink),
            null,
            Modifier.offset(14.dp, 8.dp).size(200.dp, 63.dp),
            contentScale = ContentScale.FillBounds,
        )
    }
}

/** A newspaper clipping with a torn bottom edge: the headline, a rule and two lines of greyed-out print. */
@Composable
private fun BoardClipping(text: String, piece: Piece, width: Float, clock: State<Float>, fonts: ConspiracyFonts, peek: Boolean) {
    Column(
        Modifier
            .boardPiece(piece, width, clock)
            .clip(TornEdge)
            .background(ConspiracyColors.Clipping)
            .padding(start = 8.dp, end = 8.dp, top = if (peek) 12.dp else 15.dp, bottom = 6.dp)
            .clearAndSetSemantics { },
    ) {
        ClueText(text, fonts.newsprint, if (peek) 12.sp else 12.5.sp, if (peek) 13.5.sp else 14.sp, ConspiracyColors.ClippingInk)
        Spacer(Modifier.padding(top = 5.dp).fillMaxWidth().height(1.dp).background(ClippingRule))
        Spacer(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(ClippingPrint))
        Spacer(Modifier.padding(top = 4.dp).fillMaxWidth(if (peek) 0.84f else 0.88f).height(3.dp).background(ClippingPrint))
    }
}

/** A ruled index card with the headline typed on it. */
@Composable
private fun BoardIndexCard(text: String, piece: Piece, width: Float, clock: State<Float>, fonts: ConspiracyFonts) {
    Box(
        Modifier
            .boardPiece(piece, width, clock)
            .scrapShadow(ConspiracyColors.IndexCard)
            .clipToBounds()
            .drawBehind { drawIndexCardRules() }
            .padding(start = 7.dp, end = 7.dp, top = 15.dp, bottom = 6.dp)
            .clearAndSetSemantics { },
    ) {
        ClueText(text, fonts.typewriter, 11.sp, 13.5.sp, ConspiracyColors.Ink)
    }
}

private fun DrawScope.drawIndexCardRules() {
    val unit = 1.dp.toPx()
    drawRect(ConspiracyColors.IndexCard)
    var y = 11f * unit
    while (y < size.height) {
        drawRect(ConspiracyColors.IndexCardLine, topLeft = Offset(0f, y), size = Size(size.width, unit))
        y += 13f * unit
    }
    drawRect(ConspiracyColors.IndexCardRule, topLeft = Offset(0f, 10f * unit), size = Size(size.width, unit))
}

/** A yellow sticky note with the headline written on it by hand. */
@Composable
private fun BoardSticky(text: String, width: Float, clock: State<Float>, fonts: ConspiracyFonts) {
    Box(
        Modifier
            .boardPiece(ConspiracyBoardLayout.Sticky, width, clock)
            .drawBehind { drawStickyShadow() }
            .background(ConspiracyColors.Sticky)
            .background(Brush.verticalGradient(0f to Color(0x0F000000), 0.3f to Color(0x00000000)))
            .padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 4.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        ClueText(text, fonts.handwriting, 14.5.sp, 14.sp, ConspiracyColors.Ink, TextAlign.Center)
    }
}

/** A note curling off the board: its shadow falls short of its sides and well below it. */
private fun DrawScope.drawStickyShadow() {
    val unit = 1.dp.toPx()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = ConspiracyColors.Sticky.toArgb()
        setShadowLayer(blurRadius(5f * unit), 0f, 10f * unit, StickyShadow.toArgb())
    }
    val inset = 6f * unit
    drawContext.canvas.nativeCanvas.drawRect(inset, inset, size.width - inset, size.height - inset, paint)
}

@Composable
private fun ClueText(text: String, family: FontFamily, size: TextUnit, lineHeight: TextUnit, colour: Color, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = brandedTextStyle(family, size, lineHeight),
        color = colour,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

private fun progress(t: Float, start: Float, duration: Float): Float = ((t - start) / duration).coerceIn(0f, 1f)

private val DropEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)

private val ScrapYellow = Color(0xFFD0AB1C)
private val ClippingRule = Color(0x80282420)
private val ClippingPrint = Color(0x52282420)
private val StickyShadow = Color(0x66000000)

/** The clipping's torn bottom edge, as the design cuts it. */
private val TornEdge = GenericShape { size, _ ->
    val points = listOf(
        0f to 0f, 1f to 0f, 1f to 0.88f, 0.94f to 0.92f, 0.88f to 0.87f, 0.8f to 0.94f, 0.72f to 0.89f, 0.63f to 0.96f,
        0.55f to 0.9f, 0.46f to 0.95f, 0.38f to 0.89f, 0.29f to 0.95f, 0.2f to 0.9f, 0.11f to 0.96f, 0.04f to 0.91f, 0f to 0.95f,
    )
    points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height) }
    close()
}
