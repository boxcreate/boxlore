package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import kotlin.random.Random

/**
 * Marquee's background, behind its words: the cover, recognisable, in a glow of its own colours.
 *
 * - an ambient wash: the cover itself, fetched small, scaled past the card's edges, softened (blurred
 *   on Android 12 and later) and a little more saturated, then deepened lightly toward the shade from
 *   the middle down, so the card below the square cover takes the cover's colours;
 * - the cover, whole and sharp across the full width from the top, its foot fading into the wash;
 * - three lightly blurred copies of it easing in further down ([MarqueeCoverSoftening]), so the cover
 *   stays sharp at the top and softens gradually toward the words, and its lettering never fights them
 *   (without blur, before Android 12, the cover simply stays sharp);
 * - a faint grain, so the long dark gradient never bands into stripes;
 * - frosted glass under Home's floating top bar ([MarqueeTopFrost]), fading out with no edge.
 *
 * The images take the chosen colours ([MarqueeLook.filter]): as they are in Full color, two vivid
 * tones in Artwork and App theme. The words' own scrim sits on top, in the card.
 */
@Composable
internal fun BoxScope.MarqueeBackdrop(url: String?, fallbackUrls: List<String?>, look: MarqueeLook, coverTag: String? = "home_hero_artwork") {
    OptimizedImage(
        url = url,
        fallbackUrls = fallbackUrls,
        proxyWidth = MarqueeAmbientWidth,
        adaptToLayout = false,
        showLoadingPlaceholder = false,
        errorContent = {},
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = look.filter ?: MarqueeAmbientSaturation,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                scaleX = MarqueeAmbientScale
                scaleY = MarqueeAmbientScale
            }
            .blur(MarqueeAmbientBlur, BlurredEdgeTreatment.Rectangle),
    )
    val deepen = remember(look.shade.deep) {
        Brush.verticalGradient(*marqueeEasedFade(0f, 1f, 0.03f, 0.55f).map { (at, alpha) -> at to look.shade.deep.copy(alpha = alpha) }.toTypedArray())
    }
    Box(Modifier.fillMaxSize().background(deepen))
    MarqueeCoverLayer(url, fallbackUrls, look, layer = 0, tag = coverTag)
    MarqueeCoverSoftening.indices.forEach { index -> MarqueeCoverLayer(url, fallbackUrls, look, layer = index + 1, tag = null) }
    MarqueeGrain()
    // On Home the top bar blurs the live page itself (MarqueeBarFrost); previews and tests draw their own.
    if (LocalMarqueeCardFrost.current) MarqueeTopFrost(url, fallbackUrls, look)
}

/**
 * One blurred copy of Marquee's cover: blurred by [blur] and fading in between [from] and [to] (shares
 * of the cover's height, which is its width), so the copies stack into a blur that grows down the card.
 */
internal data class MarqueeSoftening(val blur: Dp, val from: Float, val to: Float)

/**
 * The cover's softening: three light steps whose fades overlap, from a touch of blur a quarter of the way
 * down to a little more by three quarters, so the blur grows smoothly with no step showing. The last is
 * whole a little before [MarqueeCoverFoot], so the copy under it is gone before it eases into the wash.
 */
internal val MarqueeCoverSoftening = listOf(
    MarqueeSoftening(3.dp, 0.24f, 0.52f),
    MarqueeSoftening(7.dp, 0.38f, 0.64f),
    MarqueeSoftening(12.dp, 0.5f, 0.74f),
)

/**
 * A fade as gradient stops, from [fromAlpha] at [from] to [toAlpha] at [to] along an S-curve (smoothstep):
 * it eases out of its start and into its end, so neither end shows as a line the way a straight ramp's do.
 */
internal fun marqueeEasedFade(from: Float, to: Float, fromAlpha: Float, toAlpha: Float, steps: Int = MarqueeFadeSteps): List<Pair<Float, Float>> =
    (0..steps).map { step ->
        val t = step / steps.toFloat()
        (from + (to - from) * t) to (fromAlpha + (toAlpha - fromAlpha) * t * t * (3f - 2f * t))
    }

/**
 * Where each copy of the cover shows, as (share of its height, alpha), for [layer] 0 (the sharp cover)
 * up to the last of [MarqueeCoverSoftening]: each blurred copy eases in over its own stretch, overlapping
 * the one before, and every copy but the last disappears just after the next one is whole, so a sharper
 * copy never shows through a blurrier one. Only the last copy eases into the wash, over the cover's last
 * fifth from [MarqueeCoverFoot], so the square's foot never shows as an edge or a line.
 */
