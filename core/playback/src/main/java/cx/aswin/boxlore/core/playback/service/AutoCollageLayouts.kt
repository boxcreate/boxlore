package cx.aswin.boxlore.core.playback.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/** Layout helpers for [AutoCollageGenerator] Android Auto folder tiles. */
internal object AutoCollageLayouts {
    fun draw(
        canvas: Canvas,
        size: Int,
        folderId: String,
        bitmaps: List<Bitmap>,
    ) {
        when (bitmaps.size) {
            0 -> drawBrandedFallback(canvas, size, folderId)
            1 -> drawSingleCover(canvas, size, bitmaps[0])
            2 -> drawTwoSplit(canvas, size, bitmaps)
            3 -> drawThreeLayout(canvas, size, bitmaps)
            else -> drawFourGrid(canvas, size, bitmaps)
        }
        when {
            folderId.contains("drive_mix") -> drawLabelBadge(canvas, size, "MIX")
            folderId.contains("continue") -> drawLabelBadge(canvas, size, "RESUME")
        }
    }

    private fun drawLabelBadge(
        canvas: Canvas,
        size: Int,
        label: String,
    ) {
        val padding = size * 0.04f
        val badgeWidth = size * if (label.length > 3) 0.42f else 0.28f
        val badgeHeight = size * 0.14f
        val badge =
            RectF(
                size - badgeWidth - padding,
                size - badgeHeight - padding,
                size - padding,
                size - padding,
            )
        canvas.drawRoundRect(
            badge,
            size * 0.035f,
            size * 0.035f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#D91A1A2E")
            },
        )
        val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size * 0.065f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
        val centerY = badge.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, badge.centerX(), centerY, textPaint)
    }

    private fun drawSingleCover(
        canvas: Canvas,
        size: Int,
        bmp: Bitmap,
    ) {
        drawCenterCrop(canvas, bmp, Rect(0, 0, size, size))
    }

    private fun drawTwoSplit(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val gap = 3
        for (i in 0..1) {
            val left = if (i == 0) 0 else halfW + gap
            drawCenterCrop(canvas, bitmaps[i], Rect(left, 0, size.takeIf { i == 1 } ?: halfW, size))
        }
    }

    private fun drawThreeLayout(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3
        drawCenterCrop(canvas, bitmaps[0], Rect(0, 0, halfW, size))
        drawCenterCrop(canvas, bitmaps[1], Rect(halfW + gap, 0, size, halfH))
        drawCenterCrop(canvas, bitmaps[2], Rect(halfW + gap, halfH + gap, size, size))
    }

    private fun drawFourGrid(
        canvas: Canvas,
        size: Int,
        bitmaps: List<Bitmap>,
    ) {
        val halfW = size / 2
        val halfH = size / 2
        val gap = 3
        val positions =
            listOf(
                0f to 0f,
                (halfW + gap).toFloat() to 0f,
                0f to (halfH + gap).toFloat(),
                (halfW + gap).toFloat() to (halfH + gap).toFloat(),
            )
        for (i in 0 until minOf(4, bitmaps.size)) {
            val (x, y) = positions[i]
            drawCenterCrop(
                canvas,
                bitmaps[i],
                Rect(x.toInt(), y.toInt(), (x + halfW).toInt(), (y + halfH).toInt()),
            )
        }
    }

    private fun drawBrandedFallback(
        canvas: Canvas,
        size: Int,
        folderId: String,
    ) {
        val gradient =
            LinearGradient(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                Color.parseColor("#1a1a2e"),
                Color.parseColor("#16213e"),
                Shader.TileMode.CLAMP,
            )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), Paint().apply { shader = gradient })
        val label =
            when {
                folderId.contains("continue") -> "PLAY"
                folderId.contains("download") -> "OFFLINE"
                folderId.contains("drive_mix") -> "MIX"
                folderId.contains("time_picks") -> "NOW"
                folderId.contains("genres") -> "GENRES"
                folderId.contains("discover") -> "DISCOVER"
                folderId.contains("library") -> "LIBRARY"
                else -> "BOXLORE"
            }
        val textPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = size * 0.1f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
        canvas.drawText(label, size / 2f, size / 2f + textPaint.textSize / 3f, textPaint)
    }

    private fun drawCenterCrop(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: Rect,
    ) {
        val sourceRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val destinationRatio = destination.width().toFloat() / destination.height().toFloat()
        val source =
            if (sourceRatio > destinationRatio) {
                val width = (bitmap.height * destinationRatio).toInt()
                val left = (bitmap.width - width) / 2
                Rect(left, 0, left + width, bitmap.height)
            } else {
                val height = (bitmap.width / destinationRatio).toInt()
                val top = (bitmap.height - height) / 2
                Rect(0, top, bitmap.width, top + height)
            }
        canvas.drawBitmap(
            bitmap,
            source,
            destination,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
    }
}
