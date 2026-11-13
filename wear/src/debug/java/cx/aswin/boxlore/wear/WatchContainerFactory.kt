package cx.aswin.boxlore.wear

import android.content.Context
import cx.aswin.boxlore.wear.data.WatchSettings
import cx.aswin.boxlore.wear.preview.PreviewWatchLibrary
import cx.aswin.boxlore.wear.preview.PreviewWatchPlayback

/**
 * Debug builds run on the real library like release. Building with `-Pwear.sample=true` swaps in
 * sample shows instead, so every screen can be looked at without an account, a phone or a network.
 */
internal object WatchContainerFactory {
    fun create(context: Context): WatchContainer {
        if (!BuildConfig.WATCH_SAMPLE_DATA) return LiveWatchContainer.create(context)
        val library = PreviewWatchLibrary()
        return WatchContainer(
            library = library,
            playback = PreviewWatchPlayback(library),
            settings = WatchSettings.create(context),
        )
    }
}