internal fun marqueeCoverMask(layer: Int, softening: List<MarqueeSoftening> = MarqueeCoverSoftening): List<Pair<Float, Float>> {
    val own = softening.getOrNull(layer - 1)
    val start = if (own == null) listOf(0f to 1f) else listOf(0f to 0f) + marqueeEasedFade(own.from, own.to, 0f, 1f)
    val next = softening.getOrNull(layer)
    val end = if (next == null) {
        marqueeEasedFade(MarqueeCoverFoot, 1f, 1f, 0f)
    } else {
        marqueeEasedFade(next.to, (next.to + MarqueeCoverHandOff).coerceAtMost(1f), 1f, 0f) + (1f to 0f)
    }
    return start + end
}

@Composable
private fun BoxScope.MarqueeCoverLayer(url: String?, fallbackUrls: List<String?>, look: MarqueeLook, layer: Int, tag: String?) {
    val softening = MarqueeCoverSoftening.getOrNull(layer - 1)
    val mask = remember(layer) { Brush.verticalGradient(*marqueeCoverMask(layer).map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }.toTypedArray()) }
    OptimizedImage(
        url = url,
        fallbackUrls = fallbackUrls,
        proxyWidth = MarqueeCoverWidth,
        showLoadingPlaceholder = softening == null,
        errorContent = if (softening == null) null else NoErrorContent,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = look.filter,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
            .then(if (softening != null) Modifier.blur(softening.blur, BlurredEdgeTreatment.Rectangle) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

/** The blurred copies draw nothing when the cover fails to load; the sharp one shows the usual fallback. */
private val NoErrorContent: @Composable BoxScope.() -> Unit = {}

/** A faint, fixed grain over Marquee's glow, so its long dark gradient never bands into stripes. */
@Composable
private fun BoxScope.MarqueeGrain() {
    val grain = remember { marqueeGrainBrush() }
    Box(Modifier.matchParentSize().drawBehind { drawRect(grain, alpha = MarqueeGrainAlpha, blendMode = BlendMode.Overlay) })
}

/** A small tile of grey noise, the same every time, repeated across the card. */
private fun marqueeGrainBrush(): ShaderBrush {
    val random = Random(MarqueeGrainSeed)
    val pixels = IntArray(MarqueeGrainTile * MarqueeGrainTile) {
        val grey = random.nextInt(256)
        android.graphics.Color.rgb(grey, grey, grey)
    }
    val tile = Bitmap.createBitmap(pixels, MarqueeGrainTile, MarqueeGrainTile, Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/**
 * A card's own frost under a top bar, for the Settings preview and tests (on Home the bar blurs the
 * live page itself, [MarqueeBarFrost]): a blurred copy of the cover's top (framed exactly like the
 * sharp cover beneath), tinted toward black under light words or white under dark ones, solid behind
 * the bar and easing out over [MarqueeFrostReach] like the bar's. Blur needs Android 12.
 */
@Composable
internal fun MarqueeTopFrost(url: String?, fallbackUrls: List<String?>, look: MarqueeLook) {
    val topInset = LocalHeroTopInset.current
    if (topInset <= 0.dp) return
    val veil = if (look.topContent.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(topInset + MarqueeFrostReach).clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(veil.copy(alpha = look.veilAlpha))
                // Solid behind the bar, then the same short eased tail as Home's bar frost, so no edge shows.
                val stops = marqueeBarFrostStops((topInset - 20.dp).coerceAtLeast(0.dp).toPx() / size.height).map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }
                drawRect(Brush.verticalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
            }
            .testTag("home_hero_marquee_frost"),
    ) {
        OptimizedImage(
            url = url,
            fallbackUrls = fallbackUrls,
            proxyWidth = 1080,
            showLoadingPlaceholder = false,
            errorContent = {},
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = look.filter,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).aspectRatio(1f).blur(MarqueeFrostBlur),
        )
    }
}

/** Where every copy of the cover starts fading into the wash, as a share of its height; it is clear at its foot. */
internal const val MarqueeCoverFoot = 0.8f

/** Each eased fade is drawn as this many gradient steps. */
private const val MarqueeFadeSteps = 10

/** A sharper copy of the cover disappears over this share of its height once the next copy is whole. */
private const val MarqueeCoverHandOff = 0.05f
internal const val MarqueeAmbientWidth = 160

/** The sharp cover's request width; the warm-up fetches the same. */
internal const val MarqueeCoverWidth = 1080
private const val MarqueeAmbientScale = 1.35f
private val MarqueeAmbientBlur = 64.dp
private val MarqueeAmbientSaturation = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })
private const val MarqueeGrainAlpha = 0.06f
private const val MarqueeGrainTile = 96
private const val MarqueeGrainSeed = 7
private val MarqueeFrostReach = 40.dp
private val MarqueeFrostBlur = 20.dp
