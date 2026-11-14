package cx.aswin.boxlore.wear

import android.content.Context

/** Release builds always run on the listener's real library. */
internal object WatchContainerFactory {
    fun create(context: Context): WatchContainer = LiveWatchContainer.create(context)
}
