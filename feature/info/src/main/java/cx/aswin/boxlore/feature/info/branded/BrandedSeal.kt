package cx.aswin.boxlore.feature.info.branded

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.feature.info.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The Showcase seal centred in the top bar, the same on every Showcase page: a serrated holographic
 * seal with THE BEST OF on its top and bottom arcs and boxlore's mark, tilted, in the middle, so it
 * reads "the best of boxlore". A sheen sweeps across it a few times when the page opens. Tapping it
 * opens a short note about the page in the page's [popup] colours.
 */
@Composable
internal fun BrandedSeal(popup: BrandedCopyrightStyle, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.branded_seal_label)
    Box(
        modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                role = Role.Button,
                onClick = { open = !open },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(BrandedSealShape.SIZE.dp)) {
            SealArt()
            if (open) {
                val style = remember(popup) { popup.copy(caretCentre = popup.popupWidth / 2, gap = 4.dp) }
                BrandedPopup(style, onDismiss = { open = false }) {
                    Text(stringResource(R.string.branded_seal_title), style = brandedSansStyle(14.sp, 18.sp, weight = 500), color = style.title)
                    Text(
                        stringResource(R.string.branded_seal_body),
                        style = brandedSansStyle(13.sp, 18.sp),
                        color = style.body,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SealArt() {
    val mark = painterResource(DesignR.drawable.ic_boxlore_app_mark)
    val typeface = rememberSealTypeface()
    val sheen = rememberSealSheen()
    Spacer(
        Modifier.size(BrandedSealShape.SIZE.dp).drawWithCache {
            val unit = size.width / BrandedSealShape.SIZE
            val c = size.width / 2f
            val edge = Path().apply {
                BrandedSealShape.edge().forEachIndexed { i, p ->
                    if (i == 0) moveTo(c + p.x * c, c + p.y * c) else lineTo(c + p.x * c, c + p.y * c)
                }
                close()
            }
            // The shadow is cast by a plain fill under the foil: a shadow drawn with the foil's shader takes its colours.
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = SealFoil[1]
                // CSS drop-shadow(0 3px 5px): a blur sigma of half the radius.
                val sigma = SHADOW_BLUR * unit / 2f
                setShadowLayer(((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f), 0f, SHADOW_OFFSET * unit, SealShadow.toArgb())
            }
            val foil = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = SweepGradient(c, c, SealFoil, null).apply {
                    setLocalMatrix(Matrix().apply { setRotate(BrandedSealShape.FOIL_START_DEGREES, c, c) })
                }
            }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = BrandedSealShape.TEXT_SIZE * unit
                letterSpacing = BrandedSealShape.TEXT_SPACING / BrandedSealShape.TEXT_SIZE
                color = SealSoftInk.toArgb()
            }
            val words = "THE BEST OF"
            val top = BrandedSealShape.topTextRadius * unit
            val bottom = BrandedSealShape.bottomTextRadius * unit
            val topArc = android.graphics.Path().apply { addArc(c - top, c - top, c + top, c + top, 180f, 180f) }
            val bottomArc = android.graphics.Path().apply { addArc(c - bottom, c - bottom, c + bottom, c + bottom, 180f, -180f) }
            val wordsWidth = text.measureText(words)
            val markWidth = BrandedSealShape.MARK_WIDTH * size.width
            val markSize = Size(markWidth, markWidth * MARK_HEIGHT_RATIO)
            val ring = Stroke(BrandedSealShape.RING_STROKE * unit)
            val texture = Stroke(1f * unit)
            onDrawBehind {
                drawContext.canvas.nativeCanvas.drawPath(edge.asAndroidPath(), shadow)
                drawContext.canvas.nativeCanvas.drawPath(edge.asAndroidPath(), foil)
                clipPath(edge) {
                    // The foil's fine concentric rings: 1 of every 3 units, from 2 units out.
                    var r = TEXTURE_FIRST
                    while (r < BrandedSealShape.SIZE / 2f) {
                        drawCircle(SealTexture, radius = r * unit, center = Offset(c, c), style = texture)
                        r += TEXTURE_STEP
                    }
                    val along = sheen.value
                    if (along > -BrandedSealSheen.TRAVEL && along < BrandedSealSheen.TRAVEL) {
                        val centre = Offset(c, c) + BrandedSealSheen.Direction * (along * size.width)
                        val half = BrandedSealSheen.Direction * (BrandedSealSheen.HALF_WIDTH * size.width)
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, SealSheen, Color.Transparent), start = centre - half, end = centre + half))
                    }
                }
                drawCircle(SealSoftInk, radius = BrandedSealShape.RING_OUTER * c, center = Offset(c, c), style = ring)
                drawCircle(SealSoftInk, radius = BrandedSealShape.RING_INNER * c, center = Offset(c, c), style = ring)
                val native = drawContext.canvas.nativeCanvas
                native.drawTextOnPath(words, topArc, (PI.toFloat() * top - wordsWidth) / 2f, 0f, text)
                native.drawTextOnPath(words, bottomArc, (PI.toFloat() * bottom - wordsWidth) / 2f, 0f, text)
                val dot = BrandedSealShape.dotRadius * unit
                drawCircle(SealSoftInk, radius = BrandedSealShape.DOT_RADIUS * unit, center = Offset(c - dot, c))
                drawCircle(SealSoftInk, radius = BrandedSealShape.DOT_RADIUS * unit, center = Offset(c + dot, c))
                rotate(BrandedSealShape.MARK_TILT, pivot = Offset(c, c)) {
                    translate(c - markSize.width / 2f, c - markSize.height / 2f) {
                        with(mark) { draw(markSize, colorFilter = ColorFilter.tint(SealInk)) }
                    }
                }
            }
        },
    )
}

