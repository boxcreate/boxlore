package cx.aswin.boxlore.feature.home.components

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
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

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
        preferredItemWidth = 320.dp,
        itemSpacing = 16.dp,
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
 * Tablets show fixed-width cards on an uncontained carousel; phones keep multi-browse. With no end
 * padding, material3 crashes at the end of the row when the space after the last whole card is
 * almost a card wide (its end shift distance is zero while it has two end steps), so tablets always
 * keep at least [UncontainedEndPadding] after the last card. `HeroCarouselEndTest` covers it.
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
    if (LocalBoxloreWindowLayout.current.usesTabletLayout) {
        val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
        val padding = PaddingValues(
            start = contentPadding.calculateStartPadding(direction),
            top = contentPadding.calculateTopPadding(),
            end = maxOf(contentPadding.calculateEndPadding(direction), UncontainedEndPadding),
            bottom = contentPadding.calculateBottomPadding(),
        )
        HorizontalUncontainedCarousel(state = state, itemWidth = preferredItemWidth, itemSpacing = itemSpacing, contentPadding = padding, modifier = modifier, content = content)
    } else {
        HorizontalMultiBrowseCarousel(state = state, preferredItemWidth = preferredItemWidth, itemSpacing = itemSpacing, contentPadding = contentPadding, modifier = modifier, content = content)
    }
}

/** The least end padding that keeps material3's uncontained carousel from crashing at the end of the row. */
private val UncontainedEndPadding = 1.dp
