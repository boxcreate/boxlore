package cx.aswin.boxlore.wearlink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cx.aswin.boxlore.core.designsystem.components.WatchEpisodeActions
import cx.aswin.boxlore.core.designsystem.components.WatchEpisodeCopy
import cx.aswin.boxlore.core.designsystem.components.WatchPlayOffer
import cx.aswin.boxlore.core.wearlink.PhoneWatchDownloads
import cx.aswin.boxlore.core.wearlink.PhoneWearLink
import cx.aswin.boxlore.core.wearlink.WatchCopy
import cx.aswin.boxlore.core.wearlink.WatchCopyRules
import cx.aswin.boxlore.core.wearlink.WatchStoredEpisode
import cx.aswin.boxlore.core.wearlink.WearLinkStatus
import cx.aswin.boxlore.ui.WatchTakeover

/** What the players read to offer the watch, or null while no watch with boxlore is in reach of this phone. */
internal fun watchPlayOffer(status: WearLinkStatus, link: PhoneWearLink): WatchPlayOffer? =
    if (status.watchKnown && status.reachable) WatchPlayOffer(link::playOnWatch) else null

@Composable
internal fun rememberWatchPlayOffer(link: PhoneWearLink): WatchPlayOffer? {
    val status by link.status.collectAsState()
    return remember(status.watchKnown, status.reachable, link) { watchPlayOffer(status, link) }
}

/** What an episode page can do with the watch, or null until boxlore on a watch has linked with this phone. */
internal fun watchEpisodeActions(watchKnown: Boolean, stored: List<WatchStoredEpisode>, downloads: PhoneWatchDownloads): WatchEpisodeActions? {
    if (!watchKnown) return null
    return WatchEpisodeActions(
        copyOf = { id, audioUrl ->
            when (WatchCopyRules.of(id, audioUrl, stored)) {
                WatchCopy.None -> WatchEpisodeCopy.None
                WatchCopy.Downloading -> WatchEpisodeCopy.Downloading
                WatchCopy.OnWatch -> WatchEpisodeCopy.OnWatch
            }
        },
        send = { episode, onResult -> downloads.send(episode.toLink(episode.podcastId, episode.podcastTitle), onResult) },
        remove = { episode, onResult -> downloads.remove(episode.toLink(episode.podcastId, episode.podcastTitle), onResult) },
    )
}

@Composable
internal fun rememberWatchEpisodeActions(link: PhoneWearLink): WatchEpisodeActions? {
    val status by link.status.collectAsState()
    val stored by link.watchDownloads.stored.collectAsState()
    return remember(status.watchKnown, stored, link) { watchEpisodeActions(status.watchKnown, stored, link.watchDownloads) }
}

/** Whether this phone has just taken over from a playing watch, with what the notice's buttons do. */
@Composable
internal fun rememberWatchTakeover(link: PhoneWearLink): WatchTakeover {
    val offered by link.playOnWatchOffer.collectAsState()
    return remember(offered, link) { WatchTakeover(offered, link::playOnWatch, link::dismissPlayOnWatchOffer) }
}