/** Where the sheen is along its path: it sweeps [BrandedSealSheen.SWEEPS] times, then rests off the seal. */
@Composable
private fun rememberSealSheen(): Animatable<Float, *> {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val sheen = remember { Animatable(-BrandedSealSheen.TRAVEL) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        repeat(BrandedSealSheen.SWEEPS) {
            sheen.snapTo(-BrandedSealSheen.TRAVEL)
            sheen.animateTo(BrandedSealSheen.TRAVEL, tween(BrandedSealSheen.SWEEP_MILLIS, easing = SweepEasing))
            delay(BrandedSealSheen.REST_MILLIS.toLong())
        }
    }
    return sheen
}

/** boxlore's font at weight 800 for the arc text, built the way the app builds its Google Sans. */
@SuppressLint("LocalContextResourcesRead") // A bundled font file is the same in every configuration.
@Composable
private fun rememberSealTypeface(): android.graphics.Typeface {
    val context = LocalContext.current
    return remember {
        try {
            val font = android.graphics.fonts.Font.Builder(context.resources, DesignR.font.google_sans_flex_variable)
                .setFontVariationSettings("'wght' $SEAL_TEXT_WEIGHT")
                .setWeight(SEAL_TEXT_WEIGHT)
                .build()
            android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build()).build()
        } catch (_: Exception) {
            android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, SEAL_TEXT_WEIGHT, false)
        }
    }
}

/** The seal's shape in dp, from its 44dp design. */
internal object BrandedSealShape {
    const val SIZE = 44f

    /** Teeth around the edge. */
    const val POINTS = 36

    /** How deep the teeth go: the inner corners sit at this share of the radius. */
    const val TOOTH_DEPTH = 0.925f

    /** The two printed rings, as shares of the radius. */
    const val RING_OUTER = 0.835f
    const val RING_INNER = 0.555f
    const val RING_STROKE = 0.9f

    const val TEXT_SIZE = 4.6f
    const val TEXT_SPACING = 0.55f

    /** How far the arc text sits from the ring it rests on. */
    const val TEXT_GAP = 1.4f
    const val DOT_RADIUS = 0.75f

    /** The mark's width as a share of the seal, and its tilt. */
    const val MARK_WIDTH = 0.37f
    const val MARK_TILT = -12f

    /** The foil's colours start at 210° clockwise from the top, as the design's conic gradient does. */
    const val FOIL_START_DEGREES = 120f

    private const val RADIUS = SIZE / 2f

    /** The top words stand on the inner ring, the bottom words hang from the outer one; both read upright. */
    val topTextRadius: Float get() = RADIUS * RING_INNER + TEXT_GAP
    val bottomTextRadius: Float get() = RADIUS * RING_OUTER - TEXT_GAP

    /** The dots between the words sit midway between the rings. */
    val dotRadius: Float get() = RADIUS * (RING_OUTER + RING_INNER) / 2f

    /** The edge's corners around the centre, in shares of the radius: outer and inner in turn, starting at 3 o'clock. */
    fun edge(): List<Offset> = List(POINTS * 2) { n ->
        val angle = PI * n / POINTS
        val r = if (n % 2 == 0) 1f else TOOTH_DEPTH
        Offset((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
    }
}

/** The sheen: a bright band at 115° that crosses the seal corner to corner, then rests. */
internal object BrandedSealSheen {
    const val SWEEPS = 3
    const val SWEEP_MILLIS = 1760
    const val REST_MILLIS = 1440

    /** The band's centre travels this far each side of the seal's centre, in seal widths: fully off at both ends. */
    const val TRAVEL = 1.0233f

    /** Half the band's width, in seal widths. */
    const val HALF_WIDTH = 0.292f

    /** The band moves along this unit direction: right and a little down, 115° in CSS terms. */
    val Direction = Offset(0.9063f, 0.4226f)
}

private val SweepEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** The mark's height for its width: the B with the microphone is 820 by 640. */
private const val MARK_HEIGHT_RATIO = 640f / 820f
private const val SEAL_TEXT_WEIGHT = 800
private const val SHADOW_BLUR = 5f
private const val SHADOW_OFFSET = 3f
private const val TEXTURE_FIRST = 2.5f
private const val TEXTURE_STEP = 3f

private val SealFoil = intArrayOf(0xFFFFB3D9.toInt(), 0xFFFFE08A.toInt(), 0xFFC6FF9E.toInt(), 0xFF8FE9FF.toInt(), 0xFFB9A6FF.toInt(), 0xFFFFB3D9.toInt())

/** The mark prints in full ink; the rings, words and dots in the same ink, faded. */
internal val SealInk = Color(0xFF2A1630)
internal val SealSoftInk = Color(0x8C2A1630)
private val SealTexture = Color(0x42FFFFFF)
private val SealSheen = Color(0xCCFFFFFF)
private val SealShadow = Color(0x66000000)
