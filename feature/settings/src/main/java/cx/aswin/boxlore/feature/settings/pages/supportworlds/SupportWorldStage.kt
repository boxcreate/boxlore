package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * What a world puts under its floating tier on the Support stage, in place of the power cells'
 * transforming platform: a glow, the world's platform, an optional prop and ambient particles.
 * [drift] runs 0→1 on a loop and [pulse] breathes around 1.
 */
@Composable
internal fun SupportWorldPlatform(
    skin: SupportWorldSkin,
    accent: Color,
    drift: Float,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) { drawPlatformGlow(accent, pulse) }
        when (skin.stage) {
            SupportStageStyle.NeonShelf -> {
                Canvas(Modifier.fillMaxSize()) { drawLightCone(Color.White, alpha = 0.16f) }
                skin.stageProp?.let { prop ->
                    Image(
                        painter = painterResource(prop),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(160.dp)
                            .offset(y = 6.dp),
                    )
                }
            }
            SupportStageStyle.OrbitRings -> Canvas(Modifier.fillMaxSize()) { drawOrbitRings(accent, drift) }
            SupportStageStyle.RuneCircle -> Canvas(Modifier.fillMaxSize()) { drawRuneCircle(accent, drift) }
            SupportStageStyle.LightShaft -> Canvas(Modifier.fillMaxSize()) {
                drawLightCone(Color(0xFFAAE6FF), alpha = 0.1f + 0.6f * (pulse - 0.94f))
            }
            SupportStageStyle.CloudBank -> Canvas(Modifier.fillMaxSize()) { drawCloudBank(drift) }
        }
        Canvas(Modifier.fillMaxSize()) { drawParticles(skin.particles, accent, drift) }
    }
}

/** The world's painted scene behind the whole page, tinted towards the selected tier. */
@Composable
internal fun SupportWorldBackdrop(skin: SupportWorldSkin, accent: Color) {
    val background = skin.palette.background
    Box(Modifier.fillMaxSize()) {
        val fixedHeight = skin.backdropHeight
        Image(
            painter = painterResource(skin.backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = if (fixedHeight != null) Modifier.fillMaxWidth().height(fixedHeight) else Modifier.fillMaxSize(),
        )
        if (fixedHeight != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .background(Brush.verticalGradient(0.78f to Color.Transparent, 1f to background)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.15f),
                            0.38f to Color.Transparent,
                            0.66f to background.copy(alpha = 0.72f),
                            1f to background.copy(alpha = 0.94f),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(480.dp)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height * 0.4f)
                    val radius = size.height * 0.44f
                    scale(scaleX = 1.3f, scaleY = 1f, pivot = center) {
                        drawCircle(
                            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.38f), Color.Transparent), center, radius),
                            radius = radius,
                            center = center,
                        )
                    }
                },
        )
    }
}

/** The payments-off notice in a world's own words, art and palette. */
internal fun SupportWorldSkin.noticeContent(selected: Int, accent: Color): SupportNoticeContent = SupportNoticeContent(
    art = tierArt[selected],
    accent = accent,
    surface = palette.dialogSurface,
    content = palette.content,
    muted = palette.muted,
    scrim = palette.scrim,
    chip = notice.chip,
    chipColor = notice.chipColor,
    chipFont = label,
    title = notice.title,
    titleFont = name,
    body = notice.body(SUPPORT_OFFERS[selected].priceLabel),
    dismiss = notice.dismiss,
    badge = notice.badge,
    badgeContainer = notice.badgeContainer,
    badgeContent = notice.badgeContent,
)

private fun DrawScope.drawPlatformGlow(accent: Color, pulse: Float) {
    val center = Offset(size.width / 2f, size.height - 20.dp.toPx())
    val radius = 100.dp.toPx() * pulse
    scale(scaleX = 1f, scaleY = 0.24f, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.drawLightCone(color: Color, alpha: Float) {
    val topHalf = size.width * 0.09f
    val bottomHalf = size.width * 0.3f
    val cx = size.width / 2f
    val path = Path().apply {
        moveTo(cx - topHalf, -40.dp.toPx())
        lineTo(cx + topHalf, -40.dp.toPx())
        lineTo(cx + bottomHalf, size.height)
        lineTo(cx - bottomHalf, size.height)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha.coerceIn(0f, 1f)), Color.Transparent),
            startY = 0f,
            endY = size.height,
        ),
    )
}

private fun DrawScope.drawOrbitRings(accent: Color, drift: Float) {
    val center = Offset(size.width / 2f, size.height - 22.dp.toPx())
    val outer = Size(280.dp.toPx(), 64.dp.toPx())
    drawOval(
        color = accent.copy(alpha = 0.28f),
        topLeft = center - Offset(outer.width / 2f, outer.height / 2f),
        size = outer,
        style = Stroke(1.5.dp.toPx()),
    )
    val inner = Size(210.dp.toPx(), 46.dp.toPx())
    drawOval(
        color = accent.copy(alpha = 0.75f),
        topLeft = center - Offset(inner.width / 2f, inner.height / 2f),
        size = inner,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()), phase = -drift * 680.dp.toPx()),
        ),
    )
    val angle = drift * 2f * PI.toFloat()
    val dot = Offset(center.x + inner.width / 2f * cos(angle), center.y + inner.height / 2f * sin(angle))
    drawCircle(accent.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = dot)
    drawCircle(accent, radius = 4.5.dp.toPx(), center = dot)
}

