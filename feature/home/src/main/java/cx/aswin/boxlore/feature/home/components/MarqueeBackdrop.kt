package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage

/**
 * Marquee's background, behind its words. Three layers make the card glow in the cover's own colours
 * instead of cropping a square cover into a tall card:
 *
 * - an ambient wash: the cover itself, fetched tiny, scaled past the card's edges and softened (blurred
 *   on Android 12 and later; elsewhere the tiny image's own upscaling keeps it soft), then deepened
 *   toward the shade from the middle down, so the whole card takes the cover's colours;
 * - the cover, whole and sharp across the full width from the top, its lower part melting into the
 *   wash ([MarqueeCoverMelt]), so nothing of the cover is cropped away;
 * - frosted glass under Home's floating top bar ([MarqueeTopFrost]), fading out with no edge.
 *
 * Both images take the chosen colours ([MarqueeLook.filter]): as they are in Full color, two vivid
 * tones in Artwork and App theme, as bright as the cover itself. The words' own scrim sits on top, in the card.
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
        colorFilter = look.filter,
        modifier = Modifier.fillMaxSize()
            .graphicsLayer {
                scaleX = MarqueeAmbientScale
                scaleY = MarqueeAmbientScale
            }
            .blur(MarqueeAmbientBlur, BlurredEdgeTreatment.Rectangle),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to look.shade.deep.copy(alpha = 0.18f),
                0.5f to look.shade.deep.copy(alpha = 0.42f),
                1f to look.shade.deep.copy(alpha = 0.82f),
            ),
        ),
    )
    OptimizedImage(
        url = url,
        fallbackUrls = fallbackUrls,
        proxyWidth = MarqueeCoverWidth,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = look.filter,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(1f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Black, MarqueeCoverMelt to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .then(if (coverTag != null) Modifier.testTag(coverTag) else Modifier),
    )
    // On Home the top bar blurs the live page itself (MarqueeBarFrost); previews and tests draw their own.
    if (LocalMarqueeCardFrost.current) MarqueeTopFrost(url, fallbackUrls, look)
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

/** Where the cover starts melting into the wash, as a share of its height; it is clear at its foot. */
internal const val MarqueeCoverMelt = 0.58f
internal const val MarqueeAmbientWidth = 96

/** The sharp cover's request width; the warm-up fetches the same. */
internal const val MarqueeCoverWidth = 1080
private const val MarqueeAmbientScale = 1.35f
private val MarqueeAmbientBlur = 56.dp
private val MarqueeFrostReach = 40.dp
private val MarqueeFrostBlur = 20.dp
