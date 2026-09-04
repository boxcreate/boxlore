package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces

/**
 * Rasterizes widget labels with the exact Google Sans Flex ROND selected in Appearance.
 *
 * Launcher hosts are inconsistent about applying variable-font settings from RemoteViews.
 * A bitmap makes the typeface, line count, and clipping deterministic across launchers.
 */
object WidgetTextBitmapRenderer {
    data class Spec(
        val text: String,
        val widthDp: Int,
        val heightDp: Int,
        val preferredSizeSp: Float,
        val minSizeSp: Float,
        val weight: Int,
        val maxLines: Int,
        val alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    )

    fun render(
        context: Context,
        spec: Spec,
        @ColorRes colorRes: Int,
    ): Bitmap = renderColor(
        context = context,
        spec = spec,
        color = ContextCompat.getColor(context, colorRes),
    )

    fun renderColor(
        context: Context,
        spec: Spec,
        @ColorInt color: Int,
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val scaledDensity = density * context.resources.configuration.fontScale
        val widthPx = (spec.widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (spec.heightDp * density).toInt().coerceAtLeast(1)
        val paint =
            TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                this.color = color
                typeface =
                    GoogleSansTypefaces.create(
                        context = context,
                        weight = spec.weight,
                        roundness = GoogleSansTypefaces.cachedRoundness(context),
                    )
            }

        var low = spec.minSizeSp
        var high = spec.preferredSizeSp.coerceAtLeast(spec.minSizeSp)
        var best = spec.minSizeSp
        repeat(FIT_ITERATIONS) {
            val candidate = (low + high) / 2f
            paint.textSize = candidate * scaledDensity
            val layout = buildLayout(spec.text, paint, widthPx, spec.maxLines, spec.alignment)
            if (layout.height <= heightPx && !isEllipsized(layout)) {
                best = candidate
                low = candidate
            } else {
                high = candidate
            }
        }

        paint.textSize = best * scaledDensity
        val layout = buildLayout(spec.text, paint, widthPx, spec.maxLines, spec.alignment)
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.translate(0f, ((heightPx - layout.height) / 2f).coerceAtLeast(0f))
            layout.draw(canvas)
        }
    }

    private fun buildLayout(
        text: String,
        paint: TextPaint,
        widthPx: Int,
        maxLines: Int,
        alignment: Layout.Alignment,
    ): StaticLayout = StaticLayout
        .Builder
        .obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(0f, 1f)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    private fun isEllipsized(layout: StaticLayout): Boolean = (0 until layout.lineCount).any { line ->
        layout.getEllipsisCount(line) > 0
    }

    private const val FIT_ITERATIONS = 8
}
