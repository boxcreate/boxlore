package cx.aswin.boxlore.feature.info.branded.sherlock

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.Keyframe
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.branded.brandedArtNow
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.introProgress
import cx.aswin.boxlore.feature.info.branded.keyframes
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * The Strand was a monthly, delivered to subscribers: the button becomes the green front door of
 * 221B, with its brass number plate and knob. The letterbox flap lifts, the new issue (the cover the
 * header opens) is pushed through, hangs from the slot by its top edge, then drops to the mat; the
 * flap snaps shut with a jolt of the door and one haptic. Then the button clears into Subscribed.
 * No words of its own. Timings follow the approved design board, in milliseconds from the tap.
 */
internal object SherlockLetterbox : BrandedSubscribeMoment {
    override val durationMillis = 1_480
    override val revealDelayMillis = 1_320

    /** The flap snaps shut behind the dropped issue. */
    const val SNAP_AT = 985f

    override fun nudge(elapsedMillis: Float): BrandedButtonNudge = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, SNAP_AT)
        val issue = brandedArtNow(R.drawable.sherlock_cover)
        val measurer = rememberTextMeasurer()
        // The door's number in plain bold lining figures, as on a brass plate.
        val plate = brandedSansStyle(10.sp, 13.sp, weight = 700).merge(TextStyle(color = PlateText, letterSpacing = 0.6.sp))
        val number = remember(plate) { measurer.measure(PLATE_NUMBER, plate) }
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    val t = elapsedMillis()
                    // Once the door covers the button it is already Subscribed underneath, so it clears straight to it.
                    drawRect(if (t < DOOR_COVERS_AT) style.subscribeContainer else style.subscribedContainer)
                    val door = LetterboxKeys.door(t)
                    if (door <= 0f) return@drawBehind
                    translate(left = LetterboxKeys.jolt(t) * 1.dp.toPx()) { drawDoor(t, door, issue, number) }
                },
            )
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = 1f - introProgress(elapsedMillis(), 0f, 80f) },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val land = Land.transform(introProgress(elapsedMillis(), LABEL_AT, 180f))
                    alpha = land
                    scaleX = 1.04f - 0.04f * land
                    scaleY = scaleX
                },
            )
        }
    }

    private fun DrawScope.drawDoor(t: Float, alpha: Float, issue: ImageBitmap, number: TextLayoutResult) {
        val unit = 1.dp.toPx()
        drawRect(Brush.verticalGradient(0f to DoorTop, 0.55f to DoorMiddle, 1f to DoorFoot), alpha = alpha)
        // The paint's brush marks and the light along the door's top.
        var x = 0f
        while (x < size.width) {
            drawRect(Grain, topLeft = Offset(x, 0f), size = Size(unit, size.height), alpha = alpha)
            x += 9f * unit
        }
        drawRect(Brush.verticalGradient(0f to Sheen, 0.4f to Color.Transparent), alpha = alpha)
        for ((left, width) in Panels) drawPanel(left * size.width, width * size.width, unit, alpha)
        drawPlate(number, unit, alpha)
        drawKnob(unit, alpha)
        drawLetterbox(t, unit, alpha, issue)
    }

    private fun DrawScope.drawPanel(left: Float, width: Float, unit: Float, alpha: Float) {
        val top = 6f * unit
        val height = size.height - 12f * unit
        val corner = CornerRadius(2f * unit)
        drawRoundRect(PanelRing, Offset(left - unit, top - unit), Size(width + 2 * unit, height + 2 * unit), corner, style = Stroke(2f * unit), alpha = alpha)
        drawLine(PanelLight, Offset(left, top + 0.5f * unit), Offset(left + width, top + 0.5f * unit), unit, alpha = alpha)
        drawLine(PanelLight, Offset(left + 0.5f * unit, top), Offset(left + 0.5f * unit, top + height), unit, alpha = alpha)
        drawLine(PanelDark, Offset(left, top + height - 0.5f * unit), Offset(left + width, top + height - 0.5f * unit), unit, alpha = alpha)
        drawLine(PanelDark, Offset(left + width - 0.5f * unit, top), Offset(left + width - 0.5f * unit, top + height), unit, alpha = alpha)
    }

    /** The brass 221B plate, left of the letterbox. */
    private fun DrawScope.drawPlate(number: TextLayoutResult, unit: Float, alpha: Float) {
        val width = number.size.width + 10f * unit
        val height = number.size.height + 2f * unit
        val left = PLATE_LEFT * size.width
        val top = (size.height - height) / 2f
        drawRoundRect(Shade, Offset(left, top + unit), Size(width, height), CornerRadius(2f * unit), alpha = alpha * 0.5f)
        drawRoundRect(brass(top, height), Offset(left, top), Size(width, height), CornerRadius(2f * unit), alpha = alpha)
        drawText(number, topLeft = Offset(left + 5f * unit, top + unit), alpha = alpha)
    }

    private fun DrawScope.drawKnob(unit: Float, alpha: Float) {
        val radius = 5.5f * unit
        val centre = Offset(size.width * (1f - KNOB_RIGHT) - radius, size.height / 2f)
        drawCircle(Shade, radius, centre + Offset(0f, 1.5f * unit), alpha = alpha * 0.55f)
        drawCircle(
            Brush.radialGradient(0f to KnobLight, 0.45f to BrassMid, 1f to BrassDark, center = centre + Offset(-0.15f * radius * 2, -0.2f * radius * 2), radius = radius * 1.6f),
            radius,
            centre,
            alpha = alpha,
        )
    }

    /** The letterbox, its slot, the issue coming through it and the flap over it. */
    private fun DrawScope.drawLetterbox(t: Float, unit: Float, alpha: Float, issue: ImageBitmap) {
        val box = Offset(size.width / 2f - BOX_LEFT_OF_CENTRE * unit, size.height / 2f - BOX_HEIGHT / 2f * unit)
        val boxSize = Size(BOX_WIDTH * unit, BOX_HEIGHT * unit)
        drawRoundRect(Shade, box + Offset(0f, 2f * unit), boxSize, CornerRadius(3f * unit), alpha = alpha * 0.55f)
        drawRoundRect(brass(box.y, boxSize.height), box, boxSize, CornerRadius(3f * unit), alpha = alpha)
        drawRoundRect(BrassEdge, box, boxSize, CornerRadius(3f * unit), style = Stroke(unit), alpha = alpha)
        drawRoundRect(Slot, box + Offset(8f * unit, 8f * unit), Size((BOX_WIDTH - 16f) * unit, 6f * unit), CornerRadius(1.5f * unit), alpha = alpha)
        // The issue shows only below the slot's top edge, inside the door: it comes in through the slot.
        val mail = box + Offset(6f * unit, 8f * unit)
        clipRect(left = mail.x, top = mail.y, right = mail.x + (BOX_WIDTH - 12f) * unit, bottom = size.height) {
            val drop = LetterboxKeys.issueY(t) * unit
            val turn = LetterboxKeys.issueTurn(t)
            val issueLeft = mail.x + 5f * unit
            translate(issueLeft, mail.y + drop) {
                rotate(turn, pivot = Offset(ISSUE_WIDTH / 2f * unit, 0f)) {
                    drawRect(Shade, Offset(0f, 2f * unit), Size(ISSUE_WIDTH * unit, ISSUE_HEIGHT * unit), alpha = alpha * 0.5f)
                    drawImage(issue, dstSize = IntSize((ISSUE_WIDTH * unit).roundToInt(), (ISSUE_HEIGHT * unit).roundToInt()), alpha = alpha)
                }
            }
        }
        // The flap is hinged along its top: lifted, it shows as a thin strip of brass.
        val lift = abs(cos(Math.toRadians(LetterboxKeys.flap(t).toDouble()))).toFloat()
        val flapTop = box.y + 6f * unit
        drawRoundRect(
            Brush.verticalGradient(0f to FlapLight, 0.55f to BrassMid, 1f to FlapDark, startY = flapTop, endY = flapTop + 10f * unit * lift),
            Offset(box.x + 6f * unit, flapTop),
            Size((BOX_WIDTH - 12f) * unit, maxOf(10f * unit * lift, unit)),
            CornerRadius(2f * unit),
            alpha = alpha,
        )
    }

    private const val DOOR_COVERS_AT = 100f
    private const val PLATE_NUMBER = "221B"
    private const val LABEL_AT = 1_300f
    private const val BOX_WIDTH = 76f
    private const val BOX_HEIGHT = 22f
    private const val BOX_LEFT_OF_CENTRE = 26f
    private const val ISSUE_WIDTH = 50f
    private const val ISSUE_HEIGHT = 70f
    private const val PLATE_LEFT = 0.09f
    private const val KNOB_RIGHT = 0.09f

    /** The door's two panels: left edge and width, as fractions of the button. */
    private val Panels = listOf(0.07f to 0.37f, 0.56f to 0.37f)
    private val Land = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}

