package cx.aswin.boxlore.feature.info.branded.desicrime

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedIntroClock
import cx.aswin.boxlore.feature.info.branded.Keyframe
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.branded.introProgress
import cx.aswin.boxlore.feature.info.branded.keyframes
import cx.aswin.boxlore.feature.info.branded.rememberBrandedArt
import cx.aswin.boxlore.feature.info.branded.rememberBrandedIntro
import kotlin.math.roundToInt

/**
 * Their crime board, from behind Aryaan and Aishwarya on the show's cover: cork under the studio's
 * warm lamp, crowded with prints of past cases ([DesiCrimeFeed.board]: each case's own picture, cut
 * by one rule to the photo band above its DESI CRIME lettering, the nearest three captioned with
 * the case's people). In the middle, the newest case's picture down to its torn edge, so the DESI
 * CRIME lettering sits on the board, with its number on a sticky note; a Hindi paper's cutting, a
 * "सबूत?" (evidence?) tape and two more notes. The hosts peek up over their arms in front of it,
 * each held to their own edge of the window. When the page has settled the lamp flickers on, the
 * newest print drops and is pinned, and its number is slapped on. A tap does it again.
 */
@Composable
internal fun DesiCrimeBoard(board: DesiCrimeBoard, modifier: Modifier = Modifier) {
    val art by rememberBrandedArt(R.drawable.desicrime_cork, R.drawable.desicrime_host_her, R.drawable.desicrime_host_him, R.drawable.desicrime_clipping, R.drawable.desicrime_tape)
    val intro = rememberBrandedIntro(ready = art != null, endMillis = DesiCrimeArt.END)
    val fonts = rememberDesiCrimeFonts()
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val title = stringResource(R.string.desicrime_wordmark_description)
    val hosts = stringResource(R.string.desicrime_hosts_description)
    BoxWithConstraints(modifier.fillMaxWidth().height(DesiCrimeArt.HEIGHT.dp)) {
        val width = maxWidth.value
        Spacer(Modifier.fillMaxSize().drawBehind { drawCork(art?.get(0), statusBar.toPx()) })
        val pictures = art
        if (pictures != null) {
            val slots = DesiCrimeArt.slots(width)
            slots.indices.reversed().forEach { i ->
                val print = board.prints.getOrNull(i) ?: return@forEach
                val slot = slots[i]
                Polaroid(print, slot, fonts, Modifier.place(slot.edge, slot.x, slot.y, width).zIndex(DesiCrimeArt.PRINTS_Z - i))
            }
            BoardImage(pictures[3], Piece(Edge.Right, 260f, 160f, 84f, 112f), width, DesiCrimeArt.CLIPPING_Z)
            NewestPrint(board, intro, Modifier.place(Edge.Centre, DesiCrimeArt.NEWEST_X, DesiCrimeArt.NEWEST_Y, width).zIndex(DesiCrimeArt.NEWEST_Z))
            NewestPin(intro, Modifier.place(Edge.Centre, DesiCrimeArt.PIN_X, DesiCrimeArt.PIN_Y, width).zIndex(DesiCrimeArt.PIN_Z))
            BoardImage(pictures[4], Piece(Edge.Centre, 112f, 186f, 70f, 30f), width, DesiCrimeArt.PIN_Z)
            Note(stringResource(R.string.desicrime_note_show), 11.5.sp, fonts, Modifier.place(Edge.Centre, 160f, 266f, width).zIndex(DesiCrimeArt.NOTES_Z).graphicsLayer { rotationZ = 4f })
            Note(stringResource(R.string.desicrime_note_who), 12.sp, fonts, Modifier.place(Edge.Right, 328f, 238f, width).zIndex(DesiCrimeArt.NOTES_Z).graphicsLayer { rotationZ = 7f })
            board.newestNumber?.let { number -> NumberNote(number, fonts, intro, Modifier.place(Edge.Centre, 114f, 98f, width).zIndex(DesiCrimeArt.NUMBER_Z)) }
            Spacer(Modifier.fillMaxSize().zIndex(DesiCrimeArt.LIGHT_Z).drawBehind { drawStudioLight(intro.millis(), statusBar.toPx()) })
            Spacer(
                Modifier
                    .fillMaxSize()
                    .zIndex(DesiCrimeArt.HOSTS_Z)
                    .semantics { contentDescription = hosts }
                    .drawBehind { drawHosts(pictures[1], pictures[2]) },
            )
        }
        // The board's name for screen readers; the lettering is on the newest print.
        Spacer(
            Modifier
                .offset(0.dp, (DesiCrimeArt.TAP_Y - DesiCrimeArt.STATUS_BAR).dp)
                .fillMaxWidth()
                .height(DesiCrimeArt.TAP_HEIGHT.dp)
                .zIndex(DesiCrimeArt.TAP_Z)
                .semantics {
                    heading()
                    contentDescription = title
                },
        )
        // The board plays again on a tap: a flourish for the eye only, so screen readers skip it.
        Spacer(
            Modifier
                .offset(0.dp, (DesiCrimeArt.TAP_Y - DesiCrimeArt.STATUS_BAR).dp)
                .fillMaxWidth()
                .height(DesiCrimeArt.TAP_HEIGHT.dp)
                .zIndex(DesiCrimeArt.TAP_Z)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = intro.again)
                .clearAndSetSemantics {},
        )
    }
}

