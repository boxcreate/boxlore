package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsGlossary
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.feature.home.components.FeaturedVideoPodcastsShowcase
import cx.aswin.boxlore.feature.home.logic.featuredTedTalksSdPodcast

internal fun LazyStaggeredGridScope.featuredVideoPodcastsItem(
    state: HomeFeaturedVideoState,
    callbacks: HomeFeedCallbacks,
) {
    if (state.isDismissed || state.podcasts.list.isEmpty()) return
    item(
        span = StaggeredGridItemSpan.FullLine,
        key = "featured_video_podcasts",
        contentType = "featured_video_podcasts",
    ) {
        FeaturedVideoPodcastsShowcase(
            podcasts = state.podcasts.list,
            tedTalksSdPodcast = featuredTedTalksSdPodcast(),
            onPodcastClick = { podcast, index, clickTarget ->
                AnalyticsHelper.trackVideoSpotlightPodcastTapped(
                    podcastId = podcast.id,
                    podcastName = podcast.title,
                    positionIndex = index,
                    clickTarget = clickTarget,
                )
                callbacks.onPodcastClick(
                    podcast,
                    AnalyticsGlossary.VIDEO_SPOTLIGHT_ENTRY_POINT,
                    "Video Spotlight",
                    index,
                )
            },
            onDismissForever = callbacks.onDismissFeaturedVideoShowcaseForever,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
