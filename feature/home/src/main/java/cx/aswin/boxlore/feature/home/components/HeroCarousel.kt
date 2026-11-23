package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic
import kotlin.math.floor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroCarousel(
    heroItems: StableHeroList,
    onPlayClick: (Podcast, android.os.Bundle?) -> Unit,
    onDetailsClick: (Podcast) -> Unit,
    onArrowClick: (SmartHeroItem, Int) -> Unit,
    onToggleSubscription: (String) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
    currentPlayingPodcastId: String? = null,
    currentPlayingEpisodeId: String? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (heroItems.list.isEmpty()) return
    val style = HeroCardStyle.sanitize(LocalHeroCardStyle.current)
    val playing = HeroPlayingState(currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying)

    val maxScrolledIndex = rememberHeroSwipeTelemetry(heroItems.list.size)
    if (style == HeroCardStyle.MARQUEE) {
        MarqueeCarousel(heroItems, playing, onPlayClick, onDetailsClick, onArrowClick, onTogglePlayback, maxScrolledIndex, modifier)
        return
    }
    val carouselState = rememberCarouselState { heroItems.list.size }

    Column(modifier.fillMaxWidth()) {
    ResponsiveHeroCarousel(
        state = carouselState,
        preferredItemWidth = HeroPreferredCard,
        itemSpacing = HeroGap,
        contentPadding = PaddingValues(0.dp),
        modifier =
        Modifier
            .fillMaxWidth()
            .height(homeHeroHeight()),
    ) { i ->
        val item = heroItems.list[i]
        val onCollectionPlayback = collectionPlaybackAction(
            item.type,
            i,
            onPlayClick,
            onTogglePlayback,
            currentPlayingPodcastId,
            currentPlayingEpisodeId,
            isPlaying,
        )

        androidx.compose.runtime.DisposableEffect(i) {
            if (i > maxScrolledIndex.intValue) maxScrolledIndex.intValue = i
            onDispose {}
        }

        when {
            HeroCardStyle.usesArtworkColors(style) ->
                FullArtworkHeroItem(style, item, i, onCollectionPlayback, onPlayClick, onDetailsClick, onArrowClick, onTogglePlayback, playing)
            item.type == cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID ||
                item.type == cx.aswin.boxlore.feature.home.HeroType.NEW_EPISODES_GRID -> {
                val resume = item.type == cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID
                BlockHeroColors(item.gridItems.firstOrNull()?.imageUrl) {
                HeroGridCard(
                    items = item.gridItems,
                    title = stringResource(if (resume) R.string.home_hero_resume_collection else R.string.home_hero_new_collection),
                    mode = if (resume) HeroGridMode.Resume else HeroGridMode.NewEpisodes,
                    onCellClick = onDetailsClick,
                    currentPlayingPodcastId = currentPlayingPodcastId,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    modifier = Modifier.maskClip(HomeHeroStyle.CardShape),
                    onPlaybackClick = onCollectionPlayback,
                    carouselItemDrawInfo = carouselItemDrawInfo,
                )
                }
            }
            else -> BlockHeroColors(item.podcast.imageUrl) {
                HeroCard(
                    item = item,
                    onClick = heroPlayAction(item, i, playing, onPlayClick, onTogglePlayback), // Primary "Play" or "Pause" button action
                    onArrowClick = { onArrowClick(item, i) },
                    onToggleSubscription = { onToggleSubscription(item.podcast.id) },
                    currentPlayingPodcastId = currentPlayingPodcastId,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    modifier = Modifier.maskClip(HomeHeroStyle.CardShape),
                )
            }
        }
    }
    // Position dots are off for now; the strip keeps the 12dp gap to Your shows either way.
    // HeroPageIndicator(count = heroItems.list.size, current = carouselState.currentItem)
    Spacer(Modifier.height(HeroIndicatorStrip))
    }
}

private fun collectionPlaybackBundle(type: cx.aswin.boxlore.feature.home.HeroType, position: Int) = android.os.Bundle().apply {
    putString("entry_point", "home_hero_${type.name.lowercase()}")
    putInt("ep_carousel_position", position)
    putString("ep_layout_type", "grid_card")
    putBoolean("ep_is_subscribed", true)
}