/** Which edge of the window a piece keeps its distance from, so a wider window opens space between them. */
internal enum class Edge { Left, Centre, Right }

internal data class Piece(val edge: Edge, val x: Float, val y: Float, val width: Float, val height: Float)

/** Where a piece designed at [x] on the 369dp board sits on a window [width] wide. */
internal fun boardX(edge: Edge, x: Float, width: Float): Float = when (edge) {
    Edge.Left -> x
    Edge.Centre -> x + (width - DesiCrimeArt.WIDTH) / 2f
    Edge.Right -> x + width - DesiCrimeArt.WIDTH
}

private fun Modifier.place(edge: Edge, x: Float, y: Float, width: Float): Modifier =
    offset { IntOffset((boardX(edge, x, width) * density).roundToInt(), ((y - DesiCrimeArt.STATUS_BAR) * density).roundToInt()) }

@Composable
private fun BoardImage(bitmap: ImageBitmap, piece: Piece, width: Float, z: Float) {
    Spacer(
        Modifier
            .place(piece.edge, piece.x, piece.y, width)
            .size(piece.width.dp, piece.height.dp)
            .zIndex(z)
            .drawBehind { drawImage(bitmap, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())) },
    )
}

/** An earlier case's print: its photo band in a white border, pinned or taped, captioned where the slot has room. */
@Composable
private fun Polaroid(print: DesiCrimePrint, slot: DesiCrimeSlot, fonts: DesiCrimeFonts, modifier: Modifier) {
    Box(modifier.width(108.dp).graphicsLayer { rotationZ = slot.rotation }) {
        Column(
            Modifier
                .width(108.dp)
                .boxShadow(fill = PrintPaperColor, color = DesiCrimeColors.Shadow.copy(alpha = 0.55f), offsetY = 4.dp, blur = 8.dp, cornerRadius = 0.dp)
                .background(PrintPaper)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp),
        ) {
            Box(Modifier.size(100.dp, 34.dp).clipToBounds()) {
                // The episode picture's photo band: its top 34%, above the DESI CRIME lettering.
                OptimizedImage(
                    url = print.picture,
                    proxyWidth = 200,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = PrintTone,
                    modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(100.dp),
                )
                Spacer(Modifier.fillMaxSize().background(Brush.linearGradient(0f to Glare, 0.4f to Color.Transparent)))
            }
            if (slot.captioned) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = DesiCrimeColors.CaseNumber)) { append("#${print.number}") }
                        append(" ")
                        append(print.caption)
                    },
                    style = brandedTextStyle(fonts.handBold, 9.sp, 10.5.sp),
                    color = DesiCrimeColors.PrintInk,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(top = 2.dp, bottom = 1.dp).clearAndSetSemantics {},
                )
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
        Spacer(
            Modifier.matchParentSize().drawBehind {
                if (slot.taped) drawTape() else drawBrassPin(Offset(54.dp.toPx(), 1.dp.toPx()), 9.dp.toPx())
            },
        )
    }
}

/** The newest case's picture down to its torn edge, dropped onto the board as the lamp comes up. */
@Composable
private fun NewestPrint(board: DesiCrimeBoard, intro: BrandedIntroClock, modifier: Modifier) {
    val picture = board.newestPicture ?: return
    Box(
        modifier
            .size(DesiCrimeArt.NEWEST_WIDTH.dp, DesiCrimeArt.NEWEST_HEIGHT.dp)
            .graphicsLayer {
                val pose = NewestMotion.pose(intro.millis())
                transformOrigin = TransformOrigin(0.5f, 0.04f)
                translationX = pose.x.dp.toPx()
                translationY = pose.y.dp.toPx()
                rotationZ = pose.rotation
                scaleX = pose.scale
                scaleY = pose.scale
                alpha = pose.alpha
            }
            .drawWithCache {
                val shadow = TornShadow()
                onDrawBehind { drawTornShadow(shadow, NewestMotion.lift(intro.millis())) }
            },
    ) {
        OptimizedImage(
            url = picture,
            fallbackUrls = listOf(board.cover),
            proxyWidth = 300,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(DesiCrimeArt.NEWEST_WIDTH.dp)
                .clip(TornEdge),
        )
    }
}