private fun DrawScope.drawRuneCircle(accent: Color, drift: Float) {
    val center = Offset(size.width / 2f, size.height - 20.dp.toPx())
    scale(scaleX = 1f, scaleY = 0.31f, pivot = center) {
        rotate(degrees = drift * 360f, pivot = center) {
            listOf(Stroke(6.dp.toPx()) to 0.18f, Stroke(1.5.dp.toPx()) to 0.85f).forEach { (stroke, alpha) ->
                val color = accent.copy(alpha = alpha)
                drawCircle(color, radius = 104.dp.toPx(), center = center, style = stroke)
                drawCircle(color, radius = 88.dp.toPx(), center = center, style = stroke)
                drawCircle(color, radius = 34.dp.toPx(), center = center, style = stroke)
                drawPath(hexagramTriangle(center, 80.dp.toPx(), pointingUp = true), color, style = stroke)
                drawPath(hexagramTriangle(center, 80.dp.toPx(), pointingUp = false), color, style = stroke)
                for (tick in 0 until 12) {
                    val theta = tick * PI.toFloat() / 6f
                    val from = Offset(center.x + 90.dp.toPx() * sin(theta), center.y - 90.dp.toPx() * cos(theta))
                    val to = Offset(center.x + 102.dp.toPx() * sin(theta), center.y - 102.dp.toPx() * cos(theta))
                    drawLine(color, from, to, strokeWidth = stroke.width)
                }
            }
        }
    }
}

private fun hexagramTriangle(center: Offset, radius: Float, pointingUp: Boolean): Path {
    val sign = if (pointingUp) -1f else 1f
    val half = radius * 0.866f
    return Path().apply {
        moveTo(center.x, center.y + sign * radius)
        lineTo(center.x + half, center.y - sign * radius / 2f)
        lineTo(center.x - half, center.y - sign * radius / 2f)
        close()
    }
}

private fun DrawScope.drawCloudBank(drift: Float) {
    val sway = sin(drift * 2f * PI.toFloat()) * 10.dp.toPx()
    val baseY = size.height - 14.dp.toPx()
    val puffs = listOf(
        Triple(-96f, 0f, 0.55f),
        Triple(-18f, -8f, 0.6f),
        Triple(78f, 2f, 0.5f),
        Triple(-52f, 14f, 0.42f),
        Triple(52f, 18f, 0.4f),
    )
    puffs.forEachIndexed { index, (dx, dy, alpha) ->
        val offsetX = if (index % 2 == 0) sway else -sway
        val center = Offset(size.width / 2f + dx.dp.toPx() + offsetX, baseY + dy.dp.toPx())
        val radius = (56 - index * 4).dp.toPx()
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFEFE6FF).copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            topLeft = Offset(center.x - radius, center.y - radius * 0.42f),
            size = Size(radius * 2f, radius * 0.84f),
        )
    }
}

private val ParticleSeeds = listOf(
    0.24f to 0.0f,
    0.34f to 0.31f,
    0.48f to 0.62f,
    0.6f to 0.14f,
    0.72f to 0.47f,
    0.18f to 0.78f,
    0.8f to 0.9f,
)

private fun DrawScope.drawParticles(style: SupportParticleStyle, accent: Color, drift: Float) {
    ParticleSeeds.forEachIndexed { index, (xFraction, phase) ->
        val x = size.width * xFraction
        val tint = if (index % 2 == 0) accent else Color.White
        when (style) {
            SupportParticleStyle.RisingSparks, SupportParticleStyle.RisingEmbers -> {
                val progress = (drift * 3f + phase) % 1f
                val y = size.height - 30.dp.toPx() - progress * 140.dp.toPx()
                val alpha = fadeInOut(progress)
                val color = if (style == SupportParticleStyle.RisingEmbers && index % 2 == 1) Color(0xFFFFD9A0) else tint
                drawCircle(color.copy(alpha = alpha * 0.35f), radius = 6.dp.toPx(), center = Offset(x + progress * 10.dp.toPx(), y))
                drawCircle(color.copy(alpha = alpha), radius = 2.dp.toPx(), center = Offset(x + progress * 10.dp.toPx(), y))
            }
            SupportParticleStyle.Bubbles -> {
                val progress = (drift * 2.5f + phase) % 1f
                val y = size.height - 20.dp.toPx() - progress * 160.dp.toPx()
                val sway = sin((progress + phase) * 2f * PI.toFloat()) * 6.dp.toPx()
                drawCircle(
                    Color(0xFFC8F0FF).copy(alpha = fadeInOut(progress) * 0.7f),
                    radius = (3 + index % 3 * 1.5f).dp.toPx(),
                    center = Offset(x + sway, y),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            SupportParticleStyle.TwinklingStars, SupportParticleStyle.Sparkles -> {
                val alpha = 0.15f + 0.85f * abs(sin(((drift * 4f) + phase) * PI.toFloat()))
                val y = size.height * (0.05f + 0.75f * ((phase * 7.3f) % 1f))
                if (style == SupportParticleStyle.Sparkles) {
                    rotate(45f, pivot = Offset(x, y)) {
                        drawRect(tint.copy(alpha = alpha), topLeft = Offset(x - 2.5.dp.toPx(), y - 2.5.dp.toPx()), size = Size(5.dp.toPx(), 5.dp.toPx()))
                    }
                } else {
                    drawCircle(Color.White.copy(alpha = alpha), radius = 1.4.dp.toPx(), center = Offset(x, y))
                }
            }
        }
    }
}

private fun fadeInOut(progress: Float): Float = when {
    progress < 0.15f -> progress / 0.15f
    progress > 0.8f -> (1f - progress) / 0.2f
    else -> 1f
}
