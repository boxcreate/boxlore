package cx.aswin.boxlore.feature.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.model.Podcast

/** The outer card clips the top/end corner; only the exposed inner corner is rounded. */
@Composable
internal fun HeroArtworkMerge(imageUrl: String?, modifier: Modifier = Modifier) {
    OptimizedImage(
        url = imageUrl,
        proxyWidth = 800,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(HomeHeroStyle.AttachedArtworkShape),
    )
}

// Spotlight represents a show; other solo heroes represent a particular episode.
internal fun heroArtworkUrl(podcast: Podcast, showFirst: Boolean = false): String? {
    val show = podcast.imageUrl.takeIf { it.isNotBlank() } ?: podcast.fallbackImageUrl?.takeIf { it.isNotBlank() }
    val episode = podcast.latestEpisode?.imageUrl?.takeIf { it.isNotBlank() }
    return if (showFirst) show ?: episode else episode ?: show
}
