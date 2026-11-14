package cx.aswin.boxlore.wearlink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cx.aswin.boxlore.core.wearlink.PhoneWearLink
import cx.aswin.boxlore.core.wearlink.WearLinkSettings
import cx.aswin.boxlore.core.wearlink.WearLinkStatus
import cx.aswin.boxlore.feature.settings.pages.WearOsSettings
import cx.aswin.boxlore.feature.settings.pages.WearOsUiState

/** What Settings shows for Wear OS, or null until boxlore on a watch has linked with this phone. */
internal fun wearOsSettings(status: WearLinkStatus, settings: WearLinkSettings, link: PhoneWearLink): WearOsSettings? {
    if (!status.watchKnown) return null
    return WearOsSettings(
        state =
            WearOsUiState(
                watchReachable = status.reachable,
                showsLayout = settings.showsLayout,
                storageLimitMb = settings.storageLimitMb,
                watchStoredBytes = status.watch.storedBytes,
                watchDownloads = status.watch.downloads,
            ),
        onSetShowsLayout = link::setShowsLayout,
        onSetStorageLimitMb = link::setStorageLimitMb,
    )
}

@Composable
internal fun rememberWearOsSettings(link: PhoneWearLink): WearOsSettings? {
    val status by link.status.collectAsState()
    val settings by link.settings.collectAsState()
    return wearOsSettings(status, settings, link)
}
