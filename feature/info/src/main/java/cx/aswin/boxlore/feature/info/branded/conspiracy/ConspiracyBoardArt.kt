package cx.aswin.boxlore.feature.info.branded.conspiracy

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A pushpin as the board draws it: a glossy red head lit from the top left, with a specular glint,
 * a darker rim, the needle's soft shadow and the head's shadow on the paper, all in [unit] pixels
 * per dp. The head sits 3dp up and left of the point it is pinned at.
 */
internal class BoardPin(unit: Float) {
    /** The bitmap is 30dp square; the head's centre is 12dp in from its top left. */
    val image: ImageBitmap
    val headOffset = Offset(-15f * unit, -15f * unit)

    init {
        val size = (30f * unit).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.scale(unit, unit)
        canvas.translate(12f, 12f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // The needle's shadow, then the head's, both soft.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color(0x73000000).toArgb()
        paint.maskFilter = BlurMaskFilter(blurRadius(1.4f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawLine(0f, 0f, 7f, 9f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color(0x61000000).toArgb()
        paint.maskFilter = BlurMaskFilter(blurRadius(2.2f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawOval(RectF(3.4f - 6.6f, 5f - 5.2f, 3.4f + 6.6f, 5f + 5.2f), paint)
        paint.maskFilter = null
        // The head: a sphere lit from the top left. The paint's own colour must be opaque, or it fades the shader.
        paint.color = android.graphics.Color.BLACK
        paint.shader = RadialGradient(
            -1.58f,
            -2.38f,
            9.24f,
            intArrayOf(0xFFFF8C7C.toInt(), 0xFFE2352A.toInt(), 0xFFA31A11.toInt(), 0xFF6E0F09.toInt()),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(0f, 0f, 6.6f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.7f
        paint.color = Color(0x593C0000).toArgb()
        canvas.drawCircle(0f, 0f, 6.6f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color(0x40500000).toArgb()
        canvas.drawOval(RectF(-4.6f, 1.6f - 3f, 4.6f, 1.6f + 3f), paint)
        // The glint, and a faint reflection from the paper.
        canvas.withRotation(-30f, -2.2f, -2.6f) {
            paint.color = Color(0xE6FFFFFF).toArgb()
            drawOval(RectF(-2.2f - 2f, -2.6f - 1.3f, -2.2f + 2f, -2.6f + 1.3f), paint)
        }
        paint.color = Color(0x80FFBEB4).toArgb()
        canvas.drawCircle(2.6f, 2.2f, 0.8f, paint)
        image = bitmap.asImageBitmap()
    }
}

/**
 * One length of twisted red yarn: plied (light and dark strands crossing it at an angle), a little
 * fuzzy, wavering slightly along its length, with a soft shadow on the board. Built once for its full
 * length, so drawing it on bit by bit never changes what is already drawn.
 */
internal class YarnStrand(pathData: String, offset: Offset, unit: Float, seed: Int) {
    private val path = android.graphics.Path()
    private val measure: PathMeasure
    val length: Float
    private val fibres: FloatArray
    private val fibreAlpha: FloatArray
    private val segment = android.graphics.Path()

    init {
        val source = android.graphics.Path(PathParser().parsePathString(pathData).toPath().asAndroidPath())
        source.transform(
            Matrix().apply {
            setScale(unit, unit)
            postTranslate(offset.x * unit, offset.y * unit)
        }
        )
        val sourceMeasure = PathMeasure(source, false)
        val total = sourceMeasure.length
        val step = unit
        val point = FloatArray(2)
        val tangent = FloatArray(2)
        val random = Random(seed)
        val phase = random.nextFloat() * 2f * PI.toFloat()
        val fibreList = ArrayList<Float>()
        val alphaList = ArrayList<Float>()
        var s = 0f
        var first = true
        while (s <= total) {
            sourceMeasure.getPosTan(s, point, tangent)
            // A slow, uneven waver across the yarn, at most about 0.4dp.
            val d = s / unit
            val waver = (0.25f * sin(d * 0.19f + phase) + 0.15f * sin(d * 0.53f + 2f * phase)) * unit
            val x = point[0] - tangent[1] * waver
            val y = point[1] + tangent[0] * waver
            if (first) path.moveTo(x, y) else path.lineTo(x, y)
            first = false
            // Fibres stand off the yarn's edges every so often, at odd angles.
            if (random.nextFloat() < FIBRE_CHANCE) {
                val side = if (random.nextBoolean()) 1f else -1f
                val out = (1.0f + random.nextFloat() * 0.6f) * unit * side
                val angle = (random.nextFloat() - 0.5f) * 2.1f
                val len = (0.5f + random.nextFloat() * 0.9f) * unit
                val bx = x - tangent[1] * out
                val by = y + tangent[0] * out
                val dirX = tangent[0] * cos(angle) - tangent[1] * sin(angle) * side
                val dirY = tangent[1] * cos(angle) + tangent[0] * sin(angle) * side
                fibreList += listOf(s, bx, by, bx + dirX * len, by + dirY * len)
                alphaList += 0.3f + random.nextFloat() * 0.35f
            }
            s += step
        }
        measure = PathMeasure(path, false)
        length = measure.length
        fibres = fibreList.toFloatArray()
        fibreAlpha = alphaList.toFloatArray()
    }

    /** Draws the yarn from its start to [progress] of its length, moved down [sway] pixels. */
    fun draw(scope: DrawScope, progress: Float, sway: Float, paints: YarnPaints) {
        if (progress <= 0f) return
        val end = length * progress.coerceAtMost(1f)
        segment.reset()
        measure.getSegment(0f, end, segment, true)
        val canvas = scope.drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.translate(0f, sway)
        canvas.save()
        canvas.translate(paints.shadowOffset.x, paints.shadowOffset.y)
        canvas.drawPath(segment, paints.shadow)
        canvas.restore()
        canvas.drawPath(segment, paints.halo)
        var i = 0
        while (i < fibres.size) {
            if (fibres[i] <= end) {
                paints.fibre.alpha = (fibreAlpha[i / 5] * 255).roundToInt()
                canvas.drawLine(fibres[i + 1], fibres[i + 2], fibres[i + 3], fibres[i + 4], paints.fibre)
            }
            i += 5
        }
        canvas.drawPath(segment, paints.ply)
        canvas.restore()
    }

    private companion object {
        const val FIBRE_CHANCE = 0.55f
    }
}

/** The paints every length of yarn shares, for one screen density and theme. */
internal class YarnPaints(unit: Float, shadowColor: Color, width: Float = 2.5f, shadowShift: Offset = Offset(1.6f, 4.6f)) {
    val shadowOffset = shadowShift * unit

    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (width - 0.1f) * unit
        strokeCap = Paint.Cap.ROUND
        color = shadowColor.toArgb()
        maskFilter = BlurMaskFilter(blurRadius(1.7f * unit), BlurMaskFilter.Blur.NORMAL)
    }

    /** A faint fuzz just wider than the yarn. */
    val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (width + 1.1f) * unit
        strokeCap = Paint.Cap.ROUND
        color = Color(0x389E2419).toArgb()
    }

    val fibre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.4f * unit
        strokeCap = Paint.Cap.ROUND
        color = Color(0xFFB4302A).toArgb()
    }

    /** The plies: stripes 3.4dp apart, turned 36°, across the yarn wherever it runs. */
    val ply = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width * unit
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        val period = PLY_PERIOD * unit
        val angle = Math.toRadians(PLY_ANGLE).toFloat()
        shader = LinearGradient(
            0f,
            0f,
            period * cos(angle),
            period * sin(angle),
            PlyColors,
            PlyStops,
            Shader.TileMode.REPEAT,
        )
    }

    private companion object {
        const val PLY_PERIOD = 3.4f
        const val PLY_ANGLE = 36.0

        // Bright strand 0–1.6, its sheen to 2.05, the base to 2.9, a dark crease to 3.2, the base again.
        val PlyColors = intArrayOf(
            0xFFC2392C.toInt(), 0xFFC2392C.toInt(),
            0xFFC44A3D.toInt(), 0xFFC44A3D.toInt(),
            0xFF9A1D15.toInt(), 0xFF9A1D15.toInt(),
            0xFF7E1710.toInt(), 0xFF7E1710.toInt(),
            0xFF9A1D15.toInt(), 0xFF9A1D15.toInt(),
        )
        val PlyStops = floatArrayOf(0f, 0.47f, 0.47f, 0.6f, 0.6f, 0.853f, 0.853f, 0.941f, 0.941f, 1f)
    }
}

/** The platform's blur radius for a CSS blur of standard deviation [sigma]. */
internal fun blurRadius(sigma: Float): Float = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
