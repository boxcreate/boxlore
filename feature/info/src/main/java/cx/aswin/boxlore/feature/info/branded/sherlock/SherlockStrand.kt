package cx.aswin.boxlore.feature.info.branded.sherlock

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedIntroClock
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.introProgress
import cx.aswin.boxlore.feature.info.branded.rememberBrandedArt
import cx.aswin.boxlore.feature.info.branded.rememberBrandedIntro
import kotlin.math.roundToInt

/**
 * A reader's treasured issue of The Strand Magazine, where Doyle's Holmes stories first ran, lying
 * on a lamp-lit desk: George Charles Haité's 1891 cover with the show's wordmark on its banner. When
 * the page has settled the issue slides in and its cover turns over on the spine, opening on Sidney
 * Paget's Reichenbach plate and a contents page with the wordmark as its masthead, listing the
 * stories the show has read, newest first ([SherlockFeed.contents]). A tap opens it again. On a
 * window narrower than the design the issue shrinks to fit; the desk always fills the window.
 */
@Composable
internal fun StrandDesk(contents: List<StrandContentsLine>, modifier: Modifier = Modifier) {
    val art by rememberBrandedArt(R.drawable.sherlock_desk, R.drawable.sherlock_cover, R.drawable.sherlock_inside_cover, R.drawable.sherlock_page)
    val intro = rememberBrandedIntro(ready = art != null, endMillis = StrandArt.END)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val title = stringResource(R.string.sherlock_wordmark_description)
    BoxWithConstraints(modifier.fillMaxWidth().height(StrandArt.HEIGHT.dp)) {
        val fit = (maxWidth.value / StrandArt.WIDTH).coerceAtMost(1f)
        Spacer(Modifier.fillMaxSize().drawBehind { drawDesk(art?.get(0), statusBar.toPx()) })
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .requiredWidth(StrandArt.WIDTH.dp)
                .height(StrandArt.HEIGHT.dp)
                .graphicsLayer {
                    scaleX = fit
                    scaleY = fit
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .semantics {
                    heading()
                    contentDescription = title
                },
        ) {
            art?.let { Issue(it[1], it[2], it[3], contents, intro) }
            // The issue opens again on a tap: a flourish for the eye only, so screen readers skip it.
            Spacer(
                Modifier
                    .offset(StrandArt.ISSUE_X.dp, StrandArt.ISSUE_Y.dp)
                    .size(StrandArt.ISSUE_WIDTH.dp, StrandArt.ISSUE_HEIGHT.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = intro.again)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/** The issue, tilted on the desk: its shadows, the contents page, and the cover leaf turning on the spine. */
@Composable
private fun Issue(cover: ImageBitmap, insideCover: ImageBitmap, page: ImageBitmap, contents: List<StrandContentsLine>, intro: BrandedIntroClock) {
    val fonts = rememberStrandFonts()
    Box(
        Modifier
            .offset(StrandArt.ISSUE_X.dp, StrandArt.ISSUE_Y.dp)
            .size(StrandArt.ISSUE_WIDTH.dp, StrandArt.ISSUE_HEIGHT.dp)
            .graphicsLayer {
                val slide = StrandMotion.slide(intro.millis())
                translationX = (1f - slide) * StrandArt.SLIDE_FROM.dp.toPx()
                rotationZ = StrandArt.TILT_FROM + (StrandArt.TILT - StrandArt.TILT_FROM) * slide
            }
            .drawBehind { drawIssueShadows(StrandMotion.leftShadow(intro.millis())) },
    ) {
        Spacer(
            Modifier
                .offset(StrandArt.PAGE.dp, (-StrandArt.LEAF_OVERHANG).dp)
                .size(StrandArt.LEAF_WIDTH.dp, StrandArt.LEAF_HEIGHT.dp)
                .drawBehind { drawImage(page, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) },
        )
        ContentsList(
            contents,
            fonts,
            Modifier
                .offset((StrandArt.PAGE + StrandArt.CONTENTS_X).dp, StrandArt.CONTENTS_Y.dp)
                .size(StrandArt.CONTENTS_WIDTH.dp, StrandArt.CONTENTS_HEIGHT.dp),
        )
        Spacer(
            Modifier
                .offset(StrandArt.PAGE.dp, 0.dp)
                .size(StrandArt.PAGE.dp, StrandArt.ISSUE_HEIGHT.dp)
                .drawBehind {
                    val shade = StrandMotion.shade(intro.millis())
                    if (shade > 0f) drawRect(Brush.horizontalGradient(0f to PageShadeNear, 1f to PageShadeFar), alpha = shade)
                },
        )
        val leaf = remember { StrandLeaf() }
        Spacer(Modifier.matchParentSize().drawBehind { with(leaf) { draw(cover, insideCover, StrandMotion.leafAngle(intro.millis())) } })
    }
}

/**
 * The cover leaf, turning on the spine in perspective as the design's 900dp CSS perspective draws
 * it: its free edge lifts towards the reader, and past upright it shows the inside of the cover.
 * Projected here rather than with a layer's camera, so it looks the same on every device.
 */
private class StrandLeaf {
    private val matrix = android.graphics.Matrix()
    private val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val from = FloatArray(8)
    private val to = FloatArray(8)

    fun DrawScope.draw(cover: ImageBitmap, insideCover: ImageBitmap, degrees: Float) {
        val unit = 1.dp.toPx()
        val radians = Math.toRadians(degrees.toDouble())
        val cos = kotlin.math.cos(radians).toFloat()
        val sin = kotlin.math.sin(radians).toFloat()
        // Edge-on, the leaf has no width to draw.
        if (kotlin.math.abs(cos) < EDGE_ON) return
        val spine = StrandArt.PAGE * unit
        val middle = StrandArt.ISSUE_HEIGHT / 2f * unit
        val half = StrandArt.LEAF_HEIGHT / 2f * unit
        val width = StrandArt.LEAF_WIDTH * unit
        val camera = StrandArt.PERSPECTIVE * unit
        // The free edge comes towards the reader (z) as the leaf turns, and looks bigger for it.
        val lift = camera / (camera + width * sin)
        val freeX = spine + width * cos * lift
        val freeTop = middle - half * lift
        val freeBottom = middle + half * lift
        val front = degrees > -90f
        val picture = (if (front) cover else insideCover).asAndroidBitmap()
        from[0] = 0f
        from[1] = 0f
        from[2] = picture.width.toFloat()
        from[3] = 0f
        from[4] = picture.width.toFloat()
        from[5] = picture.height.toFloat()
        from[6] = 0f
        from[7] = picture.height.toFloat()
        // The cover's left edge is on the spine; seen from behind, the inside's right edge is.
        val nearX = if (front) spine else freeX
        val farX = if (front) freeX else spine
        val nearTop = if (front) middle - half else freeTop
        val nearBottom = if (front) middle + half else freeBottom
        val farTop = if (front) freeTop else middle - half
        val farBottom = if (front) freeBottom else middle + half
        to[0] = nearX
        to[1] = nearTop
        to[2] = farX
        to[3] = farTop
        to[4] = farX
        to[5] = farBottom
        to[6] = nearX
        to[7] = nearBottom
        if (!matrix.setPolyToPoly(from, 0, to, 0, 4)) return
        drawContext.canvas.nativeCanvas.drawBitmap(picture, matrix, paint)
    }

    private companion object {
        const val EDGE_ON = 0.01f
    }
}

/** The contents: each story, a dotted leader and its page, as many lines as the page holds at this text size. */
@Composable
private fun ContentsList(contents: List<StrandContentsLine>, fonts: StrandFonts, modifier: Modifier) {
    val story = brandedTextStyle(fonts.roman, 8.5.sp, 11.sp).copy(color = SherlockColors.Body)
    val number = brandedTextStyle(fonts.roman, 8.sp, 11.sp).copy(color = SherlockColors.Body, textAlign = TextAlign.End)
    Layout(
        content = {
            contents.forEach { line ->
                ContentsLine(
                    story = { Text(line.story, style = story, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    page = { Text(line.page.toString(), style = number, maxLines = 1, modifier = Modifier.width(StrandArt.CONTENTS_NUMBER.dp)) },
                )
            }
        },
        modifier = modifier.clearAndSetSemantics {},
    ) { measurables, constraints ->
        val rows = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            var y = 0
            for (row in rows) {
                if (y + row.height > constraints.maxHeight) break
                row.place(0, y)
                y += row.height
            }
        }
    }
}

/** One contents line: the story (cut short when it runs long), then dots up to its page number. */
@Composable
private fun ContentsLine(story: @Composable () -> Unit, page: @Composable () -> Unit) {
    Layout(
        content = {
            story()
            Spacer(Modifier.drawBehind { drawLeader() })
            page()
        },
    ) { measurables, constraints ->
        val gap = 2.dp.roundToPx()
        val minLeader = StrandArt.LEADER_MIN.dp.roundToPx()
        val number = measurables[2].measure(Constraints())
        val title = measurables[0].measure(Constraints(maxWidth = (constraints.maxWidth - number.width - minLeader - 2 * gap).coerceAtLeast(0)))
        val leaderWidth = (constraints.maxWidth - title.width - number.width - 2 * gap).coerceAtLeast(0)
        val height = maxOf(title.height, number.height)
        val leader = measurables[1].measure(Constraints.fixed(leaderWidth, height))
        layout(constraints.maxWidth, height) {
            title.place(0, 0)
            leader.place(title.width + gap, 0)
            number.place(constraints.maxWidth - number.width, height - number.height)
        }
    }
}

private fun DrawScope.drawLeader() {
    val step = 2.2.dp.toPx()
    val radius = 0.45.dp.toPx()
    val y = size.height - 3.dp.toPx()
    var x = step / 2f
    while (x < size.width) {
        drawCircle(LeaderDot, radius, Offset(x, y))
        x += step
    }
}

/** The desk fills the header and reaches up behind the status bar, cropped to cover; its foot fades into the page. */
private fun DrawScope.drawDesk(desk: ImageBitmap?, statusBar: Float) {
    val unit = 1.dp.toPx()
    val top = -maxOf(statusBar, StrandArt.STATUS_BAR * unit)
    clipRect(top = top, bottom = size.height) {
        drawRect(DeskFallback, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        if (desk != null) {
            val height = size.height - top
            val scale = maxOf(size.width / desk.width, height / desk.height)
            val srcWidth = (size.width / scale).roundToInt().coerceAtMost(desk.width)
            val srcHeight = (height / scale).roundToInt().coerceAtMost(desk.height)
            drawImage(
                desk,
                srcOffset = IntOffset((desk.width - srcWidth) / 2, 0),
                srcSize = IntSize(srcWidth, srcHeight),
                dstOffset = IntOffset(0, top.roundToInt()),
                dstSize = IntSize(size.width.roundToInt(), height.roundToInt()),
            )
        }
        val fade = StrandArt.DESK_FADE * unit
        drawRect(
            Brush.verticalGradient(0f to SherlockColors.Page.copy(alpha = 0f), 1f to SherlockColors.Page, startY = size.height - fade, endY = size.height),
            topLeft = Offset(0f, size.height - fade),
            size = Size(size.width, fade),
        )
    }
}

/** The soft shadows the issue casts on the desk: under the right-hand pages, and under the cover once it lies open on the left. */
private fun DrawScope.drawIssueShadows(left: Float) {
    val unit = 1.dp.toPx()
    val paint = android.graphics.Paint().apply {
        color = Color.Black.toArgb()
        maskFilter = BlurMaskFilter(StrandArt.SHADOW_BLUR * unit, BlurMaskFilter.Blur.NORMAL)
    }
    val width = (StrandArt.PAGE + StrandArt.SHADOW_SPREAD) * unit
    val height = (StrandArt.ISSUE_HEIGHT + StrandArt.SHADOW_DROP) * unit
    val top = StrandArt.SHADOW_DROP * unit
    drawContext.canvas.nativeCanvas.apply {
        paint.alpha = (StrandArt.SHADOW_ALPHA * 255).roundToInt()
        val right = (StrandArt.PAGE - 2f) * unit
        drawRect(right, top, right + width, top + height, paint)
        if (left > 0f) {
            paint.alpha = (StrandArt.SHADOW_ALPHA * left * 255).roundToInt()
            drawRect(-4f * unit, top, -4f * unit + width, top + height, paint)
        }
    }
}

/** The issue's intro, from the approved design board, in milliseconds from the start. */
internal object StrandMotion {
    private val Turn = CubicBezierEasing(0.45f, 0.05f, 0.3f, 1f)

    private fun turn(t: Float): Float = Turn.transform(introProgress(t, StrandArt.START, StrandArt.TURN))

    /** How far the issue has slid into place, 0 to 1. */
    fun slide(t: Float): Float = turn(t)

    /** The cover leaf's angle round the spine: 0 lying closed over the pages, -180 lying open on the left. */
    fun leafAngle(t: Float): Float = -180f * turn(t)

    /** Whether the leaf still shows its front, the cover; past the upright it shows the inside. */
    fun showsFront(t: Float): Boolean = turn(t) < 0.5f

    /** The open cover's shadow on the desk, 0 to 1: it comes in during the second half of the turn. */
    fun leftShadow(t: Float): Float = ((introProgress(t, StrandArt.START, StrandArt.TURN) - 0.45f) / 0.55f).coerceIn(0f, 1f)

    /** The shade over the contents page while the cover is over it, lifting as the cover opens. */
    fun shade(t: Float): Float {
        val p = introProgress(t, StrandArt.START, StrandArt.TURN)
        return if (p < 0.55f) 1f - 0.5f * (p / 0.55f) else 0.5f * (1f - (p - 0.55f) / 0.45f)
    }
}

/** The header's frame in the design's units (dp), from the top of the status bar, which is 43dp tall there. */
internal object StrandArt {
    const val WIDTH = 369f
    const val STATUS_BAR = 43f
    const val HEIGHT = 342f - STATUS_BAR
    const val ISSUE_X = 18f
    const val ISSUE_Y = 104f - STATUS_BAR
    const val ISSUE_WIDTH = 324f
    const val ISSUE_HEIGHT = 228f

    /** One page's width: the spine runs down the issue's middle. */
    const val PAGE = ISSUE_WIDTH / 2f

    /** The cover leaf is a little bigger than the pages it closes over. */
    const val LEAF_OVERHANG = 2f
    const val LEAF_WIDTH = PAGE + 2f
    const val LEAF_HEIGHT = ISSUE_HEIGHT + 2 * LEAF_OVERHANG
    const val CONTENTS_X = 16f
    const val CONTENTS_Y = 97f
    const val CONTENTS_WIDTH = PAGE - CONTENTS_X - 10f
    const val CONTENTS_HEIGHT = 102f
    const val CONTENTS_NUMBER = 15f
    const val LEADER_MIN = 6f
    const val TILT = -1.2f
    const val TILT_FROM = -3f
    const val SLIDE_FROM = -81f
    const val START = 120f
    const val TURN = 1_050f
    const val END = START + TURN

    /** The design's CSS perspective, from the spine. */
    const val PERSPECTIVE = 900f

    const val SHADOW_BLUR = 12f
    const val SHADOW_SPREAD = 6f
    const val SHADOW_DROP = 2f
    const val SHADOW_ALPHA = 0.55f
    const val DESK_FADE = 17f
}

private val DeskFallback = Color(0xFF24150E)
private val LeaderDot = Color(0x8C2E241C)
private val PageShadeNear = Color(0x8C0A0604)
private val PageShadeFar = Color(0x260A0604)
