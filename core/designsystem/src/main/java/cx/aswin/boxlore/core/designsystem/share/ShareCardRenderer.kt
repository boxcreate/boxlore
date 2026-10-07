@file:Suppress("LongParameterList", "TooManyFunctions", "kotlin:S107")

package cx.aswin.boxlore.core.designsystem.share

import android.content.Context
import cx.aswin.boxlore.core.model.ShareTarget

internal data class ShareBrandingLayout(
    val contentGap: Float,
    val labelSize: Float,
    val logoWidth: Int,
    val inline: Boolean,
)

internal fun shareBrandingLayout(isStory: Boolean): ShareBrandingLayout = if (isStory) {
    ShareBrandingLayout(
        contentGap = 80f,
        labelSize = 36f,
        logoWidth = 280,
        inline = false,
    )
} else {
    ShareBrandingLayout(
        contentGap = 48f,
        labelSize = 40f,
        logoWidth = 280,
        inline = true,
    )
}

internal fun shareBrandingTop(isStory: Boolean, contentBottom: Float, brandingHeight: Float): Float {
    val preferredTop = if (isStory) 1_440f else 1_200f - 96f - brandingHeight
    return maxOf(preferredTop, contentBottom + shareBrandingLayout(isStory).contentGap)
}

/** Bitmap card composition for [ShareManager] share sheets / Instagram stories. */
internal object ShareCardRenderer {
    fun createShareCard(
        context: Context,
        artwork: android.graphics.Bitmap,
        title: String,
        subtitle: String,
        target: ShareTarget,
    ): android.graphics.Bitmap {
        val isStory = target == ShareTarget.INSTAGRAM_STORY
        val width = if (isStory) 1080 else 1200
        val height = if (isStory) 1920 else 1200
        val output =
            android.graphics.Bitmap.createBitmap(
                width,
                height,
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.parseColor("#2E2DD0"))
        drawShareCardShapes(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            isStory = isStory,
        )

        val artworkSize = if (isStory) 760f else 680f
        val artworkTop = if (isStory) 290f else 55f
        val artworkRect =
            android.graphics.RectF(
                (width - artworkSize) / 2f,
                artworkTop,
                (width + artworkSize) / 2f,
                artworkTop + artworkSize,
            )
        drawRoundedArtwork(
            canvas = canvas,
            artwork = artwork,
            destination = artworkRect,
            cornerRadius = 72f,
        )

        val brandingLayout = shareBrandingLayout(isStory)
        if (isStory) {
            val textBottom =
                drawShareText(
                    context = context,
                    canvas = canvas,
                    canvasWidth = width,
                    title = title,
                    subtitle = subtitle,
                    titleTop = 1_110f,
                    titleWidth = 860,
                    titleSize = 60f,
                    subtitleSize = 44f,
                    blockSpacing = 13f,
                )
            drawShareBranding(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                contentBottom = textBottom,
                isStory = true,
                layout = brandingLayout,
            )
        } else {
            val textBottom =
                drawShareText(
                    context = context,
                    canvas = canvas,
                    canvasWidth = width,
                    title = title,
                    subtitle = subtitle,
                    titleTop = 785f,
                    titleWidth = 960,
                    titleSize = 48f,
                    subtitleSize = 36f,
                    blockSpacing = 8f,
                )
            drawShareBranding(
                context = context,
                canvas = canvas,
                canvasWidth = width,
                contentBottom = textBottom,
                isStory = false,
                layout = brandingLayout,
            )
        }

        return output
    }

