package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem

/** Marquee's Settings picture is this tall: a phone-like slice of Home, so the cover shows above its words. */
internal val MarqueePreviewHeight = 480.dp

/**
 * Marquee in Settings: a slice of Home rather than a lone card, since the style is the layout. The cover
 * runs under a floating top bar with its soft fade and wordmark, the page dots sit under Play, and
 * Your shows' rounded sheet rises over the cover's foot with its heading and a few shows. With
 * [collection] the cover is the Jump back in card with its strip of covers.
 */
@Composable
internal fun MarqueePreviewScreen(collection: Boolean, podcasts: List<Podcast>, upNext: List<Podcast>) {
    val first = podcasts.firstOrNull() ?: return
    val shows = (podcasts + upNext).distinctBy { it.id }
    val wordmark = rememberMarqueeLook(first.imageUrl).topContent
    Box(
        Modifier.fillMaxSize().clip(HomeHeroStyle.CardShape).background(MaterialTheme.colorScheme.surface)
            .testTag("home_hero_marquee_preview"),
    ) {
        CompositionLocalProvider(LocalHeroTopInset provides MarqueePreviewBar) {
            Box(Modifier.fillMaxWidth().height(MarqueePreviewHeight - MarqueePreviewBody + MarqueeSheetOverlap)) {
                if (collection) {
                    MarqueeCollectionCard(
                        items = podcasts,
                        title = stringResource(R.string.home_hero_resume_collection),
                        mode = HeroGridMode.Resume,
                        active = false,
                        onCellClick = {},
                        onPlaybackClick = {},
                        currentPlayingPodcastId = null,
                        currentPlayingEpisodeId = null,
                        isPlaying = false,
                    )
                } else {
                    MarqueeHeroCard(
                        item = SmartHeroItem(HeroType.RESUME, first, "Resume", description = first.latestEpisode?.title),
                        onClick = {},
                        onArrowClick = {},
                        currentPlayingPodcastId = null,
                        currentPlayingEpisodeId = null,
                        isPlaying = false,
                        upNext = upNext.mapIndexed { index, podcast -> MarqueeNext(index + 1, podcast.imageUrl, podcast.title) },
                        onUpNext = {},
                    )
                }
                MarqueePageDots(
                    count = MarqueePreviewPages,
                    current = if (collection) 1 else 0,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MarqueeSheetOverlap + MarqueeDotsBottom),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(MarqueePreviewBar).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxLoreLogo(textColor = wordmark)
            TopBarUtilityActions(onFeedbackClick = {}, onSettingsClick = {})
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().height(MarqueePreviewBody).clip(MarqueeSheetShape)
                .background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 16.dp, top = 10.dp),
        ) {
            MarqueeSheetHandle(Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.home_your_shows_heading),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shows.take(MarqueePreviewShows).forEach { show ->
                    OptimizedImage(
                        url = show.imageUrl,
                        proxyWidth = 160,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(CircleShape),
                    )
                }
            }
        }
    }
}

private val MarqueePreviewBar = 56.dp
private val MarqueePreviewBody = 92.dp
private const val MarqueePreviewPages = 3
private const val MarqueePreviewShows = 5