/** The moment's keyframes, from the design board, in milliseconds from the tap. */
internal object LetterboxKeys {
    /** The door over the button: in fast, held while the issue is delivered, then gone before Subscribed lands. */
    fun door(t: Float): Float = when {
        t < 96f -> t / 96f
        t < 1_152f -> 1f
        else -> (1f - (t - 1_152f) / 128f).coerceAtLeast(0f)
    }

    /** The flap's angle round its hinge: lifts, holds while the issue comes through, snaps shut past closed and settles. */
    fun flap(t: Float): Float = keyframes(
        introProgress(t, 140f, 900f),
        Keyframe(0f, 0f, CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)),
        Keyframe(0.2f, -80f),
        Keyframe(0.88f, -82f, CubicBezierEasing(0.6f, 0f, 0.9f, 0.5f)),
        Keyframe(0.94f, 10f),
        Keyframe(1f, 0f),
    )

    /** How far the issue has come through the slot, in dp: pushed in, hanging by its top edge, then dropping away. */
    fun issueY(t: Float): Float = keyframes(
        introProgress(t, 300f, 700f),
        Keyframe(0f, -72f, CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)),
        Keyframe(0.45f, 1.5f),
        Keyframe(0.55f, 0f),
        Keyframe(0.75f, 0f, CubicBezierEasing(0.55f, 0f, 1f, 0.6f)),
        Keyframe(1f, 84f),
    )

    /** The issue's sway as it hangs, and its turn as it falls, in degrees. */
    fun issueTurn(t: Float): Float = keyframes(
        introProgress(t, 300f, 700f),
        Keyframe(0f, 0f),
        Keyframe(0.45f, 0f),
        Keyframe(0.55f, -1.6f),
        Keyframe(0.65f, 1f),
        Keyframe(0.75f, 0f, CubicBezierEasing(0.55f, 0f, 1f, 0.6f)),
        Keyframe(1f, 9f),
    )

    /** The door's jolt as the flap snaps, in dp. */
    fun jolt(t: Float): Float = keyframes(introProgress(t, 980f, 160f), Keyframe(0f, 0f), Keyframe(0.35f, 0.8f), Keyframe(0.7f, -0.4f), Keyframe(1f, 0f))
}

private val DoorTop = Color(0xFF24473A)
private val DoorMiddle = Color(0xFF1A3729)
private val DoorFoot = Color(0xFF132A1F)
private val Grain = Color(0x06FFFFFF)
private val Sheen = Color(0x12FFFFFF)
private val PanelLight = Color(0x1AFFFFFF)
private val PanelDark = Color(0x59000000)
private val PanelRing = Color(0x1F000000)
private val Shade = Color(0xFF000000)
private val BrassMid = Color(0xFFC9A24A)
private val BrassDark = Color(0xFF7A5A20)
private val BrassEdge = Color(0x80503710)
private val KnobLight = Color(0xFFFBEAB0)
private val FlapLight = Color(0xFFF6DE97)
private val FlapDark = Color(0xFF94702C)
private val Slot = Color(0xFF0B0907)
private val PlateText = Color(0xFF3A2A10)

/** Polished brass, lit from above, over a plate [height] tall from [top]. */
private fun brass(top: Float, height: Float): Brush =
    Brush.verticalGradient(0f to Color(0xFFF1D68A), 0.38f to BrassMid, 0.72f to Color(0xFF8A6526), 1f to Color(0xFFB89040), startY = top, endY = top + height)