/** The newest print's own pin, pushed in once it has landed. */
@Composable
private fun NewestPin(intro: BrandedIntroClock, modifier: Modifier) {
    Spacer(
        modifier
            .size(11.dp)
            .graphicsLayer {
                val p = PinIn.transform(introProgress(intro.millis(), 760f, 200f))
                alpha = p.coerceIn(0f, 1f)
                translationX = -5.dp.toPx() * (1f - p)
                translationY = -9.dp.toPx() * (1f - p)
                scaleX = 1.9f - 0.9f * p
                scaleY = scaleX
            }
            .drawBehind { drawBrassPin(center, size.width) },
    )
}

/** The newest case's number, on a sticky slapped onto its print and underlined in red. */
@Composable
private fun NumberNote(number: Int, fonts: DesiCrimeFonts, intro: BrandedIntroClock, modifier: Modifier) {
    Box(
        modifier
            .size(46.dp, 40.dp)
            .graphicsLayer {
                val p = introProgress(intro.millis(), 1_080f, 260f)
                val slap = Slap.transform(p)
                transformOrigin = TransformOrigin(0.6f, 0.2f)
                alpha = (p / 0.6f).coerceIn(0f, 1f)
                rotationZ = -24f + 15f * slap
                scaleX = 1.7f - 0.7f * slap
                scaleY = scaleX
            },
    ) {
        StickyPaper(Modifier.matchParentSize())
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("#$number", style = brandedTextStyle(fonts.handBold, 16.sp, 16.sp), color = DesiCrimeColors.NoteInk, maxLines = 1)
            Spacer(
                Modifier
                    .padding(top = 1.dp)
                    .size(30.dp, 2.dp)
                    .graphicsLayer { rotationZ = -3f }
                    .background(DesiCrimeColors.Underline, RoundedCornerShape(1.dp)),
            )
        }
        Spacer(Modifier.matchParentSize().drawBehind { drawPushPin(Offset(size.width / 2f, 3.dp.toPx()), 8.dp.toPx()) })
    }
}

