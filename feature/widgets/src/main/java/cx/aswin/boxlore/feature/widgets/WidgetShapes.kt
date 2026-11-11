package cx.aswin.boxlore.feature.widgets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Outlines the widgets cut artwork and placeholders into. */
internal enum class WidgetArtShape {
    CIRCLE,
    COOKIE,
    ARCH,
    FLOWER,
    ROUNDED,
}

/**
 * Shape outlines and the bitmaps cut from them. Masks are white, so the launcher tints them with a
 * [WidgetChrome] colour; artwork keeps its own colours.
 */
internal object WidgetShapes {
    /** Tile shapes for the Your Shows grid when the listener chose mixed shapes, in reading order. */
    private val MIXED =
        listOf(
            WidgetArtShape.COOKIE, WidgetArtShape.CIRCLE, WidgetArtShape.ARCH, WidgetArtShape.FLOWER,
            WidgetArtShape.CIRCLE, WidgetArtShape.FLOWER, WidgetArtShape.COOKIE, WidgetArtShape.ARCH,
            WidgetArtShape.FLOWER, WidgetArtShape.COOKIE, WidgetArtShape.ARCH, WidgetArtShape.CIRCLE,
        )

    fun mixedShapeAt(position: Int): WidgetArtShape = MIXED[position.mod(MIXED.size)]

    /** The outline of [shape] filling a [size] × [size] square. */
    fun path(shape: WidgetArtShape, size: Float): Path = when (shape) {
        WidgetArtShape.CIRCLE -> Path().apply { addOval(RectF(0f, 0f, size, size), Path.Direction.CW) }
        WidgetArtShape.COOKIE -> scalloped(size, lobes = COOKIE_LOBES, depth = COOKIE_DEPTH)
        WidgetArtShape.FLOWER -> scalloped(size, lobes = FLOWER_LOBES, depth = FLOWER_DEPTH)
        WidgetArtShape.ARCH -> {
            val foot = size * ARCH_FOOT_RADIUS
            val crown = size / 2f
            Path().apply {
                addRoundRect(
                    RectF(0f, 0f, size, size),
                    floatArrayOf(crown, crown, crown, crown, foot, foot, foot, foot),
                    Path.Direction.CW,
                )
            }
        }
        WidgetArtShape.ROUNDED -> {
            val radius = size * ROUNDED_RADIUS
            Path().apply { addRoundRect(RectF(0f, 0f, size, size), radius, radius, Path.Direction.CW) }
        }
    }

    /** A soft-scalloped circle: the radius swells and dips [lobes] times around the edge. */
    private fun scalloped(size: Float, lobes: Int, depth: Float): Path {
        val center = size / 2f
        val steps = lobes * STEPS_PER_LOBE
        return Path().apply {
            for (step in 0 until steps) {
                val angle = 2.0 * PI * step / steps
                val radius = center * ((1f - depth) + depth * cos(lobes * angle).toFloat())
                val x = center + radius * sin(angle).toFloat()
                val y = center - radius * cos(angle).toFloat()
                if (step == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }

    /**
     * A white [shape] on a clear ground, for placeholders the launcher tints. [insetFraction] matches
     * [shapeArtwork], so a placeholder sits exactly where the artwork would.
     */
    fun mask(shape: WidgetArtShape, sizePx: Int, insetFraction: Float = 0f): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val inset = size * insetFraction.coerceIn(0f, MAX_INSET)
        return createBitmap(size, size).also { bitmap ->
            Canvas(bitmap).apply {
                translate(inset, inset)
                drawPath(path(shape, (size - inset * 2f).coerceAtLeast(1f)), fillPaint())
            }
        }
    }

    /**
     * [source] centre-cropped into [shape]. [insetFraction] leaves a clear band around the artwork so
     * a progress ring of the same size can sit outside it.
     */
    fun shapeArtwork(source: Bitmap, sizePx: Int, shape: WidgetArtShape, insetFraction: Float = 0f): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val inset = size * insetFraction.coerceIn(0f, MAX_INSET)
        val inner = (size - inset * 2f).coerceAtLeast(1f)
        val crop = minOf(source.width, source.height).toFloat()
        val matrix =
            Matrix().apply {
                postTranslate(-(source.width - crop) / 2f, -(source.height - crop) / 2f)
                postScale(inner / crop, inner / crop)
            }
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
            }
        return createBitmap(size, size).also { bitmap ->
            Canvas(bitmap).apply {
                translate(inset, inset)
                drawPath(path(shape, inner), paint)
            }
        }
    }

