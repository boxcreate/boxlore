package cx.aswin.boxlore.wearlink

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.wearlink.PhoneLinkEvent
import cx.aswin.boxlore.core.wearlink.PhoneLinkEvents

/** Sends what passes through the watch link as usage events. A failure here never reaches the link. */
internal class PhoneLinkAnalytics(private val send: (PhoneLinkEvent) -> Unit = ::track) : PhoneLinkEvents {
    override fun note(event: PhoneLinkEvent) {
        runCatching { send(event) }
    }

    private companion object {
        fun track(event: PhoneLinkEvent) {
            when (event) {
                PhoneLinkEvent.WatchLinked -> AnalyticsHelper.trackWatchLinked()
                is PhoneLinkEvent.SignInAnswered -> AnalyticsHelper.trackWatchSignInAnswered(event.status)
                is PhoneLinkEvent.EpisodeOrder -> AnalyticsHelper.trackWatchEpisodeOrder(event.download, event.sent)
                is PhoneLinkEvent.PlaybackMoved -> AnalyticsHelper.trackWatchPlaybackMoved(event.toWatch)
                is PhoneLinkEvent.WatchChangesMerged -> AnalyticsHelper.trackWatchChangesMerged(event.episodes, event.follows)
            }
        }
    }
}
