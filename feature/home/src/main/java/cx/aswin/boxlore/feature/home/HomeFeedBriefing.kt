package cx.aswin.boxlore.feature.home

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.BriefingLinks
import cx.aswin.boxlore.core.designsystem.briefing.briefingShowPodcast
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.components.DailyBriefingCard
import java.time.LocalDate
import java.time.ZoneOffset

internal fun LazyStaggeredGridScope.dailyBriefingItem(
    feedState: PodcastFeedUiState,
    playback: PodcastFeedPlayback,
    callbacks: HomeFeedCallbacks,
    context: Context,
) {
    if (feedState.briefing == null) return
    item(span = StaggeredGridItemSpan.FullLine, key = "briefing", contentType = "briefing") {
        DailyBriefingFeedCard(feedState, playback, callbacks, context)
    }
}

@Composable
internal fun DailyBriefingFeedCard(feedState: PodcastFeedUiState, playback: PodcastFeedPlayback, callbacks: HomeFeedCallbacks, context: Context) {
    val briefing = feedState.briefing ?: return
    PinnedGridItemContent {
        val briefingId = "briefing_${briefing.region}_${briefing.date}"
        val playbackState = playback.episodePlaybackState.map[briefingId]
        LaunchedEffect(briefing.region, briefing.date) {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingCardImpression(
                region = briefing.region,
                date = briefing.date,
                playbackStatus = playbackState?.first?.name ?: "NOT_STARTED",
            )
        }
        DailyBriefingCard(
            briefing = briefing,
            chapters = feedState.briefingChapters,
            isPlaying = playback.player.isPlaying && playback.player.currentPlayingEpisodeId == briefingId,
            playbackStatus = playbackState?.first,
            playbackProgress = playbackState?.second,
            isBuffering = playback.player.isPlayerLoading && playback.player.currentPlayingEpisodeId == briefingId,
            onPlayPauseClick = {
                playDailyBriefingFromFeed(
                    briefing = briefing,
                    briefingId = briefingId,
                    playback = playback.player,
                    callbacks = callbacks,
                    context = context,
                )
            },
            onClick = {
                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingBannerTapped(
                    region = briefing.region,
                    date = briefing.date,
                )
                callbacks.onBriefingClick(briefing.region)
            },
            onDismiss = callbacks.onDismissBriefing,
            onDismissForever = callbacks.onDismissBriefingForever,
            onFeedbackClick = callbacks.onFeedbackClick,
            modifier = Modifier.padding(bottom = if (LocalBoxloreWindowLayout.current.usesTabletLayout) 0.dp else 12.dp),
        )
    }
}

private fun playDailyBriefingFromFeed(
    briefing: Briefing,
    briefingId: String,
    playback: HomePlaybackUi,
    callbacks: HomeFeedCallbacks,
    context: Context,
) {
    val isCurrentPlaying = playback.isPlaying && playback.currentPlayingEpisodeId == briefingId
    trackDailyBriefingPlayPause(briefing, isCurrentPlaying)
    val show = briefingShowPodcast(context, briefing.region)
    callbacks.onPlayEpisode(
        dailyBriefingEpisode(briefing, briefingId, show),
        show,
        // Glossary entry_point=briefing (via enum → sourceContext). Do not use GENERIC —
        // that normalizes to unknown and breaks Brief playback attribution.
        PlaybackEntryPoint.BRIEFING,
    )
}

private fun trackDailyBriefingPlayPause(
    briefing: Briefing,
    isCurrentPlaying: Boolean,
) {
    if (isCurrentPlaying) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingPauseClicked(
            region = briefing.region,
            date = briefing.date,
            source = "home_banner",
        )
    } else {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDailyBriefingPlayClicked(
            region = briefing.region,
            date = briefing.date,
            source = "home_banner",
        )
    }
}

private fun dailyBriefingEpisode(
    briefing: Briefing,
    briefingId: String,
    show: Podcast,
): Episode {
    val version = dailyBriefingVersion(briefing.audioUrl)
    return Episode(
        id = briefingId,
        title = briefing.title,
        description = "Your daily AI-generated news briefing for ${briefing.region.uppercase()}.",
        audioUrl = briefing.audioUrl,
        imageUrl = show.imageUrl,
        podcastId = show.id,
        podcastTitle = show.title,
        podcastImageUrl = show.imageUrl,
        podcastArtist = show.artist,
        duration = 180,
        publishedDate = dailyBriefingPublishedDate(briefing.date),
        transcriptUrl =
        briefing.transcriptUrl
            ?: BriefingLinks.transcriptUrl(briefing.region, briefing.date, version),
        chaptersUrl =
        briefing.chaptersUrl
            ?: BriefingLinks.chaptersUrl(briefing.region, briefing.date, version),
    )
}

private fun dailyBriefingPublishedDate(date: String): Long = try {
    LocalDate
        .parse(date)
        .atStartOfDay(ZoneOffset.UTC)
        .toEpochSecond()
} catch (e: Exception) {
    System.currentTimeMillis() / 1000
}

private fun dailyBriefingVersion(audioUrl: String): String? = Uri.parse(audioUrl).getQueryParameter("v")
