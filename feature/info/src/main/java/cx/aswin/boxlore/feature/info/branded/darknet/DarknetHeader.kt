package cx.aswin.boxlore.feature.info.branded.darknet

import android.animation.ValueAnimator
import android.os.PowerManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.awaitPageSettled
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.rememberBrandedArt
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The flag-bearer: the show's own art, cut out of its black, in a red light. The flag waves (only
 * the cloth; the figure, pole and trident never move), the light behind him breathes, light shafts
 * sway through scratchy ink grit, and red ink flecks drift through the air. The flag carries the
 * show's name, so there is no separate wordmark. The motion runs for a few seconds whenever the
 * header comes on screen, then settles; with animations off or battery saver on it stays still.
 * Below: the host's name with the ©, the description, centred, which opens on tap, and the chips.
 */
@Composable
internal fun DarknetHeader(podcast: Podcast, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height((DarknetArt.PUBLISHER_BOTTOM - DarknetArt.STATUS_BAR).dp)) {
            InkedFlag()
            HostLine(podcast, Modifier.align(Alignment.BottomCenter))
        }
        if (description.text.isNotEmpty()) {
            Text(
                description.text,
                style = brandedSansStyle(14.5.sp, 21.sp),
                color = DarknetColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = if (description.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 28.dp, end = 28.dp, top = 6.dp)
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                    .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 18.dp else 14.dp))
        chips()
    }
}

/** "JACK RHYSIDER" in spaced capitals, the © beside it. */
@Composable
private fun HostLine(podcast: Podcast, modifier: Modifier) {
    Row(
        modifier.padding(horizontal = 28.dp).height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            podcast.artist.uppercase(Locale.getDefault()),
            style = brandedSansStyle(11.sp, 14.sp, weight = 700).copy(letterSpacing = 1.8.sp),
            color = DarknetColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        BrandedCopyrightButton(DarknetColors.Copyright, stringResource(R.string.darknet_copyright))
    }
}

@Composable
private fun InkedFlag() {
    val art by rememberBrandedArt(R.drawable.darknet_flag, R.drawable.darknet_flag_amp, R.drawable.darknet_flag_shade, R.drawable.darknet_grit)
    val rays by rememberRays()
    val motion = rememberInkMotion(ready = art != null && rays != null)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val flecks = remember { InkFleck.scatter() }
    val title = stringResource(R.string.darknet_wordmark_description)
    Spacer(
        Modifier
            .fillMaxWidth()
            .height((DarknetArt.HEIGHT - DarknetArt.STATUS_BAR).dp)
            .semantics {
                heading()
                contentDescription = title
            }
            // Its own layer: redrawing the art each frame never re-records the rest of the page.
            .graphicsLayer()
            .drawWithCache {
                val unit = 1.dp.toPx()
                val left = (size.width / unit - DarknetArt.WIDTH) / 2f * unit
                val origin = Offset(left, -DarknetArt.STATUS_BAR * unit)
                val top = -maxOf(statusBar.toPx(), DarknetArt.STATUS_BAR * unit)
                val pictures = art
                val light = rays
                val ink = if (pictures != null && light != null) InkArt(pictures, light) else null
                onDrawBehind {
                    val t = motion.clock.value
                    val rise = motion.rise.value
                    clipRect(top = top, bottom = size.height) {
                        drawRect(DarknetColors.Header, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
                        if (ink != null && rise > 0f) {
                            drawGlow(origin, unit, t, rise)
                            drawRays(ink.rays, origin, unit, t, rise)
                            drawGrit(ink.grit, origin, unit)
                            drawFlag(ink, origin, unit, t, rise)
                            drawFlecks(flecks, origin, unit, t, rise)
                        }
                        drawFades(top, unit)
                    }
                }
            },
    )
}

/** The header's decoded art and the pre-rendered light shafts. */
private class InkArt(pictures: List<ImageBitmap>, val rays: ImageBitmap) {
    val mesh = FlagMesh(pictures[0].asAndroidBitmap(), pictures[1].asAndroidBitmap())
    val shade = FlagShade(pictures[2].asAndroidBitmap())
    val grit = pictures[3]
}

/** The light shafts, drawn once off the main thread at half resolution: they are soft, so it never shows. */
@Composable
private fun rememberRays(): State<ImageBitmap?> {
    val density = LocalDensity.current.density
    return produceState<ImageBitmap?>(initialValue = DarknetRays.cached(density), density) {
        if (value == null) value = withContext(Dispatchers.Default) { DarknetRays.render(density) }
    }
}

/**
 * The art's arrival and the motion's clock in seconds. Nothing moves until the art is decoded and
 * the page has settled; then the art rises into place (the first time the page opens) and the
 * motion eases in and keeps going while the header is on screen. With animations off or battery
 * saver on it stays still.
 */
@Composable
private fun rememberInkMotion(ready: Boolean): InkMotion {
    val context = LocalContext.current
    val still = remember {
        !ValueAnimator.areAnimatorsEnabled() || context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }
    var shown by rememberSaveable { mutableStateOf(false) }
    val motion = remember { InkMotion(rise = Animatable(if (shown) 1f else 0f)) }
    LaunchedEffect(ready, still) {
        if (!ready) return@LaunchedEffect
        if (still) {
            motion.rise.snapTo(1f)
            shown = true
            return@LaunchedEffect
        }
        awaitPageSettled()
        shown = true
        launch { motion.rise.animateTo(1f, tween(RISE_MILLIS, easing = IntroEasing)) }
        delay(MOTION_AFTER_MILLIS)
        val start = withFrameNanos { it }
        // An infinite animation: tests skip it, so they never wait on it.
        while (true) {
            withInfiniteAnimationFrameNanos { now -> motion.clock.floatValue = DarknetMotion.eased((now - start) / 1_000_000_000f) }
        }
    }
    return motion
}

@Stable
private class InkMotion(val rise: Animatable<Float, *>) {
    val clock = mutableFloatStateOf(0f)
}

private const val RISE_MILLIS = 500
private const val MOTION_AFTER_MILLIS = 250L

/** The red light behind the figure, breathing; it comes up with the art. */
private fun DrawScope.drawGlow(origin: Offset, unit: Float, t: Float, rise: Float) {
    val b = DarknetMotion.breath(t)
    val centre = origin + Offset(205.2f, 244f) * unit
    val pivot = origin + Offset(195f, 220f) * unit
    scale(1f + 0.05f * b, pivot = pivot) {
        scale(scaleX = 1f, scaleY = 200f / 244.8f, pivot = centre) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFE50706),
                    0.30f to Color(0xBFC80605),
                    0.58f to Color(0x59820404),
                    0.82f to Color(0x00000000),
                    center = centre,
                    radius = 244.8f * unit,
                ),
                radius = 244.8f * unit * 1.6f,
                center = centre,
                alpha = (0.82f + 0.18f * b) * rise,
            )
        }
    }
}

