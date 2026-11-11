package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces
import kotlin.math.cos
import kotlin.math.sin

/**
 * The seek icon the player uses: a circular arrow around the number of seconds it jumps. Drawn as a
 * white mask so the launcher tints it, and redrawn when the listener changes the seek length.
 */
internal object WidgetSeekIcon {
    fun mask(context: Context, seconds: Int, forward: Boolean, sizePx: Int): Bitmap {
        val size = sizePx.coerceAtLeast(1).toFloat()
        val stroke = size * STROKE
        val inset = stroke * INSET_STROKES
        val startAngle = if (forward) FORWARD_START else BACK_START
        val sweepAngle = if (forward) SWEEP else -SWEEP
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        return createBitmap(size.toInt(), size.toInt()).also { bitmap ->
            val canvas = Canvas(bitmap)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(RectF(inset, inset, size - inset, size - inset), startAngle, sweepAngle, false, paint)

            paint.style = Paint.Style.FILL
            canvas.drawPath(arrowHead(size, stroke, inset, startAngle + sweepAngle, forward), paint)

            paint.typeface =
                GoogleSansTypefaces.create(
                    context = context,
                    weight = NUMBER_WEIGHT,
                    roundness = GoogleSansTypefaces.cachedRoundness(context),
                )
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = size * if (seconds >= THREE_DIGITS) NUMBER_SIZE_LONG else NUMBER_SIZE
            val metrics = paint.fontMetrics
            canvas.drawText(seconds.toString(), size / 2f, size / 2f - (metrics.ascent + metrics.descent) / 2f, paint)
        }
    }

    private fun arrowHead(size: Float, stroke: Float, inset: Float, endAngle: Float, forward: Boolean): Path {
        val endRadians = Math.toRadians(endAngle.toDouble())
        val radius = (size - inset * 2f) / 2f
        val center = size / 2f
        val endX = center + radius * cos(endRadians).toFloat()
        val endY = center + radius * sin(endRadians).toFloat()
        val direction = if (forward) 1f else -1f
        val tangentX = -sin(endRadians).toFloat() * direction
        val tangentY = cos(endRadians).toFloat() * direction
        val head = stroke * HEAD_STROKES
        val tipX = endX + tangentX * head * HEAD_TIP
        val tipY = endY + tangentY * head * HEAD_TIP
        val baseX = endX - tangentX * head * HEAD_BASE
        val baseY = endY - tangentY * head * HEAD_BASE
        // Perpendicular to the tangent.
        val sideX = -tangentY * head * HEAD_TIP
        val sideY = tangentX * head * HEAD_TIP
        return Path().apply {
            moveTo(tipX, tipY)
            lineTo(baseX + sideX, baseY + sideY)
            lineTo(baseX - sideX, baseY - sideY)
            close()
        }
    }

    private const val STROKE = 0.085f
    private const val INSET_STROKES = 1.35f
    private const val FORWARD_START = -55f
    private const val BACK_START = 235f
    private const val SWEEP = 290f
    private const val HEAD_STROKES = 2.25f
    private const val HEAD_TIP = 0.55f
    private const val HEAD_BASE = 0.45f
    private const val NUMBER_WEIGHT = 700
    private const val NUMBER_SIZE = 0.3f
    private const val NUMBER_SIZE_LONG = 0.24f
    private const val THREE_DIGITS = 100
}
