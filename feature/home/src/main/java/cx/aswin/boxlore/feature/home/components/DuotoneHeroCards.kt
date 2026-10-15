package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomePlaybackStateLogic

/**
 * The Duotone hero card: the cover fills the card redrawn in two tones ([rememberDuotoneLook]), with
 * the solid cards' context block at the top and Cover's title, show name, progress and Play /
 * details pair at the bottom. The tones keep the words readable without a shade over the art.
 */
@Composable
@Suppress("LongParameterList")
internal fun DuotoneHeroCard(
    item: SmartHeroItem,
    onClick: () -> Unit,
    onArrowClick: () -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val current = HomePlaybackStateLogic.isHeroItemCurrent(
        itemEpisodeId = item.podcast.latestEpisode?.id,
        itemPodcastId = item.podcast.id,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        currentPlayingPodcastId = currentPlayingPodcastId,
    )
    val pause = isPlaying && current
    val progress = HomeHeroStyle.progress(item.podcast)
    val progressState = if (item.type == HeroType.RESUME && progress > 0f) HeroProgressState(progress, pause) else null
    val imageUrl = heroArtworkUrl(item.podcast, showFirst = item.type == HeroType.SPOTLIGHT)
    val look = rememberDuotoneLook(imageUrl)
    val shade = look.shade
    val filter = remember(look.duotone) { look.duotone.colorFilter() }
    Card(
        onClick = onArrowClick,
        shape = HomeHeroStyle.CardShape,
        colors = CardDefaults.cardColors(containerColor = look.duotone.shadow, contentColor = shade.content),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Hero).fillMaxSize().testTag("home_hero_duotone"),
    ) {
        Box(Modifier.fillMaxSize()) {
            OptimizedImage(
                url = imageUrl,
                fallbackUrls = heroArtworkSources(item),
                proxyWidth = 800,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // A short card (above a tabletop fold) puts the context in the copy, as Cover does, so Play keeps its height.
                val short = maxHeight < ShortHeroHeight
                Column(Modifier.fillMaxSize().padding(20.dp)) {
                    if (!short) HeroCardContext(item, shade.content, wide = LocalDensity.current.fontScale > 1.3f, Modifier.fillMaxWidth())
                    Spacer(Modifier.weight(1f))
                    CoverCopy(item, shade, progressState, showContext = short)
                    Spacer(Modifier.height(16.dp))
                    HeroActions(
                        pause = pause,
                        resume = item.type == HeroType.RESUME || current,
                        colors = HeroActionColors(play = shade.content, onPlay = shade.deep, details = shade.container, onDetails = shade.content),
                        item = item,
                        onPlayClick = onClick,
                        onDetailsClick = onArrowClick,
                    )
                }
            }
        }
    }
}

/**
 * The Duotone collection card: the solid cards' collection layout (rows, peek label and counts) on
 * the first cover's shadow tone, with every cover redrawn in its own two tones.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
internal fun DuotoneHeroGridCard(
    items: List<Podcast>,
    title: String,
    mode: HeroGridMode,
    onCellClick: (Podcast) -> Unit,
    onPlaybackClick: (Podcast) -> Unit,
    currentPlayingPodcastId: String?,
    currentPlayingEpisodeId: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    carouselItemDrawInfo: CarouselItemDrawInfo? = null,
) {
    val look = rememberDuotoneLook(items.firstOrNull()?.imageUrl)
    HeroGridCard(
        items = items,
        title = title,
        mode = mode,
        onCellClick = onCellClick,
        currentPlayingPodcastId = currentPlayingPodcastId,
        currentPlayingEpisodeId = currentPlayingEpisodeId,
        isPlaying = isPlaying,
        modifier = modifier.testTag("home_hero_duotone_collection"),
        onPlaybackClick = onPlaybackClick,
        carouselItemDrawInfo = carouselItemDrawInfo,
        containerColor = look.duotone.shadow,
        contentColor = look.shade.content,
        artworkColorFilter = { podcast -> duotoneFilterFor(podcast) },
    )
}

@Composable
private fun duotoneFilterFor(podcast: Podcast): ColorFilter {
    val tones = rememberDuotoneLook(podcast.imageUrl).duotone
    return remember(tones) { tones.colorFilter() }
}
