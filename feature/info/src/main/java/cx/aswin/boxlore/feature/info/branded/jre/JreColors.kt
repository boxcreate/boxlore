package cx.aswin.boxlore.feature.info.branded.jre

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedActionColors
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightStyle

/**
 * The studio: the red velvet curtain every guest sits in front of, warm light and the show's
 * orange, from the approved design boards. The studio is dark, so the page is dark only.
 */
internal object JreColors {
    val Page = Color(0xFF0C0807)
    val Ink = Color(0xFFF5EFE8)
    val Sub = Color(0xFFCBBAB0)

    /** The thumbnails' orange: the genre chip, switched-on alerts, Play and the navbar's selected tab. */
    val Orange = Color(0xFFF87000)
    val OnOrange = Color(0xFF1A0B02)
    val Control = Color(0xFF241614)
    val ChipLine = Color(0xFF4D2E27)
    val TagRing = Color(0xFF6B3A22)
    val Navigation = Color(0xFF1F1412)
    val NavigationText = Color(0xFFE9DDD5)

    /** The warm raised panel the episode's thumbnail sits in, so its black edges never melt into the page. */
    val Panel = Color(0xFF2B1D19)
    val PanelRing = Color(0xFF44302A)
    val PanelShadow = Color(0x80000000)
    val Picture = Color(0xFF160C0B)

    /** The thumbnail template's black label and its grey second half. */
    val Label = Color.Black
    val LabelMuted = Color(0xFFBDB2AB)

    /** The unwatched part of the picture's progress bar. */
    val WatchedTrack = Color(0x66FFFFFF)

    /** The curtain's folds, light to shadow, every 28dp. */
    private val Folds = listOf(
        0f to Color(0xFF3A0507),
        7f / 28 to Color(0xFF640B0F),
        12f / 28 to Color(0xFF8B161B),
        17f / 28 to Color(0xFF650C10),
        24f / 28 to Color(0xFF3A0507),
        1f to Color(0xFF290305),
    )

    /** Every 41dp a deeper shadow, so the folds never repeat evenly. */
    private val FoldShadows = listOf(0f to Color.Transparent, 19f / 41 to Color(0x47000000), 1f to Color.Transparent)

    /** The stage light behind the badge. */
    val Glow = Color(0xFFFF8246)

    /** The card's buttons, as on the episode cards below it. */
    val Actions = BrandedActionColors(toggle = Control, onToggle = Ink, checked = Orange, onChecked = OnOrange, play = Orange, onPlay = OnOrange)

    val Copyright = BrandedCopyrightStyle(
        icon = Sub,
        popup = Color(0xFF2A1A17),
        title = Ink,
        body = Color(0xFFE7D8CF),
        note = Color(0xFFB8A398),
        shadow = Color(0xA6000000),
        ring = Color.Transparent,
        popupWidth = 257.dp,
        caretCentre = 173.dp,
        gap = 6.dp,
        overhangTop = 8.dp,
        overhangBottom = 8.dp,
        iconSize = 15.dp,
    )

    /** The curtain's velvet: the folds, then the uneven shadows over them, across the whole area. */
    fun DrawScope.drawCurtain(top: Float = 0f, height: Float = size.height) {
        val unit = 1.dp.toPx()
        val area = androidx.compose.ui.geometry.Size(size.width, height)
        drawRect(
            Brush.horizontalGradient(*Folds.toTypedArray(), startX = 0f, endX = 28 * unit, tileMode = TileMode.Repeated),
            topLeft = Offset(0f, top),
            size = area,
        )
        drawRect(
            Brush.horizontalGradient(*FoldShadows.toTypedArray(), startX = 0f, endX = 41 * unit, tileMode = TileMode.Repeated),
            topLeft = Offset(0f, top),
            size = area,
        )
    }

    /** A soft oval of warm light: [radiusX] by [radiusY] around [centre], [strength] at its heart. */
    fun DrawScope.drawStageLight(centre: Offset, radiusX: Float, radiusY: Float, strength: Float, reach: Float = 0.72f) {
        scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = centre) {
            drawCircle(
                Brush.radialGradient(0f to Glow.copy(alpha = strength), reach to Glow.copy(alpha = 0f), center = centre, radius = radiusX),
                radius = radiusX,
                center = centre,
            )
        }
    }
}