/** Plays the card's episode with the carousel's analytics, or pauses it when it is the one playing. */
internal fun heroPlayAction(
    item: SmartHeroItem,
    position: Int,
    playing: HeroPlayingState,
    onPlayClick: (Podcast, android.os.Bundle?) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
): () -> Unit = {
    val bundle = android.os.Bundle().apply {
        putString("entry_point", "home_hero_${item.type.name.lowercase()}")
        putInt("ep_carousel_position", position)
        putString("ep_layout_type", "full_card")
    }
    val current = HomePlaybackStateLogic.isHeroItemPlaying(
        itemEpisodeId = item.podcast.latestEpisode?.id,
        itemPodcastId = item.podcast.id,
        currentPlayingEpisodeId = playing.episodeId,
        currentPlayingPodcastId = playing.podcastId,
        isPlaying = playing.isPlaying,
    )
    if (current) onTogglePlayback(bundle) else onPlayClick(item.podcast, bundle)
}

/** Marquee's full-width hero, with the carousel's playback, details and swipe telemetry. */
@Composable
@Suppress("LongParameterList")
private fun MarqueeCarousel(
    heroItems: StableHeroList,
    playing: HeroPlayingState,
    onPlayClick: (Podcast, android.os.Bundle?) -> Unit,
    onDetailsClick: (Podcast) -> Unit,
    onArrowClick: (SmartHeroItem, Int) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
    maxScrolledIndex: androidx.compose.runtime.MutableIntState,
    modifier: Modifier,
) {
    MarqueeHero(
            heroItems = heroItems,
            playing = playing,
            playAction = { item, position -> heroPlayAction(item, position, playing, onPlayClick, onTogglePlayback) },
            collectionPlayback = { item, position ->
                collectionPlaybackAction(item.type, position, onPlayClick, onTogglePlayback, playing.podcastId, playing.episodeId, playing.isPlaying)
            },
            onDetailsClick = onDetailsClick,
            onArrowClick = onArrowClick,
            onPageShown = { page -> if (page > maxScrolledIndex.intValue) maxScrolledIndex.intValue = page },
            modifier = modifier,
        )
}

private fun collectionPlaybackAction(
    type: cx.aswin.boxlore.feature.home.HeroType,
    position: Int,
    onPlay: (Podcast, android.os.Bundle?) -> Unit,
    onToggle: (android.os.Bundle?) -> Unit,
    currentPodcastId: String?,
    currentEpisodeId: String?,
    playing: Boolean,
): (Podcast) -> Unit = { podcast ->
    val bundle = collectionPlaybackBundle(type, position)
    if (HomePlaybackStateLogic.isHeroItemPlaying(
            itemEpisodeId = podcast.latestEpisode?.id,
            itemPodcastId = podcast.id,
            currentPlayingEpisodeId = currentEpisodeId,
            currentPlayingPodcastId = currentPodcastId,
            isPlaying = playing,
        )
    ) {
        onToggle(bundle)
    } else {
        onPlay(podcast, bundle)
    }
}

internal data class HeroPlayingState(val podcastId: String?, val episodeId: String?, val isPlaying: Boolean)

/**
 * The Cover and Duotone options' cards, on the same carousel, playback and details callbacks. As on
 * the solid cards, a collection item opens its episode and the Play button on its cover plays it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun androidx.compose.material3.carousel.CarouselItemScope.FullArtworkHeroItem(
    style: String,
    item: SmartHeroItem,
    position: Int,
    onCollectionPlayback: (Podcast) -> Unit,
    onPlayClick: (Podcast, android.os.Bundle?) -> Unit,
    onDetailsClick: (Podcast) -> Unit,
    onArrowClick: (SmartHeroItem, Int) -> Unit,
    onTogglePlayback: (android.os.Bundle?) -> Unit,
    playing: HeroPlayingState,
) {
    val shape = HomeHeroStyle.CardShape
    when (item.type) {
        cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID, cx.aswin.boxlore.feature.home.HeroType.NEW_EPISODES_GRID -> {
            val resume = item.type == cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID
            val title = stringResource(if (resume) R.string.home_hero_resume_collection else R.string.home_hero_new_collection)
            val mode = if (resume) HeroGridMode.Resume else HeroGridMode.NewEpisodes
            FullArtworkGridCard(style, item.gridItems, title, mode, onDetailsClick, onCollectionPlayback, playing, carouselItemDrawInfo, Modifier.maskClip(shape))
        }
        else -> FullArtworkHeroCard(
            style = style,
            item = item,
            onClick = heroPlayAction(item, position, playing, onPlayClick, onTogglePlayback),
            onArrowClick = { onArrowClick(item, position) },
            currentPlayingPodcastId = playing.podcastId,
            currentPlayingEpisodeId = playing.episodeId,
            isPlaying = playing.isPlaying,
            modifier = Modifier.maskClip(shape),
        )
    }
}

/** Telemetry: the deepest card viewed, reported 3 s after the listener stops swiping. */
@Composable
private fun rememberHeroSwipeTelemetry(cardCount: Int): androidx.compose.runtime.MutableIntState {
    val maxScrolledIndex = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(maxScrolledIndex.intValue) {
        if (maxScrolledIndex.intValue > 0) {
            kotlinx.coroutines.delay(3000)
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeHeroCarouselSwiped(
                maxCardIndexViewed = maxScrolledIndex.intValue,
                totalCardsAvailable = cardCount,
            )
        }
    }
    return maxScrolledIndex
}

