package cx.aswin.boxlore.feature.widgets

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration

/**
 * Re-pushes widget RemoteViews when system UI configuration changes (Material You
 * accent, night mode, density, etc.). Needed for System widget appearance, App-theme
 * widgets whose Theme is System, and empty-state bitmaps that bake ARGB. Pref-driven
 * Appearance changes are collected in `:app` and request a separate refresh.
 */
internal object WidgetThemeSync {
    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            app.registerComponentCallbacks(
                object : ComponentCallbacks2 {
                    override fun onConfigurationChanged(newConfig: Configuration) {
                        NowPlayingWidgetCoordinator.requestRefresh(app)
                        LibraryWidgetCoordinator.requestRefresh(app)
                    }

                    override fun onLowMemory() = Unit

                    override fun onTrimMemory(level: Int) = Unit
                },
            )
            installed = true
        }
    }
}
