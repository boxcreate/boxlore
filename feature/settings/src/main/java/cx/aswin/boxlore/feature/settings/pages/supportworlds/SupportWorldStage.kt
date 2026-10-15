package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal val SupportStageHeight = 330.dp

/**
 * The hero area: a world's codename behind its platform, a floating tier object and particles.
 * Purely decorative for accessibility; the tier name and impact are announced below it.
 */
@Composable
internal fun SupportWorldStage(
    skin: SupportWorldSkin,
    selected: Int,
    accent: Color,
    reducedMotion: Boolean,
    signalLost: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = !reducedMotion
    val float by rememberSupportLoop(motion, durationMillis = 3600, label = "support_world_float")
    val pulse by rememberSupportLoop(motion, durationMillis = 2700, label = "support_world_pulse")
    val drift by rememberSupportLoop(
        motion,
        durationMillis = 9000,
        label = "support_world_drift",
        reverse = false,
        easing = LinearEasing,
    )
    val flicker by rememberSupportLoop(
        motion && signalLost,
        durationMillis = 1600,
        label = "support_world_flicker",
        reverse = false,
        easing = LinearEasing,
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(SupportStageHeight)
            .clearAndSetSemantics { },
    ) {
        StageCodename(skin, selected, accent, maxWidth, pulse, flicker, signalLost)
        StageDecor(skin, accent, drift, pulse)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (skin.objectBottom - 22.dp).coerceAtLeast(0.dp))
                .size(width = 200.dp, height = 44.dp)
                .graphicsLayer {
                    val scale = 0.9f + 0.18f * pulse
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.6f + 0.4f * pulse
                }
                .blur(22.dp, BlurredEdgeTreatment.Unbounded)
                .background(accent.copy(alpha = 0.55f), RoundedCornerShape(50)),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = skin.objectBottom)
                .size(skin.objectSize)
                .graphicsLayer {
                    translationY = -12.dp.toPx() * float
                    rotationZ = if (skin.stage == SupportStageStyle.OrbitRings) -2f + 3.5f * float else 0f
                },
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(initialScale = 0.6f, animationSpec = spring(0.55f, Spring.StiffnessMediumLow)))
                        .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.6f))
                },
                label = "support_world_object",
            ) { index ->
                Image(
                    painter = painterResource(skin.tierArt[index]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (motion) {
            Canvas(Modifier.fillMaxSize()) { drawParticles(skin.particles, accent, drift) }
        }
    }
}

@Composable
private fun StageCodename(
    skin: SupportWorldSkin,
    selected: Int,
    accent: Color,
    stageWidth: Dp,
    pulse: Float,
    flicker: Float,
    signalLost: Boolean,
) {
    val density = LocalDensity.current
    val glow = Shadow(color = accent, blurRadius = 44f)
    when (skin.codename) {
        SupportCodenameStyle.SlotCode -> {
            val alpha = if (signalLost) neonFlickerAlpha(flicker) else 0.5f + 0.12f * pulse
            Text(
                text = skin.levelLabel(selected),
                style = TextStyle(
                    fontFamily = skin.display.family,
                    fontWeight = skin.display.weight,
                    fontSize = with(density) { 172.dp.toSp() },
                    textAlign = TextAlign.Center,
                    shadow = glow,
                ),
                color = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .graphicsLayer { this.alpha = alpha },
            )
        }
        SupportCodenameStyle.SplitName -> Crossfade(targetState = selected, label = "support_world_codename") { index ->
            val words = skin.tierNames[index].uppercase().split(" ")
            val first = words.first()
            val second = words.drop(1).joinToString(" ")
            Box(Modifier.fillMaxSize()) {
                CodenameLine(first, skin, accent, glow, stageWidth, Modifier.padding(start = 14.dp, top = 16.dp))
                if (second.isNotEmpty()) {
                    CodenameLine(
                        second,
                        skin,
                        accent,
                        glow,
                        stageWidth,
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 14.dp, top = 166.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CodenameLine(
    word: String,
    skin: SupportWorldSkin,
    accent: Color,
    glow: Shadow,
    stageWidth: Dp,
    modifier: Modifier,
) {
    val sizeDp = codenameSizeDp(word.length, stageWidth.value, skin.displayWidthFactor)
    Text(
        text = word,
        style = TextStyle(
            fontFamily = skin.display.family,
            fontWeight = skin.display.weight,
            fontSize = with(LocalDensity.current) { sizeDp.dp.toSp() },
            shadow = glow,
        ),
        color = accent,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** Fits a codename to the stage the way the mockups did, never larger than 66dp. */
internal fun codenameSizeDp(length: Int, stageWidthDp: Float, widthFactor: Float): Float =
    min(66f, (stageWidthDp - 28f) / (length.coerceAtLeast(1) * widthFactor))

/** Off-beats of a failing neon tube, as in the mockup's flicker keyframes. */
internal fun neonFlickerAlpha(progress: Float): Float = when (progress) {
    in 0.19f..0.21f, in 0.23f..0.24f, in 0.54f..0.56f -> 0.08f
    else -> 0.62f
}

@Composable
private fun StageDecor(skin: SupportWorldSkin, accent: Color, drift: Float, pulse: Float) {
    when (skin.stage) {
        SupportStageStyle.NeonShelf -> {
            Canvas(Modifier.fillMaxSize()) { drawLightCone(Color.White, alpha = 0.18f) }
            skin.stageProp?.let { prop ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Image(
                        painter = painterResource(prop),
                        contentDescription = null,
                        modifier = Modifier
                            .size(240.dp)
                            .offset(y = 20.dp),
                    )
                }
            }
        }
        SupportStageStyle.OrbitRings -> Canvas(Modifier.fillMaxSize()) { drawOrbitRings(accent, drift) }
        SupportStageStyle.RuneCircle -> Canvas(Modifier.fillMaxSize()) { drawRuneCircle(accent, drift) }
        SupportStageStyle.LightShaft -> Canvas(Modifier.fillMaxSize()) {
            drawLightCone(Color(0xFFAAE6FF), alpha = 0.14f + 0.08f * pulse)
        }
        SupportStageStyle.CloudBank -> Canvas(Modifier.fillMaxSize()) { drawCloudBank(drift) }
    }
}

private fun DrawScope.drawLightCone(color: Color, alpha: Float) {
    val topHalf = size.width * 0.09f
    val bottomHalf = size.width * 0.34f
    val cx = size.width / 2f
    val path = Path().apply {
        moveTo(cx - topHalf, -30.dp.toPx())
        lineTo(cx + topHalf, -30.dp.toPx())
        lineTo(cx + bottomHalf, size.height)
        lineTo(cx - bottomHalf, size.height)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.9f,
        ),
    )
}

private fun DrawScope.drawOrbitRings(accent: Color, drift: Float) {
    val center = Offset(size.width / 2f, size.height - 60.dp.toPx())
    val outer = Size(320.dp.toPx(), 78.dp.toPx())
    drawOval(
        color = accent.copy(alpha = 0.28f),
        topLeft = center - Offset(outer.width / 2f, outer.height / 2f),
        size = outer,
        style = Stroke(1.5.dp.toPx()),
    )
    val inner = Size(240.dp.toPx(), 58.dp.toPx())
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
    drawCircle(accent.copy(alpha = 0.3f), radius = 11.dp.toPx(), center = dot)
    drawCircle(accent, radius = 5.dp.toPx(), center = dot)
}

private fun DrawScope.drawRuneCircle(accent: Color, drift: Float) {
    val center = Offset(size.width / 2f, size.height - 56.dp.toPx())
    scale(scaleX = 1f, scaleY = 0.31f, pivot = center) {
        rotate(degrees = drift * 360f, pivot = center) {
            listOf(Stroke(7.dp.toPx()) to 0.18f, Stroke(1.6.dp.toPx()) to 0.85f).forEach { (stroke, alpha) ->
                val color = accent.copy(alpha = alpha)
                drawCircle(color, radius = 124.dp.toPx(), center = center, style = stroke)
                drawCircle(color, radius = 104.dp.toPx(), center = center, style = stroke)
                drawCircle(color, radius = 40.dp.toPx(), center = center, style = stroke)
                drawPath(hexagramTriangle(center, 96.dp.toPx(), pointingUp = true), color, style = stroke)
                drawPath(hexagramTriangle(center, 96.dp.toPx(), pointingUp = false), color, style = stroke)
                for (tick in 0 until 12) {
                    val theta = tick * PI.toFloat() / 6f
                    val from = Offset(center.x + 106.dp.toPx() * sin(theta), center.y - 106.dp.toPx() * cos(theta))
                    val to = Offset(center.x + 122.dp.toPx() * sin(theta), center.y - 122.dp.toPx() * cos(theta))
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
    val baseY = size.height - 24.dp.toPx()
    val puffs = listOf(
        Triple(-110f, 0f, 0.55f),
        Triple(-20f, -10f, 0.6f),
        Triple(90f, 2f, 0.5f),
        Triple(-60f, 18f, 0.42f),
        Triple(60f, 22f, 0.4f),
    )
    puffs.forEachIndexed { index, (dx, dy, alpha) ->
        val offsetX = if (index % 2 == 0) sway else -sway
        val center = Offset(size.width / 2f + dx.dp.toPx() + offsetX, baseY + dy.dp.toPx())
        val radius = (66 - index * 4).dp.toPx()
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
                val progress = (drift * 2.4f + phase) % 1f
                val y = size.height - 60.dp.toPx() - progress * 140.dp.toPx()
                val alpha = fadeInOut(progress)
                val color = if (style == SupportParticleStyle.RisingEmbers && index % 2 == 1) Color(0xFFFFD9A0) else tint
                drawCircle(color.copy(alpha = alpha * 0.35f), radius = 6.dp.toPx(), center = Offset(x + progress * 10.dp.toPx(), y))
                drawCircle(color.copy(alpha = alpha), radius = 2.dp.toPx(), center = Offset(x + progress * 10.dp.toPx(), y))
            }
            SupportParticleStyle.Bubbles -> {
                val progress = (drift * 2f + phase) % 1f
                val y = size.height - 40.dp.toPx() - progress * 170.dp.toPx()
                val sway = sin((progress + phase) * 2f * PI.toFloat()) * 6.dp.toPx()
                drawCircle(
                    Color(0xFFC8F0FF).copy(alpha = fadeInOut(progress) * 0.7f),
                    radius = (3 + index % 3 * 1.5f).dp.toPx(),
                    center = Offset(x + sway, y),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            SupportParticleStyle.TwinklingStars, SupportParticleStyle.Sparkles -> {
                val alpha = 0.15f + 0.85f * abs(sin(((drift * 3f) + phase) * PI.toFloat()))
                val y = size.height * (0.08f + 0.7f * ((phase * 7.3f) % 1f))
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