/** The artwork styles' Jump back in / New episodes card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
private fun FullArtworkGridCard(
    style: String,
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    onCellClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    playing: HeroPlayingState,
    drawInfo: androidx.compose.material3.carousel.CarouselItemDrawInfo,
    modifier: Modifier,
) {
    when (style) {
        HeroCardStyle.DUOTONE -> DuotoneHeroGridCard(
            items = items,
            title = title,
            mode = mode,
            onCellClick = onCellClick,
            onPlaybackClick = onPlaybackClick,
            currentPlayingPodcastId = playing.podcastId,
            currentPlayingEpisodeId = playing.episodeId,
            isPlaying = playing.isPlaying,
            modifier = modifier,
            carouselItemDrawInfo = drawInfo,
        )
        HeroCardStyle.HALO ->
            HaloHeroGridCard(items, title, mode, onCellClick, onPlaybackClick, playing.podcastId, playing.episodeId, playing.isPlaying, modifier)
        HeroCardStyle.TIDE ->
            TideHeroGridCard(items, title, mode, onCellClick, onPlaybackClick, playing.podcastId, playing.episodeId, playing.isPlaying, modifier)
        else -> CoverHeroGridCard(items, title, mode, onCellClick, onPlaybackClick, playing.podcastId, playing.episodeId, playing.isPlaying, modifier)
    }
}

/** The artwork styles' single-episode card. */
@Composable
@Suppress("LongParameterList")
private fun FullArtworkHeroCard(
    style: String,
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier,
) {
    when (style) {
        HeroCardStyle.DUOTONE -> DuotoneHeroCard(item, onClick, onArrowClick, currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying, modifier)
        HeroCardStyle.HALO -> HaloHeroCard(item, onClick, onArrowClick, currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying, modifier)
        HeroCardStyle.TIDE -> TideHeroCard(item, onClick, onArrowClick, currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying, modifier)
        else -> CoverHeroCard(item, onClick, onArrowClick, currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying, modifier)
    }
}

/**
 * Lays out hero cards for the window it is in, keeping one [state] across every change:
 * - Phones up to [PhoneMaxWidth] wide: material3's multi-browse carousel at [preferredItemWidth].
 * - Wider compact windows below the tablet width (the Galaxy Z Fold 8 cover, split-screen halves):
 *   multi-browse at the [heroCompactGeometry] card width, so the row shows one large card and a
 *   40-56dp peek at every width. At [preferredItemWidth], material3's keylines switched between one
 *   large card and three or four narrow ones from one width to the next (about 470-495dp wide).
 * - Tablets, and compact windows at least [TabletMinWidth] wide (sideways phones): fixed-width cards
 *   on an uncontained carousel ([heroTabletItemWidth]).
 *
 * Known material3 carousel bug (material3 1.5.0 with Compose 1.12, `HorizontalUncontainedCarousel`),
 * all at the end of the row, for 320dp cards and 16dp gaps:
 * - With no end padding it crashes where the space after the last whole card is almost a card wide
 *   (616–672, 952–1008, 1288–1344dp): its end shift distance is zero while it has two end steps.
 *   Wide rows therefore keep at least [UncontainedEndPadding] after the last card.
 * - Where one whole card fits with almost a second (about 606–672dp), the last card ends past the
 *   end edge. [heroTabletItemWidth] fits two whole cards there instead.
 * - With two or more whole cards, the last card can stop up to one card spacing short of the end
 *   edge (15.5dp at 1316dp; 1.5.0-alpha11 placed it flush). This is accepted, and
 *   `HeroCarouselEndTest` allows it while still rejecting a card past the edge.
 * Cause: material3's keyline maths is unchanged, but Compose 1.12's pager settles on a different
 * current page at the end of the row, and 1.5.0's `KeylineSnapPosition` snaps the last items to the
 * end step's last focal keyline instead of its first. Re-check on every material3 or Compose
 * upgrade: once `HeroCarouselEndTest` passes flush without these workarounds, remove them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResponsiveHeroCarousel(
    state: CarouselState,
    preferredItemWidth: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable CarouselItemScope.(Int) -> Unit,
) {
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    when (heroRowMode(LocalBoxloreWindowLayout.current)) {
        HeroRowMode.Phone ->
            HorizontalMultiBrowseCarousel(state = state, preferredItemWidth = preferredItemWidth, itemSpacing = itemSpacing, contentPadding = contentPadding, modifier = modifier, content = content)
        HeroRowMode.Compact -> BoxWithConstraints(modifier) {
            val available = maxWidth - contentPadding.calculateStartPadding(direction) - contentPadding.calculateEndPadding(direction)
            HorizontalMultiBrowseCarousel(
                state = state,
                preferredItemWidth = heroCompactGeometry(available).card,
                itemSpacing = itemSpacing,
                contentPadding = contentPadding,
                modifier = Modifier.matchParentSize(),
                content = content,
            )
        }
        HeroRowMode.Row -> {
            val padding = PaddingValues(
                start = contentPadding.calculateStartPadding(direction),
                top = contentPadding.calculateTopPadding(),
                end = maxOf(contentPadding.calculateEndPadding(direction), UncontainedEndPadding),
                bottom = contentPadding.calculateBottomPadding(),
            )
            BoxWithConstraints(modifier) {
                val available = maxWidth - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction)
                val itemWidth = heroTabletItemWidth(available, preferredItemWidth, itemSpacing)
                HorizontalUncontainedCarousel(state = state, itemWidth = itemWidth, itemSpacing = itemSpacing, contentPadding = padding, modifier = Modifier.matchParentSize(), content = content)
            }
        }
    }
}

/** How the hero row lays out its cards in a window; see [ResponsiveHeroCarousel]. */
internal enum class HeroRowMode { Phone, Compact, Row }