/** Shafts of red light fanning out from behind him, fading with distance, swaying slowly, screened on. */
private fun DrawScope.drawRays(rays: ImageBitmap, origin: Offset, unit: Float, t: Float, rise: Float) {
    val centre = origin + DarknetRays.Centre * unit
    val pivot = origin + Offset(190f, 250f) * unit
    val reach = DarknetRays.REACH * unit
    rotate(DarknetMotion.sway(t), pivot = pivot) {
        drawImage(
            rays,
            dstOffset = IntOffset((centre.x - reach).toInt(), (centre.y - reach).toInt()),
            dstSize = IntSize((reach * 2f).toInt(), (reach * 2f).toInt()),
            alpha = rise,
            blendMode = BlendMode.Screen,
        )
    }
}

/** The illustrations' ink grit: specks and fine scratches, tiled across wide windows. */
private fun DrawScope.drawGrit(grit: ImageBitmap, origin: Offset, unit: Float) {
    val w = DarknetArt.WIDTH * unit
    var x = origin.x
    while (x > 0f) x -= w
    while (x < size.width) {
        drawImage(grit, dstOffset = IntOffset(x.toInt(), origin.y.toInt()), dstSize = IntSize(w.toInt() + 1, (DarknetArt.HEIGHT * unit).toInt()))
        x += w
    }
}

/** The flag-bearer, its cloth waving, with light and shade rippling along the cloth; it rises 8dp into place. */
private fun DrawScope.drawFlag(ink: InkArt, origin: Offset, unit: Float, t: Float, rise: Float) {
    val left = origin.x + DarknetArt.ART_LEFT * unit
    val top = origin.y + DarknetArt.ART_TOP * unit + 8f * unit * (1f - rise)
    val side = DarknetArt.ART_SIZE * unit
    val canvas = drawContext.canvas.nativeCanvas
    ink.mesh.draw(canvas, left, top, side, t, (rise * 255).toInt())
    ink.shade.draw(canvas, left, top, side, t, rise)
}

private fun DrawScope.drawFlecks(flecks: List<InkFleck>, origin: Offset, unit: Float, t: Float, rise: Float) {
    flecks.forEach { fleck ->
        val p = fleck.phase(t)
        val at = origin + Offset(fleck.x + fleck.dx * p, fleck.y + fleck.dy * p) * unit
        rotate(fleck.rotation * p, pivot = at) {
            drawOval(
                DarknetColors.Ink,
                topLeft = at - Offset(fleck.width, fleck.height) * (unit / 2f),
                size = Size(fleck.width * unit, fleck.height * unit),
                alpha = fleck.alpha(p) * rise,
            )
        }
    }
}

/** Darker under the top bar, and the art falling into the page at its foot. */
private fun DrawScope.drawFades(top: Float, unit: Float) {
    val fadeEnd = (104f - DarknetArt.STATUS_BAR) * unit
    drawRect(
        Brush.verticalGradient(0f to Color(0xE6040303), 1f to Color(0x00040303), startY = -DarknetArt.STATUS_BAR * unit, endY = fadeEnd),
        topLeft = Offset(0f, top),
        size = Size(size.width, fadeEnd - top),
    )
    val footTop = (280f - DarknetArt.STATUS_BAR) * unit
    drawRect(
        Brush.verticalGradient(0f to Color(0x000B0A0A), 0.6f to Color(0xD90B0A0A), 1f to DarknetColors.Page, startY = footTop, endY = size.height),
        topLeft = Offset(0f, footTop),
        size = Size(size.width, size.height - footTop),
    )
}

private val IntroEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)

/** The art's layout in dp on the design's 369dp board, whose top is the top of the screen under a 43dp status bar. */
internal object DarknetArt {
    const val WIDTH = 369f
    const val STATUS_BAR = 43f
    const val HEIGHT = 344f
    const val PUBLISHER_BOTTOM = 354f
    const val ART_LEFT = 16f
    const val ART_TOP = 80f
    const val ART_SIZE = 340f
}
