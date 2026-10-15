package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Podcast
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
    val cover = LocalCoverHeroCards.current
    val playing = HeroPlayingState(currentPlayingPodcastId, currentPlayingEpisodeId, isPlaying)

    val carouselState = rememberCarouselState { heroItems.list.size }

    val maxScrolledIndex = rememberHeroSwipeTelemetry(heroItems.list.size)

    Column(modifier.fillMaxWidth()) {
    HorizontalMultiBrowseCarousel(
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
            if (i > maxScrolledIndex.intValue) {
                maxScrolledIndex.intValue = i
            }
            onDispose {}
        }

        when {
            cover -> CoverHeroItem(item, i, onCollectionPlayback, onPlayClick, onDetailsClick, onArrowClick, onTogglePlayback, playing)
            item.type == cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID -> {
                HeroGridCard(
                    items = item.gridItems,
                    title = stringResource(R.string.home_hero_resume_collection),
                    mode = HeroGridMode.Resume,
                    onCellClick = onDetailsClick,
                    currentPlayingPodcastId = currentPlayingPodcastId,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    modifier = Modifier.maskClip(HomeHeroStyle.CardShape),
                    onPlaybackClick = onCollectionPlayback,
                    carouselItemDrawInfo = carouselItemDrawInfo,
                )
            }
            item.type == cx.aswin.boxlore.feature.home.HeroType.NEW_EPISODES_GRID -> {
                HeroGridCard(
                    items = item.gridItems,
                    title = stringResource(R.string.home_hero_new_collection),
                    mode = HeroGridMode.NewEpisodes,
                    onCellClick = onDetailsClick,
                    currentPlayingPodcastId = currentPlayingPodcastId,
                    currentPlayingEpisodeId = currentPlayingEpisodeId,
                    isPlaying = isPlaying,
                    modifier = Modifier.maskClip(HomeHeroStyle.CardShape),
                    onPlaybackClick = onCollectionPlayback,
                    carouselItemDrawInfo = carouselItemDrawInfo,
                )
            }
            else -> {
                HeroCard(
                    item = item,
                    onClick = {
                        val bundle =
                            android.os.Bundle().apply {
                                putString("entry_point", "home_hero_${item.type.name.lowercase()}")
                                putInt("ep_carousel_position", i)
                                putString("ep_layout_type", "full_card")
                            }
                        if (HomePlaybackStateLogic.isHeroItemPlaying(
                                itemEpisodeId = item.podcast.latestEpisode?.id,
                                itemPodcastId = item.podcast.id,
                                currentPlayingEpisodeId = currentPlayingEpisodeId,
                                currentPlayingPodcastId = currentPlayingPodcastId,
                                isPlaying = isPlaying,
                            )
                        ) {
                            onTogglePlayback(bundle)
                        } else {
                            onPlayClick(item.podcast, bundle)
                        }
                    }, // Primary "Play" or "Pause" button action
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
 * The Cover option's cards, on the same carousel, playback and details callbacks. As on the solid
 * cards, a collection tile opens its episode and the Play button on its cover plays it.
 */
@Composable
@Suppress("LongParameterList")
private fun androidx.compose.material3.carousel.CarouselItemScope.CoverHeroItem(
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
            CoverHeroGridCard(
                items = item.gridItems,
                title = stringResource(if (resume) R.string.home_hero_resume_collection else R.string.home_hero_new_collection),
                mode = if (resume) HeroGridMode.Resume else HeroGridMode.NewEpisodes,
                onCellClick = onDetailsClick,
                onPlaybackClick = onCollectionPlayback,
                currentPlayingPodcastId = playing.podcastId,
                currentPlayingEpisodeId = playing.episodeId,
                isPlaying = playing.isPlaying,
                modifier = Modifier.maskClip(shape),
            )
        }
        else -> CoverHeroCard(
            item = item,
            onClick = {
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
            },
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