internal fun heroRowMode(window: cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout): HeroRowMode = when {
    window.usesTabletLayout || window.widthDp >= TabletMinWidth -> HeroRowMode.Row
    window.widthDp > PhoneMaxWidth -> HeroRowMode.Compact
    else -> HeroRowMode.Phone
}

/** The widest phone window that keeps the 320dp multi-browse hero unchanged. */
private const val PhoneMaxWidth = 430f

/** From this window width compact windows (sideways phones) show the tablet's row of whole cards. */
private const val TabletMinWidth = 600f

/** One large hero card, the gap after it and the narrow peek of the next card, filling a row. */
internal data class HeroCompactGeometry(val card: Dp, val gap: Dp, val peek: Dp)

/**
 * The hero row aims for a 320dp card after a 16dp gap and keeps the peek between 40dp and 56dp, so
 * narrow rows shrink the card and wide ones grow it. The loading placeholder uses the same widths.
 */
internal fun heroCompactGeometry(available: Dp): HeroCompactGeometry {
    val peek = (available - HeroGap - HeroPreferredCard).coerceIn(HeroMinPeek, HeroMaxPeek)
    val card = (available - HeroGap - peek).coerceAtLeast(0.dp)
    return HeroCompactGeometry(card = card, gap = HeroGap, peek = (available - card - HeroGap).coerceAtLeast(0.dp))
}

internal val HeroGap = 16.dp
internal val HeroPreferredCard = 320.dp
internal val HeroMinPeek = 40.dp
internal val HeroMaxPeek = 56.dp

/** The least end padding that keeps material3's uncontained carousel from crashing at the end of the row (see [ResponsiveHeroCarousel]). */
internal val UncontainedEndPadding = 1.dp

/**
 * The tablet card width for a row [available] wide. When one whole card fits with almost a second
 * one after it, two narrower cards fill the row instead: material3's uncontained carousel otherwise
 * leaves the last card past the end edge at the end of the row (a known material3 bug, see
 * [ResponsiveHeroCarousel]). A narrow pane (a half-open book foldable, a split view beside the
 * player) that can't show a card and at least [HeroMinPeek] of the next gets one card filling it, so
 * the card is never cut at the pane's edge and no sliver of the next one shows; material3 draws it at
 * most the row's width. Every other width keeps [preferred].
 */
internal fun heroTabletItemWidth(available: Dp, preferred: Dp, spacing: Dp): Dp {
    val slot = preferred + spacing
    if (available < slot + HeroMinPeek) return available
    val whole = floor(available / slot).toInt().coerceAtLeast(1)
    val rest = available - slot * whole
    return if (whole == 1 && rest >= slot * NearlyAnotherCard) available / 2 - spacing else preferred
}

/** How much of another card's space counts as "almost a second card" for [heroTabletItemWidth]. */
private const val NearlyAnotherCard = 0.8f
