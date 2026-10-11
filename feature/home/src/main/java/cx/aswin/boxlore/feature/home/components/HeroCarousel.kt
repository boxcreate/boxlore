package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.PaddingValues
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

    val carouselState = rememberCarouselState { heroItems.list.size }

    // Telemetry: Track Max Swipe Depth
    val maxScrolledIndex = androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(maxScrolledIndex.intValue) {
        if (maxScrolledIndex.intValue > 0) {
            kotlinx.coroutines.delay(3000) // 3s debounce
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackHomeHeroCarouselSwiped(
                maxCardIndexViewed = maxScrolledIndex.intValue,
                totalCardsAvailable = heroItems.list.size,
            )
        }
    }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = 320.dp,
        itemSpacing = 16.dp,
        contentPadding = PaddingValues(0.dp),
        modifier =
        modifier
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

        if (item.type == cx.aswin.boxlore.feature.home.HeroType.RESUME_GRID) {
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
        } else if (item.type == cx.aswin.boxlore.feature.home.HeroType.NEW_EPISODES_GRID) {
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
        } else {
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
