@file:Suppress("TooManyFunctions")

package cx.aswin.boxlore.core.designsystem.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogoAspectRatio
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces
import cx.aswin.boxlore.core.model.ShareTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Draws the share card: the show or episode as a boxlore player, in colours taken from its artwork.
 * The artwork is shown whole with rounded corners; nothing is cut into a shape.
 */
internal object ShareCardRenderer {
    /**
     * [seed] is the artwork's colour, or null for boxlore indigo; [progress] places the slider's
     * thumb (0–1). [scale] draws a smaller copy, for the share sheet's preview.
     */
    @Suppress("LongParameterList")
    fun createShareCard(
        context: Context,
        artwork: Bitmap,
        seed: Int?,
        text: ShareCardText,
        progress: Float,
        target: ShareTarget,
        scale: Float = 1f,
    ): Bitmap {
        val layout = shareCardLayout(target)
        val colors = shareCardColors(seed)
        val output = createBitmap(
            (layout.width * scale).toInt().coerceAtLeast(1),
            (layout.height * scale).toInt().coerceAtLeast(1),
        )
        val canvas = Canvas(output)
        canvas.scale(scale, scale)
        canvas.drawColor(colors.ground)
        drawPanel(canvas, layout.card, top = layout.cardTopRadius, bottom = layout.joinRadius, color = colors.card)
        drawArtwork(canvas, artwork, layout.artwork, layout.artworkRadius)
        drawText(context, canvas, layout, colors, text)
        drawSlider(canvas, layout.slider, progress, colors)
        drawPlayButton(canvas, layout.play, colors)
        drawPanel(canvas, layout.bar, top = layout.joinRadius, bottom = layout.barBottomRadius, color = colors.accent)
        drawBranding(context, canvas, layout, colors)
        return output
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** A rounded panel with different top and bottom corners. */
    private fun drawPanel(canvas: Canvas, box: ShareBox, top: Float, bottom: Float, color: Int) {
        val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        val path = Path().apply { addRoundRect(box.rect(), radii, Path.Direction.CW) }
        canvas.drawPath(path, paint(color))
    }

    private fun drawArtwork(canvas: Canvas, artwork: Bitmap, box: ShareBox, radius: Float) {
        val side = minOf(artwork.width, artwork.height)
        val source = Rect((artwork.width - side) / 2, (artwork.height - side) / 2, (artwork.width + side) / 2, (artwork.height + side) / 2)
        val destination = box.rect()
        canvas.withClip(Path().apply { addRoundRect(destination, radius, radius, Path.Direction.CW) }) {
            drawBitmap(artwork, source, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        // A faint light edge keeps dark covers from melting into the card.
        val edge = paint(android.graphics.Color.argb(46, 255, 255, 255)).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(RectF(destination).apply { inset(1f, 1f) }, radius - 1f, radius - 1f, edge)
    }

    private fun drawText(context: Context, canvas: Canvas, layout: ShareCardLayout, colors: ShareCardColors, text: ShareCardText) {
        val box = layout.text
        val width = box.width.toInt()
        val subtitle = text.subtitle.takeIf(String::isNotBlank)?.let {
            textLayout(it, width, layout.subtitleSize, typeface(context, Typeface.NORMAL), colors.subtitle, maxLines = 1)
        }
        val moment = text.moment?.let {
            textLayout(it, width, layout.momentSize, typeface(context, Typeface.BOLD), colors.accent, maxLines = 1)
        }
        val subtitleGap = layout.subtitleSize * 0.4f
        val momentGap = layout.momentSize * 0.6f
        val belowTitle = (subtitle?.let { it.height + subtitleGap } ?: 0f) + (moment?.let { it.height + momentGap } ?: 0f)
        val titleRoom = box.height - belowTitle
        val bold = typeface(context, Typeface.BOLD)

        fun titleAt(size: Float): StaticLayout {
            // A line is the font's ascent plus descent (about 1.2× its size), times the spacing.
            val lineHeight = size * LineHeightFactor * TitleLineSpacing
            val lines = floor(titleRoom / lineHeight).toInt().coerceIn(1, layout.titleMaxLines)
            return textLayout(text.title, width, size, bold, colors.title, maxLines = lines, spacing = TitleLineSpacing)
        }
        val size = fitTitleSize(layout.titleSizes) { candidate ->
            val laidOut = titleAt(candidate)
            laidOut.getEllipsisCount(laidOut.lineCount - 1) == 0 && laidOut.height <= titleRoom
        }
        val title = titleAt(size)
        val blockHeight = title.height + belowTitle
        var y = if (layout.textBeside) box.top + (box.height - blockHeight) / 2f else box.top
        y = drawLayout(canvas, title, box.left, y)
        subtitle?.let { y = drawLayout(canvas, it, box.left, y + subtitleGap) }
        moment?.let { drawLayout(canvas, it, box.left, y + momentGap) }
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, left: Float, top: Float): Float {
        canvas.withTranslation(left, top) { layout.draw(this) }
        return top + layout.height
    }

    @Suppress("LongParameterList")
    private fun textLayout(text: String, width: Int, size: Float, typeface: Typeface, color: Int, maxLines: Int, spacing: Float = 1.15f): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.typeface = typeface
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(0f, spacing)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    /** The player's slider: a wavy played part, a tall thumb, then a flat track. */
    private fun drawSlider(canvas: Canvas, box: ShareBox, progress: Float, colors: ShareCardColors) {
        val centre = box.top + box.height / 2f
        val thumbX = box.left + box.width * progress.coerceIn(0f, 1f)
        val gap = 14f
        val waveEnd = thumbX - gap
        if (waveEnd > box.left + WaveHalfPeriod) {
            val wave = Path().apply {
                moveTo(box.left, centre)
                var x = box.left
                while (x < waveEnd) {
                    x = minOf(x + 3f, waveEnd)
                    lineTo(x, centre - WaveAmplitude * sin((x - box.left) / WaveHalfPeriod * PI.toFloat()))
                }
            }
            canvas.drawPath(
                wave,
                paint(colors.subtitle).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            )
        }
        canvas.drawRoundRect(RectF(thumbX - 6f, box.top, thumbX + 6f, box.bottom), 6f, 6f, paint(colors.thumb))
        val trackStart = thumbX + 6f + gap
        if (trackStart < box.right) {
            canvas.drawRoundRect(RectF(trackStart, centre - 7f, box.right, centre + 7f), 7f, 7f, paint(colors.track))
        }
    }

    /** The scalloped play button the player uses, with a rounded play triangle. */
    private fun drawPlayButton(canvas: Canvas, box: ShareBox, colors: ShareCardColors) {
        val cx = box.left + box.width / 2f
        val cy = box.top + box.height / 2f
        val radius = box.width / 2f
        val scallop = Path()
        val points = 240
        for (i in 0..points) {
            val angle = i.toDouble() / points * 2 * PI
            val r = radius * (1 - ScallopDepth + ScallopDepth * cos(ScallopLobes * angle)).toFloat()
            val x = cx + r * cos(angle).toFloat()
            val y = cy + r * sin(angle).toFloat()
            if (i == 0) scallop.moveTo(x, y) else scallop.lineTo(x, y)
        }
        scallop.close()
        canvas.drawPath(scallop, paint(colors.accent))
        val side = box.width * 0.34f
        val left = cx - side * 0.4f
        val triangle = Path().apply {
            moveTo(left, cy - side / 2f)
            lineTo(left + side * 0.9f, cy)
            lineTo(left, cy + side / 2f)
            close()
        }
        canvas.drawPath(
            triangle,
            paint(colors.onAccent).apply {
            pathEffect = android.graphics.CornerPathEffect(side * 0.14f)
        }
        )
    }

    private fun drawBranding(context: Context, canvas: Canvas, layout: ShareCardLayout, colors: ShareCardColors) {
        val label = context.getString(R.string.share_listen_on)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.onAccent
            textSize = layout.labelSize
            typeface = typeface(context, Typeface.NORMAL)
        }
        val gap = layout.labelSize * 0.45f
        val labelWidth = textPaint.measureText(label)
        val logoHeight = layout.logoWidth / BoxLoreLogoAspectRatio
        val bar = layout.bar
        val left = bar.left + (bar.width - labelWidth - gap - layout.logoWidth) / 2f
        val centre = bar.top + bar.height / 2f
        val metrics = textPaint.fontMetrics
        canvas.drawText(label, left, centre - (metrics.ascent + metrics.descent) / 2f, textPaint)
        ContextCompat.getDrawable(context, R.drawable.ic_boxlore_logo)?.mutate()?.apply {
            setTint(colors.onAccent)
            val logoLeft = (left + labelWidth + gap).toInt()
            val logoTop = (centre - logoHeight / 2f).toInt()
            setBounds(logoLeft, logoTop, (logoLeft + layout.logoWidth).toInt(), (logoTop + logoHeight).toInt())
            draw(canvas)
        }
    }

    private fun typeface(context: Context, style: Int): Typeface =
        GoogleSansTypefaces.create(context = context, style = style, roundness = GoogleSansTypefaces.cachedRoundness(context))

    private fun ShareBox.rect() = RectF(left, top, right, bottom)

    private const val TitleLineSpacing = 1.06f
    private const val LineHeightFactor = 1.2f
    private const val WaveAmplitude = 13f
    private const val WaveHalfPeriod = 24f
    private const val ScallopLobes = 12
    private const val ScallopDepth = 0.035
}