    /**
     * The two arcs of a progress ring as white masks: what has played, then a gap, then what is left.
     * Either is null when it has nothing to draw.
     */
    fun progressRing(sizePx: Int, fraction: Float, strokeFraction: Float): ProgressRing {
        val size = sizePx.coerceAtLeast(1)
        val stroke = max(size * strokeFraction, 1f)
        val radius = (size - stroke) / 2f
        val arcs = WidgetRingArcs.of(fraction, gapDegrees = Math.toDegrees((stroke * GAP_STROKES / radius).toDouble()).toFloat())
        val bounds = RectF(stroke / 2f, stroke / 2f, size - stroke / 2f, size - stroke / 2f)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }

        fun draw(arc: WidgetRingArcs.Arc?): Bitmap? = arc?.let {
            createBitmap(size, size).also { bitmap ->
                Canvas(bitmap).drawArc(bounds, RING_START_DEGREES + it.startDegrees, it.sweepDegrees, false, paint)
            }
        }
        return ProgressRing(played = draw(arcs.played), remaining = draw(arcs.remaining))
    }

    class ProgressRing(val played: Bitmap?, val remaining: Bitmap?)

    /** Decodes [path] downsampled to roughly [targetPx] on its short side, or null when unreadable. */
    fun decodeSampled(path: String, targetPx: Int): Bitmap? {
        val target = targetPx.coerceAtLeast(1)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= target && bounds.outHeight / (sampleSize * 2) >= target) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun fillPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private const val COOKIE_LOBES = 12
    private const val COOKIE_DEPTH = 0.07f
    private const val FLOWER_LOBES = 8
    private const val FLOWER_DEPTH = 0.1f
    private const val STEPS_PER_LOBE = 12
    private const val ARCH_FOOT_RADIUS = 0.22f
    private const val ROUNDED_RADIUS = 0.24f
    private const val MAX_INSET = 0.4f
    private const val GAP_STROKES = 1.6f
    private const val RING_START_DEGREES = -90f
}

/**
 * Where a progress ring's arcs start and how far they sweep, in degrees clockwise from the top.
 * Pure geometry so it can be tested without a canvas.
 */
internal data class WidgetRingArcs(val played: Arc?, val remaining: Arc?) {
    data class Arc(val startDegrees: Float, val sweepDegrees: Float)

    companion object {
        private const val FULL = 360f

        /** A sliver of progress still shows as a visible dot. */
        private const val MIN_PLAYED_SWEEP = 2f

        fun of(fraction: Float, gapDegrees: Float): WidgetRingArcs {
            val clamped = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
            val gap = gapDegrees.coerceIn(0f, FULL / 4f)
            if (clamped <= 0f) return WidgetRingArcs(played = null, remaining = Arc(0f, FULL))
            val playedSweep = (FULL * clamped).coerceAtLeast(MIN_PLAYED_SWEEP)
            val remainingSweep = FULL - playedSweep - gap * 2f
            // Too little left for a separate arc: the played arc closes the ring.
            if (remainingSweep <= gap) return WidgetRingArcs(played = Arc(0f, FULL), remaining = null)
            return WidgetRingArcs(
                played = Arc(0f, playedSweep),
                remaining = Arc(playedSweep + gap, remainingSweep),
            )
        }
    }
}