/** A sticky note on the board, in their handwriting. */
@Composable
private fun Note(text: String, size: TextUnit, fonts: DesiCrimeFonts, modifier: Modifier) {
    Box(modifier.size(44.dp, 40.dp).clearAndSetSemantics {}) {
        StickyPaper(Modifier.matchParentSize())
        Text(
            text,
            style = brandedTextStyle(fonts.handBold, size, 12.sp),
            color = DesiCrimeColors.NoteInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
        Spacer(Modifier.matchParentSize().drawBehind { drawPushPin(Offset(this.size.width / 2f, 3.dp.toPx()), 8.dp.toPx()) })
    }
}

/** A sticky note's paper: yellow, a little darker towards its foot and under its glued top, lifting off the cork. */
@Composable
internal fun StickyPaper(modifier: Modifier) {
    Spacer(
        modifier
            .boxShadow(fill = StickyColor, color = DesiCrimeColors.Shadow.copy(alpha = 0.5f), offsetY = 4.dp, blur = 6.dp, cornerRadius = 0.dp)
            .background(DesiCrimeColors.Sticky)
            .drawBehind {
                val band = 9.dp.toPx()
                drawRect(Brush.verticalGradient(0f to DesiCrimeColors.StickyTop, 1f to Color.Transparent, endY = band), size = Size(size.width, band))
            },
    )
}

/** The newest print's drop: from above and tilted, onto the board, settling at its slant. */
internal object NewestMotion {
    data class Pose(val x: Float, val y: Float, val rotation: Float, val scale: Float, val alpha: Float)

    private val Drop = CubicBezierEasing(0.3f, 0.7f, 0.35f, 1f)

    fun pose(t: Float): Pose {
        val p = introProgress(t, 220f, 560f)
        return Pose(
            x = keyframes(p, Keyframe(0f, 18f, Drop), Keyframe(0.78f, 0f, Drop), Keyframe(1f, 0f)),
            y = keyframes(p, Keyframe(0f, -46f, Drop), Keyframe(0.78f, 2f, Drop), Keyframe(1f, 0f)),
            rotation = keyframes(p, Keyframe(0f, -11f, Drop), Keyframe(0.78f, -1.6f, Drop), Keyframe(1f, -2.5f)),
            scale = keyframes(p, Keyframe(0f, 1.12f, Drop), Keyframe(0.78f, 1f, Drop), Keyframe(1f, 1f)),
            alpha = keyframes(p, Keyframe(0f, 0f, Drop), Keyframe(0.55f, 1f), Keyframe(1f, 1f)),
        )
    }

    /** How far the print has come down onto the board, 0 to 1, for its shadow. */
    fun lift(t: Float): Float = introProgress(t, 220f, 560f)
}

/** The board in the design's units (dp), from the top of the status bar, which is 43dp tall there. */
internal object DesiCrimeArt {
    const val WIDTH = 369f
    const val STATUS_BAR = 43f
    const val HEIGHT = 355f - STATUS_BAR
    const val NEWEST_X = 112f
    const val NEWEST_Y = 106f
    const val NEWEST_WIDTH = 146f
    const val NEWEST_HEIGHT = 89f
    const val PIN_X = 185f - 5.5f
    const val PIN_Y = 110f - 5.5f
    const val HOSTS_Y = 183.9f
    const val HOSTS_HEIGHT = 170.6f
    const val TOP_SHADE = 118f
    const val TAP_Y = 102f
    const val TAP_HEIGHT = 118f
    const val END = 1_340f
    const val PRINTS_Z = 10f
    const val CLIPPING_Z = 11f
    const val NEWEST_Z = 12f
    const val PIN_Z = 13f
    const val NOTES_Z = 14f
    const val NUMBER_Z = 15f
    const val LIGHT_Z = 16f
    const val HOSTS_Z = 17f
    const val TAP_Z = 18f

    /** A window this wide has room between the edges' prints for [WideSlots]. */
    const val WIDE_FROM = 640f

    /** The slots for a window [width] wide: the design's, and on a wide window more between them. */
    fun slots(width: Float): List<DesiCrimeSlot> = if (width >= WIDE_FROM) Slots + WideSlots else Slots

    /** Where earlier cases' prints go, most prominent first; the three nearest the lamp are captioned. */
    val Slots = listOf(
        DesiCrimeSlot(Edge.Left, 2f, 104f, -5f, taped = false, captioned = true),
        DesiCrimeSlot(Edge.Right, 256f, 104f, 5f, taped = false, captioned = true),
        DesiCrimeSlot(Edge.Left, 6f, 168f, 3f, taped = true, captioned = true),
        DesiCrimeSlot(Edge.Left, 6f, 50f, 3f, taped = false, captioned = false),
        DesiCrimeSlot(Edge.Centre, 130f, 52f, -2f, taped = true, captioned = false),
        DesiCrimeSlot(Edge.Right, 254f, 49f, 3f, taped = false, captioned = false),
        DesiCrimeSlot(Edge.Left, 2f, 3f, -3f, taped = false, captioned = false),
        DesiCrimeSlot(Edge.Centre, 126f, -1f, 2f, taped = true, captioned = false),
        DesiCrimeSlot(Edge.Right, 250f, 4f, -2f, taped = false, captioned = false),
    )

    /** On a wide window, more prints either side of the middle, above the hosts. */
    val WideSlots = listOf(
        DesiCrimeSlot(Edge.Centre, -40f, 106f, 4f, taped = false, captioned = false),
        DesiCrimeSlot(Edge.Centre, 300f, 108f, -3f, taped = true, captioned = false),
        DesiCrimeSlot(Edge.Centre, -64f, 52f, -2f, taped = true, captioned = false),
        DesiCrimeSlot(Edge.Centre, 326f, 50f, 3f, taped = false, captioned = false),
        DesiCrimeSlot(Edge.Centre, -58f, 2f, 2f, taped = false, captioned = false),
        DesiCrimeSlot(Edge.Centre, 320f, 0f, -2f, taped = false, captioned = false),
    )

    /** Prints the feed fills at most: every slot on the widest board. */
    val MAX_PRINTS = Slots.size + WideSlots.size
}

internal data class DesiCrimeSlot(val edge: Edge, val x: Float, val y: Float, val rotation: Float, val taped: Boolean, val captioned: Boolean)

private val PrintPaperColor = Color(0xFFEBE5D8)
private val PrintPaper = Brush.verticalGradient(0f to Color(0xFFF3EFE5), 1f to Color(0xFFE3DCCC))
private val StickyColor = Color(0xFFE9E07F)
private val PrintTone = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.85f) })
private val Glare = Color(0x33FFFFFF)
private val PinIn = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1.4f)
private val Slap = CubicBezierEasing(0.25f, 0.8f, 0.35f, 1.25f)
