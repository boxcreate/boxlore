package cx.aswin.boxlore.wearlink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import cx.aswin.boxlore.core.designsystem.components.WatchPlayOffer
import cx.aswin.boxlore.core.wearlink.PhoneWearLink
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

/** Whether this phone has just taken over from a playing watch, with what the notice's buttons do. */
@Composable
internal fun rememberWatchTakeover(link: PhoneWearLink): WatchTakeover {
    val offered by link.playOnWatchOffer.collectAsState()
    return remember(offered, link) { WatchTakeover(offered, link::playOnWatch, link::dismissPlayOnWatchOffer) }
}
