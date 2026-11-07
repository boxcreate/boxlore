package cx.aswin.boxlore.feature.info.branded.history

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withTranslation
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage

/** A frame's soft shadows on the wall, sized like CSS box-shadows. */
@Immutable
internal data class FrameShadow(val color: Color, val offsetY: Dp, val blur: Dp)

/** How a bronze frame is made: its outline, moulding, mount and the shadows it casts. */
@Immutable
internal data class FrameStyle(
    val oval: Boolean,
    val moulding: Dp,
    /** The aged card between moulding and picture; 0 for a bare frame. */
    val mount: Dp,
    val shadows: List<FrameShadow>,
    /** The moulding's bevel: a dark outer line, a light line, then a brown one this wide. */
    val bevel: Dp = 3.5.dp,
)

internal val WallShadows = listOf(FrameShadow(Color(0x99140409), 11.dp, 16.dp), FrameShadow(Color(0x8C140409), 2.dp, 3.dp))
internal val CardShadows = listOf(FrameShadow(Color(0x990A0205), 10.dp, 16.dp), FrameShadow(Color(0x8C0A0205), 2.dp, 3.dp))
internal val StripShadows = listOf(FrameShadow(Color(0x990A0205), 3.dp, 5.dp))

internal val OvalShape: Shape = GenericShape { size, _ -> addOval(Rect(Offset.Zero, size)) }

/**
 * An episode's picture in an antique bronze frame: a bevelled moulding with a warm sheen, an aged
 * mount with a soft shadow inside it, and the picture cropped to fill. [dim] darkens it all
 * (0 = lit, 1 = dark) while the gallery's lights come on, read while drawing.
 */
@Composable
internal fun BronzeFrame(
    picture: String?,
    fallback: String?,
    style: FrameStyle,
    modifier: Modifier = Modifier,
    dim: () -> Float = { 0f },
    onLoaded: (() -> Unit)? = null,
) {
    val shape = if (style.oval) OvalShape else androidx.compose.ui.graphics.RectangleShape
    Box(
        modifier
            .drawWithContent {
                drawContent()
                drawSheen(style.oval)
                val d = dim()
                if (d != 0f) {
                    clipPath(outline(style.oval, Offset.Zero, size)) {
                        if (d > 0f) drawRect(Color.Black, alpha = d.coerceAtMost(1f)) else drawRect(Color.White, alpha = (-d).coerceAtMost(1f))
                    }
                }
            }
            .drawBehind { drawMoulding(style) }
            .padding(style.moulding)
            .then(
                if (style.mount > 0.dp) {
                    Modifier.clip(shape).background(HistoryColors.Mount).drawWithContent {
                drawContent()
                drawMountEdge(style.oval)
            }
                } else {
                    Modifier
                }
            )
            .padding(style.mount),
    ) {
        // No loading placeholder: an empty frame is a dark canvas until its picture arrives.
        OptimizedImage(
            url = picture,
            fallbackUrls = listOf(fallback),
            proxyWidth = 300,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(shape).background(HistoryColors.EmptyCanvas).drawWithContent {
                drawContent()
                drawPictureEdge(style.oval)
            },
            showLoadingPlaceholder = false,
            onLoaded = onLoaded,
        )
    }
}

internal fun outline(oval: Boolean, topLeft: Offset, size: Size): Path = Path().apply {
    val r = Rect(topLeft, size)
    if (oval) addOval(r) else addRect(r)
}

/** The moulding: shadows on the wall, the bronze, and its bevel. */
private fun DrawScope.drawMoulding(style: FrameStyle) {
    val path = outline(style.oval, Offset.Zero, size)
    val canvas = drawContext.canvas.nativeCanvas
    style.shadows.forEach { shadow ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HistoryColors.Bronze.first().second.toArgb()
            setShadowLayer(((shadow.blur.toPx() / 2f - 0.5f) / 0.57735f).coerceAtLeast(0.1f), 0f, shadow.offsetY.toPx(), shadow.color.toArgb())
        }
        canvas.drawPath(path.asAndroidPath(), paint)
    }
    // CSS 135deg: from the top-left corner towards the bottom-right, across the box's diagonal.
    val half = (size.width + size.height) * 0.7071f / 2f
    val dir = Offset(0.7071f, 0.7071f)
    val centre = Offset(size.width / 2f, size.height / 2f)
    drawPath(path, Brush.linearGradient(*HistoryColors.Bronze.toTypedArray(), start = centre - dir * half, end = centre + dir * half))
    val unit = 1.dp.toPx()
    bevel(style.oval, 0.5f * unit, unit, Color(0xF21C1005))
    bevel(style.oval, 1.5f * unit, unit, Color(0x52F0D7A0))
    val brown = style.bevel.toPx() - 2f * unit
    if (brown > 0f) bevel(style.oval, 2f * unit + brown / 2f, brown, Color(0x99462E12))
}

private fun DrawScope.bevel(oval: Boolean, inset: Float, width: Float, color: Color) {
    val topLeft = Offset(inset, inset)
    val s = Size(size.width - 2 * inset, size.height - 2 * inset)
    if (oval) drawOval(color, topLeft, s, style = Stroke(width)) else drawRect(color, topLeft, s, style = Stroke(width))
}

/** The mount's cut edge: a dark line, a gilt line, and a soft shadow falling inside from the top left. */
private fun DrawScope.drawMountEdge(oval: Boolean) {
    val unit = 1.dp.toPx()
    val inner = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(-20f * unit, -20f * unit, size.width + 20f * unit, size.height + 20f * unit))
        if (oval) addOval(Rect(Offset.Zero, size)) else addRect(Rect(Offset.Zero, size))
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color(0x61281406).toArgb()
        maskFilter = BlurMaskFilter(((2.5f * unit - 0.5f) / 0.57735f).coerceAtLeast(0.1f), BlurMaskFilter.Blur.NORMAL)
    }
    val canvas = drawContext.canvas.nativeCanvas
    canvas.withTranslation(2f * unit, 2f * unit) { drawPath(inner.asAndroidPath(), paint) }
    bevel(oval, 0.5f * unit, unit, Color(0xB3281906))
    bevel(oval, 1.5f * unit, unit, HistoryColors.MountLine)
}

private fun DrawScope.drawPictureEdge(oval: Boolean) {
    val unit = 1.dp.toPx()
    bevel(oval, 0.5f * unit, unit, Color(0x8C28190A))
}

/** A warm light glancing off the top left of the frame. */
private fun DrawScope.drawSheen(oval: Boolean) {
    val centre = Offset(size.width * 0.3f, size.height * 0.15f)
    val rx = size.width * 1.2f
    val ry = size.height * 0.9f
    clipPath(outline(oval, Offset.Zero, size)) {
        scale(scaleX = 1f, scaleY = ry / rx, pivot = centre) {
            drawCircle(
                Brush.radialGradient(0f to Color(0x1AFFF0D2), 0.55f to Color(0x00FFF0D2), center = centre, radius = rx),
                radius = rx * 3f,
                center = centre,
            )
        }
    }
}
