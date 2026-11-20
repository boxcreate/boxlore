package cx.aswin.boxlore.feature.home.components

import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem

/**
 * How far Home has scrolled Marquee's hero up off the screen, in pixels (Int.MAX_VALUE once it has
 * gone). The hero reads it while drawing, to fade its words as they slide under Home's top bar, and to
 * stop moving on by itself once it is mostly scrolled away.
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
 * How much of Home's top bar ([barHeight] px) sits over Marquee's cover: 1 while the body's rounded edge
 * ([sheetOverlap] px above the hero's foot at [heroBottom]) is at or below the bar, then down to 0 over
 * [edgePx] as the edge scrolls under the bar, and 0 once the hero has scrolled away (null).
 */
internal fun marqueeBarOverArtwork(heroBottom: Int?, sheetOverlap: Float, barHeight: Int, edgePx: Float): Float {
    if (heroBottom == null || edgePx <= 0f) return 0f
    return ((heroBottom - sheetOverlap - barHeight + edgePx) / edgePx).coerceIn(0f, 1f)
}

/** How strongly the frost under the top bar leans toward the words' opposite: stronger when the cover's tone is unknown. */
internal fun marqueeVeilAlpha(toneKnown: Boolean): Float = if (toneKnown) 0.16f else 0.3f

/**
 * The words' scrim over Marquee's backdrop: the card's deep shade, light enough that the cover stays in
 * view behind the words and dark enough for them to read.
 */
internal const val MarqueeWordsScrim = 0.72f
internal val MarqueeWordsFade = 150.dp
private val MarqueeWordsSolid = 40.dp

/**
 * Fades the backdrop above this block of Marquee's words into [color] at [MarqueeWordsScrim]: clear
 * [MarqueeWordsFade] above the block, full [MarqueeWordsSolid] into it, along an S-curve so the shade
 * never shows a line where it starts or settles. Draws outside the block's bounds; the card clips it.
 */
internal fun Modifier.marqueeWordsScrim(color: Color): Modifier = drawBehind {
    val fade = MarqueeWordsFade.toPx()
    val total = fade + size.height
    if (total <= 0f) return@drawBehind
    val settled = ((fade + MarqueeWordsSolid.toPx()) / total).coerceAtMost(1f)
    val stops = marqueeEasedFade(0f, settled, 0f, MarqueeWordsScrim).map { (at, alpha) -> at to color.copy(alpha = alpha) } + (1f to color.copy(alpha = MarqueeWordsScrim))
    drawRect(Brush.verticalGradient(*stops.toTypedArray(), startY = -fade, endY = size.height), topLeft = Offset(0f, -fade), size = Size(size.width, total))
}

/** The top of Home's body over Marquee's hero: the sheet's rounded edge, which scrolls with the page. */
@Composable
internal fun MarqueeSheetEdge(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(MarqueeSheetOverlap).background(MaterialTheme.colorScheme.surface, MarqueeSheetShape).testTag("home_hero_sheet_edge"))
}

/**
 * Where a part of Marquee's words is fully faded and where fully shown, down its own height in px, as
 * Home scrolls it under the floating top bar: [barBottom] is the bar's foot on screen, [scroll] how far
 * the hero has scrolled up and [top] where the part starts in the hero. It fades over [fade] px above
 * the bar's foot, so words thin out as they slide under the bar instead of being cut by its edge. Null
 * while none of it is under the bar, or once the hero has gone, so it draws as it is.
 */
internal fun marqueeFadeUnderBar(barBottom: Float, scroll: Int, top: Float, fade: Float): Pair<Float, Float>? {
    if (scroll <= 0 || scroll == Int.MAX_VALUE) return null
    val shown = barBottom + scroll - top
    return if (shown <= 0f) null else (shown - fade) to shown
}

/**
 * Fades this part of Marquee's words as Home scrolls it under the floating top bar
 * ([marqueeFadeUnderBar]). Reads the scroll and its own position only while drawing, so scrolling never
 * recomposes the card, and a move without a scroll still redraws the fade.
 */
@Composable
internal fun Modifier.marqueeFadesUnderBar(): Modifier {
    val scroll = LocalHeroScrollOffset.current
    val density = LocalDensity.current
    val barBottom = with(density) { LocalHeroTopInset.current.toPx() }
    val fade = with(density) { MarqueeWordsUnderBarFade.toPx() }
    // State, so a move without a scroll (a resize, a different card height) redraws the fade too.
    val top = remember { mutableFloatStateOf(0f) }
    return this
        .onPlaced { top.floatValue = it.positionInParent().y }
        .graphicsLayer {
            val fading = marqueeFadeUnderBar(barBottom, scroll(), top.floatValue, fade) != null
            compositingStrategy = if (fading) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            val band = marqueeFadeUnderBar(barBottom, scroll(), top.floatValue, fade) ?: return@drawWithContent
            val stops = marqueeEasedFade(0f, 1f, 0f, 1f).map { (at, alpha) -> at to Color.Black.copy(alpha = alpha) }
            drawRect(Brush.verticalGradient(*stops.toTypedArray(), startY = band.first, endY = band.second), blendMode = BlendMode.DstIn)
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

/** Marquee moves on its own (the hero between cards, the collection strip between episodes); tests turn it off. */
val LocalMarqueeAutoAdvance = staticCompositionLocalOf { true }

/**
 * Whether this phone suits content that moves on its own: not with TalkBack exploring by touch, and
 * not with animations turned off in system settings.
 */
@Composable
internal fun rememberMotionAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        val animations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        accessibility?.isTouchExplorationEnabled != true && animations > 0f
    }
}

/**
 * Whether the hero should move to its next card: there is one, motion suits the phone, nobody is
 * touching or dragging it, and most of it is still on screen.
 */
internal fun marqueeShouldRotate(count: Int, allowed: Boolean, touched: Boolean, onScreen: Boolean): Boolean =
    count > 1 && allowed && !touched && onScreen

/** How long the hero stays on a card: 6 seconds, or long enough for a collection's strip to show every episode. */
internal fun marqueeDwellMillis(item: SmartHeroItem): Long = when (item.type) {
    HeroType.RESUME_GRID, HeroType.NEW_EPISODES_GRID -> (MarqueeStripStepMillis * item.gridItems.take(4).size).coerceAtLeast(MarqueeCardMillis)
    else -> MarqueeCardMillis
}

/**
 * Reports a finger anywhere on the hero ([onPress]) and its lifting ([onRelease]), watching on the way
 * down without taking the touch, so swipes, scrolls and taps still reach what is under the finger.
 */
internal fun Modifier.marqueeTouches(onPress: () -> Unit, onRelease: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPress()
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            onRelease()
        }
    }
}

/** The collection strip moves to its next episode this often. */
internal const val MarqueeStripStepMillis = 5_000L
private const val MarqueeCardMillis = 6_000L

/** Room under Play for the page dots: 14dp above them, the dots, 12dp below. */
internal val MarqueeDotsReserve = 32.dp
internal val MarqueeDotsBottom = 12.dp
private val MarqueeDotSize = 6.dp

/** Words fade out over this much as they slide under Home's top bar. */
private val MarqueeWordsUnderBarFade = 56.dp
private const val MarqueeLightCover = 0.6f
