package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.home.components.LocalHeroScrollOffset
import cx.aswin.boxlore.feature.home.components.LocalHeroTopInset
import cx.aswin.boxlore.feature.home.components.LocalMarqueeCardFrost
import cx.aswin.boxlore.feature.home.components.LocalMarqueeTopTone
import cx.aswin.boxlore.feature.home.components.MarqueeBarFrost
import cx.aswin.boxlore.feature.home.components.MarqueeSheetOverlap
import cx.aswin.boxlore.feature.home.components.MarqueeTopTone
import cx.aswin.boxlore.feature.home.components.marqueeBarOverArtwork
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * Home over Marquee: the feed runs from the top of the screen and scrolls as one piece, hero included,
 * under a floating [topBar] that stays put. The hero leaves room for the bar's height ([LocalHeroTopInset],
 * measured while [barExpanded], as the bar shrinks once Home scrolls) and fades its words as they slide
 * under it; the bar's frost blurs whatever passes beneath it and gives way to the body's colour once the
 * body's rounded edge has passed under. [topBar] gets how much of it sits over the cover, the cover's
 * tone for its words, and a modifier to place on it.
 */
@Composable
internal fun MarqueeHomeLayout(
    gridState: LazyStaggeredGridState,
    barExpanded: () -> Boolean,
    feed: @Composable () -> Unit,
    topBar: @Composable (overArtwork: () -> Float, tone: MarqueeTopTone, modifier: Modifier) -> Unit,
) {
    var topBarHeight by remember { mutableIntStateOf(0) }
    val estimate = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HomeTopBarEstimate
    val topInset = if (topBarHeight > 0) with(LocalDensity.current) { topBarHeight.toDp() } else estimate
    val topTone = remember { MarqueeTopTone() }
    val overlapPx = with(LocalDensity.current) { MarqueeSheetOverlap.toPx() }
    val edgePx = with(LocalDensity.current) { MarqueeBarEdge.toPx() }
    // How far the hero has scrolled up: its words fade under the bar, and it stops moving on once mostly gone.
    val heroScroll: () -> Int = remember(gridState) {
        { if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset else Int.MAX_VALUE }
    }
    val heroItem = { gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" } }
    // The bar stays clear over the cover, and fills with the body's colour as the body passes under it.
    val overArtwork: () -> Float = remember(gridState, overlapPx, edgePx) {
        { marqueeBarOverArtwork(heroItem()?.let { it.offset.y + it.size.height }, overlapPx, topBarHeight, edgePx) }
    }
    val marqueeFrost = remember { HazeState() }
    CompositionLocalProvider(
        LocalHeroTopInset provides topInset,
        LocalHeroScrollOffset provides heroScroll,
        LocalMarqueeTopTone provides topTone,
        LocalMarqueeCardFrost provides false,
    ) { Box(Modifier.fillMaxSize().hazeSource(marqueeFrost)) { feed() } }
    // The bar's frost blurs the live page under it, fixed to the top at the bar's full height.
    MarqueeBarFrost(marqueeFrost, topInset, topTone, overArtwork)
    topBar(overArtwork, topTone, Modifier.onSizeChanged { if (barExpanded()) topBarHeight = it.height })
}

/** The expanded bar below the status bar: 16dp above and below its 33dp shortcuts. */
private val HomeTopBarEstimate = 65.dp

/** The bar fills over this distance as the body's edge passes under it. */
private val MarqueeBarEdge = 24.dp
