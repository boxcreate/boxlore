package cx.aswin.boxlore.feature.info.branded.crimejunkie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel

/**
 * Flashlight: the lights go out on the Subscribe button, a torch beam sweeps across it and back
 * with a slight hand wobble, finds Crime Junkie's ink splatter with one tap, then opens up to fill
 * the button as the lights come on, and the button settles on Subscribed. About 1.35 seconds.
 */
internal object CrimeJunkieFlashlight : BrandedSubscribeMoment {
    override val durationMillis = CrimeJunkieTorch.DURATION_MILLIS
    override val revealDelayMillis = CrimeJunkieTorch.REVEAL_MILLIS

    override fun nudge(elapsedMillis: Float) = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, CrimeJunkieTorch.FOUND_MILLIS)
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                if (CrimeJunkieTorch.dark(elapsedMillis())) drawRect(CrimeJunkieColors.Darkness)
            }
            // The beam's own layer, so its soft edge takes away only the lit patch and the ink.
            Canvas(
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = CrimeJunkieTorch.beamAlpha(elapsedMillis())
                    compositingStrategy = CompositingStrategy.Offscreen
                },
            ) { drawBeam(elapsedMillis(), style.subscribedContainer) }
            Canvas(Modifier.fillMaxSize()) { drawGlow(elapsedMillis()) }
            Canvas(Modifier.fillMaxSize()) {
                if (CrimeJunkieTorch.settled(elapsedMillis())) drawRect(style.subscribedContainer)
            }
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = 1f - CrimeJunkieTorch.subscribeLabelOut(elapsedMillis()) },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val (landAlpha, landScale) = CrimeJunkieTorch.subscribedLabel(elapsedMillis())
                    alpha = landAlpha
                    scaleX = landScale
                    scaleY = landScale
                },
            )
        }
    }
}

private fun DrawScope.drawBeam(millis: Float, subscribed: Color) {
    val (point, radiusDp) = CrimeJunkieTorch.beam(millis)
    if (radiusDp <= 0f) return
    val unit = 1.dp.toPx()
    drawRect(lerp(CrimeJunkieColors.Lit, subscribed, CrimeJunkieTorch.lightsOn(millis)))
    drawInk(millis, unit)
    val centre = Offset(point.x * size.width, point.y * size.height)
    drawRect(Brush.radialGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent, center = centre, radius = radiusDp * unit), blendMode = BlendMode.DstIn)
}

private fun DrawScope.drawGlow(millis: Float) {
    val alpha = CrimeJunkieTorch.glowAlpha(millis)
    val (point, radiusDp) = CrimeJunkieTorch.beam(millis)
    if (alpha <= 0f || radiusDp <= 0f) return
    val beam = CrimeJunkieColors.Beam
    drawRect(
        Brush.radialGradient(
            0f to beam.copy(alpha = 0.30f),
            0.55f to beam.copy(alpha = 0.10f),
            1f to beam.copy(alpha = 0f),
            center = Offset(point.x * size.width, point.y * size.height),
            radius = radiusDp * 1.dp.toPx(),
        ),
        alpha = alpha,
    )
}

/** The splatter at 44% across and halfway down, popping when found and fading as the lights come on. */
private fun DrawScope.drawInk(millis: Float, unit: Float) {
    val alpha = CrimeJunkieTorch.inkAlpha(millis)
    if (alpha <= 0f) return
    val centre = Offset(CrimeJunkieTorch.InkCentre.x * size.width, CrimeJunkieTorch.InkCentre.y * size.height)
    scale(CrimeJunkieTorch.inkScale(millis), pivot = centre) {
        val blob = Path()
        CrimeJunkieTorch.InkOutline.forEachIndexed { i, p ->
            val x = centre.x + p.x * unit
            val y = centre.y + p.y * unit
            if (i == 0) blob.moveTo(x, y) else blob.lineTo(x, y)
        }
        blob.close()
        val bounds = blob.getBounds()
        drawPath(
            blob,
            Brush.linearGradient(
                0f to CrimeJunkieColors.SplatMagenta,
                0.55f to CrimeJunkieColors.SplatViolet,
                1f to CrimeJunkieColors.SplatBlue,
                start = bounds.topLeft,
                end = bounds.bottomRight,
            ),
            alpha = alpha,
        )
        CrimeJunkieTorch.Droplets.forEach { (at, radius, tone) ->
            drawCircle(DropletColors[tone], radius = radius * unit, center = centre + at * unit, alpha = alpha)
        }
    }
}

/** The droplets in brighter tones of the splatter's magenta, violet and blue. */
private val DropletColors = listOf(Color(0xFFE040F0), Color(0xFF8A3CF0), Color(0xFF4058E8))