    @Suppress("CyclomaticComplexMethod")
    private fun drawShareCardShapes(canvas: android.graphics.Canvas, width: Float, height: Float, isStory: Boolean,) {
        val paint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#5B5BDF")
                alpha = 185
                style = android.graphics.Paint.Style.FILL
            }

        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.Puffy,
            left = width - if (isStory) 345f else 315f,
            top = if (isStory) -145f else -165f,
            size = if (isStory) 500f else 455f,
            rotation = 14f,
            paint = paint,
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.PuffyDiamond,
            left = if (isStory) -195f else -155f,
            top = if (isStory) 710f else height * 0.35f,
            size = if (isStory) 390f else 320f,
            rotation = -18f,
            paint = paint.apply { alpha = 155 },
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.Cookie4,
            left = if (isStory) -120f else -100f,
            top = if (isStory) 35f else -20f,
            size = if (isStory) 390f else 325f,
            rotation = -24f,
            paint = paint.apply { alpha = 130 },
        )
        drawExpressiveShape(
            canvas = canvas,
            type = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType.SoftBurst,
            left = width - if (isStory) 230f else 195f,
            top = if (isStory) 710f else 530f,
            size = if (isStory) 430f else 370f,
            rotation = 20f,
            paint = paint.apply { alpha = 180 },
        )
    }

    private fun drawExpressiveShape(
        canvas: android.graphics.Canvas,
        type: cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.DecorativeType,
        left: Float,
        top: Float,
        size: Float,
        rotation: Float,
        paint: android.graphics.Paint,
    ) {
        val path =
            cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.androidPath(
                type = type,
                width = size,
                height = size,
            )
        canvas.save()
        canvas.translate(left, top)
        canvas.rotate(rotation, size / 2f, size / 2f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawShareBranding(
        context: Context,
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        contentBottom: Float,
        isStory: Boolean,
        layout: ShareBrandingLayout,
    ) {
        val textPaint =
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = layout.labelSize
                typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL)
                textAlign = if (layout.inline) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.CENTER
            }
        val label = context.getString(cx.aswin.boxlore.core.designsystem.R.string.share_listen_on)
        val labelHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
        val logoHeight = layout.logoWidth / cx.aswin.boxlore.core.designsystem.components.BoxLoreLogoAspectRatio
        val brandingHeight = if (layout.inline) maxOf(labelHeight, logoHeight) else labelHeight + 12f + logoHeight
        val brandingTop = shareBrandingTop(isStory, contentBottom, brandingHeight)
        val labelWidth = textPaint.measureText(label)
        val rowLeft = (canvasWidth - labelWidth - 18f - layout.logoWidth) / 2f
        val labelX = if (layout.inline) rowLeft else canvasWidth / 2f
        val labelTop = if (layout.inline) brandingTop + (brandingHeight - labelHeight) / 2f else brandingTop
        canvas.drawText(label, labelX, labelTop - textPaint.fontMetrics.ascent, textPaint)

        val logoTop =
            if (layout.inline) {
                brandingTop + (brandingHeight - logoHeight) / 2f
            } else {
                brandingTop + labelHeight + 12f
            }
        val logoLeft = if (layout.inline) rowLeft + labelWidth + 18f else (canvasWidth - layout.logoWidth) / 2f
        androidx.core.content.ContextCompat
            .getDrawable(
                context,
                cx.aswin.boxlore.core.designsystem.R.drawable.ic_boxlore_logo,
            )?.mutate()
            ?.apply {
                setTint(android.graphics.Color.WHITE)
                setBounds(
                    logoLeft.toInt(),
                    logoTop.toInt(),
                    (logoLeft + layout.logoWidth).toInt(),
                    (logoTop + logoHeight).toInt(),
                )
                draw(canvas)
            }
    }

    private fun drawShareText(
        context: Context,
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        title: String,
        subtitle: String,
        titleTop: Float,
        titleWidth: Int,
        titleSize: Float,
        subtitleSize: Float,
        blockSpacing: Float,
    ): Float {
        val titleHeight =
            drawCenteredTextBlock(
                canvas = canvas,
                canvasWidth = canvasWidth,
                text = title,
                top = titleTop,
                width = titleWidth,
                textSize = titleSize,
                maxLines = 2,
                color = android.graphics.Color.WHITE,
                typeface = googleSansTypeface(context, android.graphics.Typeface.BOLD),
            )
        var nextTop = titleTop + titleHeight
        if (subtitle.isNotBlank()) {
            val bySize = subtitleSize * 0.78f
            val byTop = nextTop + blockSpacing
            val byHeight =
                drawCenteredTextBlock(
                    canvas = canvas,
                    canvasWidth = canvasWidth,
                    text = "by",
                    top = byTop,
                    width = titleWidth,
                    textSize = bySize,
                    maxLines = 1,
                    color = android.graphics.Color.parseColor("#BEBEE8"),
                    typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL),
                )
            val subtitleTop = byTop + byHeight + blockSpacing * 0.5f
            val subtitleHeight =
                drawCenteredTextBlock(
                    canvas = canvas,
                    canvasWidth = canvasWidth,
                    text = subtitle,
                    top = subtitleTop,
                    width = titleWidth,
                    textSize = subtitleSize,
                    maxLines = 1,
                    color = android.graphics.Color.parseColor("#DCDCF8"),
                    typeface = googleSansTypeface(context, android.graphics.Typeface.NORMAL),
                )
            nextTop = subtitleTop + subtitleHeight
        }
        return nextTop
    }

    private fun drawCenteredTextBlock(
        canvas: android.graphics.Canvas,
        canvasWidth: Int,
        text: String,
        top: Float,
        width: Int,
        textSize: Float,
        maxLines: Int,
        color: Int,
        typeface: android.graphics.Typeface,
    ): Int {
        val textPaint =
            android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                this.textSize = textSize
                this.typeface = typeface
            }
        val layout =
            android.text.StaticLayout.Builder
                .obtain(
                    text,
                    0,
                    text.length,
                    textPaint,
                    width,
                ).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setLineSpacing(0f, 1.02f)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()

        canvas.save()
        canvas.translate((canvasWidth - width) / 2f, top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    private fun googleSansTypeface(context: Context, style: Int,): android.graphics.Typeface = cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces.create(
        context = context,
        style = style,
        roundness =
        cx.aswin.boxlore.core.designsystem.theme.GoogleSansTypefaces
            .cachedRoundness(context),
    )

    private fun drawRoundedArtwork(
        canvas: android.graphics.Canvas,
        artwork: android.graphics.Bitmap,
        destination: android.graphics.RectF,
        cornerRadius: Float,
    ) {
        val sourceSide = minOf(artwork.width, artwork.height)
        val sourceLeft = (artwork.width - sourceSide) / 2
        val sourceTop = (artwork.height - sourceSide) / 2
        val source =
            android.graphics.Rect(
                sourceLeft,
                sourceTop,
                sourceLeft + sourceSide,
                sourceTop + sourceSide,
            )
        val clipPath =
            android.graphics.Path().apply {
                addRoundRect(
                    destination,
                    cornerRadius,
                    cornerRadius,
                    android.graphics.Path.Direction.CW,
                )
            }
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.drawBitmap(
            artwork,
            source,
            destination,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
            },
        )
        canvas.restore()

        canvas.drawRoundRect(
            destination,
            cornerRadius,
            cornerRadius,
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(52, 255, 255, 255)
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f
            },
        )
    }
}
