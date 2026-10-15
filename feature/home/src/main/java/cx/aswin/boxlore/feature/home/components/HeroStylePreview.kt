package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.logic.HomeHeroLogic

private val PreviewCardWidth = 320.dp

/**
 * Home's hero cards in [style] (Block or Cover, with [coverShade]) for the Appearance previews,
 * scaled to fit this modifier's bounds: the latest episode to resume, or with [collection] the
 * Jump back in card. Uses the listener's resume [sessions]; with none yet (two for a collection),
 * it uses covers bundled with the app and sample names. The cards are pictures: callers block taps.
 */
@Composable
fun HeroStylePreview(
    style: String,
    coverShade: String,
    collection: Boolean,
    sessions: List<PlaybackSession>,
    modifier: Modifier = Modifier,
) {
    val packageName = LocalContext.current.packageName
    val podcasts = remember(sessions, collection, packageName) { heroPreviewPodcasts(sessions, collection, packageName) }
    // Marquee's single card shows the next covers beside Play, and its slice of Home a few shows.
    val upNext = remember(sessions, packageName, style, collection) {
        if (HeroCardStyle.sanitize(style) == HeroCardStyle.MARQUEE && !collection) heroPreviewPodcasts(sessions, true, packageName).drop(1) else emptyList()
    }
    val cardHeight = if (HeroCardStyle.sanitize(style) == HeroCardStyle.MARQUEE) MarqueePreviewHeight else homeHeroHeight()
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth / PreviewCardWidth, maxHeight / cardHeight)
        Box(Modifier.size(PreviewCardWidth * scale, cardHeight * scale)) {
            Box(Modifier.requiredSize(PreviewCardWidth, cardHeight).graphicsLayer { scaleX = scale; scaleY = scale }) {
                CompositionLocalProvider(LocalHeroCoverShade provides coverShade) {
                    HeroPreviewCard(HeroCardStyle.sanitize(style), collection, podcasts, upNext)
                }
            }
        }
    }
}

@Composable
private fun HeroPreviewCard(style: String, collection: Boolean, podcasts: List<Podcast>, upNext: List<Podcast>) {
    if (collection) {
        val title = stringResource(R.string.home_hero_resume_collection)
        when (style) {
            HeroCardStyle.COVER ->
                CoverHeroGridCard(podcasts, title, HeroGridMode.Resume, onCellClick = {}, onPlaybackClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            HeroCardStyle.DUOTONE ->
                DuotoneHeroGridCard(podcasts, title, HeroGridMode.Resume, onCellClick = {}, onPlaybackClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            HeroCardStyle.TIDE ->
                TideHeroGridCard(podcasts, title, HeroGridMode.Resume, onCellClick = {}, onPlaybackClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            HeroCardStyle.MARQUEE -> MarqueePreviewScreen(collection = true, podcasts = podcasts, upNext = emptyList())
            HeroCardStyle.HALO ->
                HaloHeroGridCard(podcasts, title, HeroGridMode.Resume, onCellClick = {}, onPlaybackClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            else -> HeroGridCard(podcasts, title, HeroGridMode.Resume, onCellClick = {})
        }
    } else {
        val first = podcasts.first()
        val item = SmartHeroItem(HeroType.RESUME, first, "Resume", description = first.latestEpisode?.title)
        when (style) {
            HeroCardStyle.COVER ->
                CoverHeroCard(item, onClick = {}, onArrowClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            HeroCardStyle.DUOTONE ->
                DuotoneHeroCard(item, onClick = {}, onArrowClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            HeroCardStyle.TIDE ->
                TideHeroCard(item, onClick = {}, onArrowClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            HeroCardStyle.MARQUEE -> MarqueePreviewScreen(collection = false, podcasts = podcasts, upNext = upNext)
            HeroCardStyle.HALO ->
                HaloHeroCard(item, onClick = {}, onArrowClick = {}, currentPlayingPodcastId = null, currentPlayingEpisodeId = null, isPlaying = false)
            else -> HeroCard(item, onClick = {}, onArrowClick = {}, onToggleSubscription = {})
        }
    }
}

/** The listener's latest resume episodes when there are enough, else bundled samples. */
internal fun heroPreviewPodcasts(sessions: List<PlaybackSession>, collection: Boolean, packageName: String): List<Podcast> {
    val needed = if (collection) 2 else 1
    if (sessions.size >= needed) {
        return sessions.take(if (collection) 4 else 1).map { HomeHeroLogic.sessionToPodcast(it, subs = emptyList()) }
    }
    return heroPreviewSamples(packageName).take(if (collection) 4 else 1)
}

private data class PreviewSample(val cover: Int, val show: String, val episode: String, val minutes: Int, val progress: Float)

// Covers bundled in the design system (named in the onboarding cover reel); the episode names are samples.
private val PreviewSamples = listOf(
    PreviewSample(DesignR.drawable.pod_cover_13, "Radiolab", "The sound of an empty room", 52, 0.42f),
    PreviewSample(DesignR.drawable.pod_cover_77, "Hidden Brain", "Why we remember the strangest things", 48, 0.66f),
    PreviewSample(DesignR.drawable.pod_cover_81, "Short Wave", "What clouds are made of", 14, 0.3f),
    PreviewSample(DesignR.drawable.pod_cover_92, "Ologies", "A whole hour about owls", 70, 0.18f),
)

internal fun heroPreviewSamples(packageName: String): List<Podcast> = PreviewSamples.mapIndexed { index, sample ->
    val art = "android.resource://$packageName/${sample.cover}"
    Podcast(
        id = "preview-$index",
        title = sample.show,
        artist = sample.show,
        imageUrl = art,
        fallbackImageUrl = art,
        resumeProgress = sample.progress,
        episodeStatus = EpisodeStatus.IN_PROGRESS,
        latestEpisode = Episode(
            id = "preview-episode-$index",
            title = sample.episode,
            description = "",
            audioUrl = "",
            imageUrl = art,
            podcastImageUrl = art,
            podcastTitle = sample.show,
            podcastId = "preview-$index",
            duration = sample.minutes * 60,
            publishedDate = 0L,
        ),
    )
}
