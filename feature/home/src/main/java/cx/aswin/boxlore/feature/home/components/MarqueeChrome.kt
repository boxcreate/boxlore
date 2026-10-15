package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.feature.home.R

/**
 * How far Home has scrolled Marquee's hero up off the screen, in pixels (Int.MAX_VALUE once it has
 * gone). The hero reads it while drawing to keep its cover still as Home's body slides over it.
 */
val LocalHeroScrollOffset = compositionLocalOf<() -> Int> { { 0 } }

/** The colour Home's floating top bar gives its wordmark and status icons over the current Marquee cover. */
@Stable
class MarqueeTopTone {
    var content by mutableStateOf(MarqueeLightContent)
}

/** Provided by Home when Marquee's hero sits under the top bar; the settled card reports its cover's tone. */
val LocalMarqueeTopTone = staticCompositionLocalOf<MarqueeTopTone?> { null }

internal val MarqueeLightContent = Color(0xFFF4F1FA)
internal val MarqueeDarkContent = Color(0xFF1B1B21)

/**
 * Light words over darker covers and dark words over light ones, judged by the cover's mean colour
 * ([average], ARGB). Unknown covers keep light words, over a stronger veil ([marqueeVeilAlpha]).
 */
internal fun marqueeTopContent(average: Int?): Color =
    if (average != null && Color(average).luminance() > MarqueeLightCover) MarqueeDarkContent else MarqueeLightContent

/**
 * How much of Home's top bar ([barHeight] px) still sits over Marquee's cover: 1 until the body's rounded
 * edge ([sheetOverlap] px above the hero's foot at [heroBottom]) comes within [edgePx] of the bar, then
 * down to 0 as the edge passes under it, and 0 once the hero has scrolled away (null).
 */
internal fun marqueeBarOverArtwork(heroBottom: Int?, sheetOverlap: Float, barHeight: Int, edgePx: Float): Float {
    if (heroBottom == null || edgePx <= 0f) return 0f
    return ((heroBottom - sheetOverlap - barHeight) / edgePx).coerceIn(0f, 1f)
}

/** How strongly the frosted band leans toward the words' opposite: a touch when the cover's tone is known. */
internal fun marqueeVeilAlpha(toneKnown: Boolean): Float = if (toneKnown) 0.12f else 0.28f

/**
 * Keeps the hero's cover still while Home scrolls: the content moves down by as much as the hero has
 * scrolled up, clipped to the hero, so Home's body and its rounded edge slide up over the cover.
 */
internal fun Modifier.marqueePinned(scroll: () -> Int): Modifier = graphicsLayer {
    translationY = scroll().toFloat().coerceIn(0f, size.height)
}

/**
 * A frosted band behind Home's floating top bar: a blurred copy of the cover's top, framed exactly like
 * the still cover beneath it ([cardHeight] tall), with a light veil toward the opposite of [content],
 * fading out just below the bar. Blur needs Android 12; older phones keep the veil.
 */
@Composable
@Suppress("LongParameterList")
internal fun MarqueeFrost(url: String?, fallbackUrls: List<String?>, filter: ColorFilter?, content: Color, veilAlpha: Float, cardHeight: Dp) {
    val topInset = LocalHeroTopInset.current
    if (topInset <= 0.dp) return
    val veil = if (content.luminance() > 0.5f) Color.Black else Color.White
    Box(
        Modifier.fillMaxWidth().height(topInset + MarqueeFrostFade).clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(veil.copy(alpha = veilAlpha))
                val solid = (topInset.toPx() / size.height).coerceIn(0f, 1f)
                drawRect(Brush.verticalGradient(0f to Color.Black, solid to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }
            .testTag("home_hero_marquee_frost"),
    ) {
        OptimizedImage(
            url = url,
            fallbackUrls = fallbackUrls,
            proxyWidth = 1080,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(cardHeight).blur(MarqueeFrostBlur),
        )
    }
}

/** Which card the hero shows: a pill for the current one and dots for the rest, light on the cover's deep shade. */
@Composable
internal fun MarqueePageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    val description = stringResource(R.string.home_hero_collection_position, current + 1, count)
    Row(
        modifier.height(MarqueeDotSize).testTag("home_hero_page_dots").semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == current) 18.dp else MarqueeDotSize, label = "marqueeDot")
            Box(
                Modifier.size(width, MarqueeDotSize).clip(CircleShape)
                    .background(MarqueeLightContent.copy(alpha = if (index == current) 1f else 0.45f)),
            )
        }
    }
}

/** Room under Play for the page dots: 14dp above them, the dots, 12dp below. */
internal val MarqueeDotsReserve = 32.dp
internal val MarqueeDotsBottom = 12.dp
private val MarqueeDotSize = 6.dp
private val MarqueeFrostFade = 28.dp
private val MarqueeFrostBlur = 24.dp
private const val MarqueeLightCover = 0.6f
