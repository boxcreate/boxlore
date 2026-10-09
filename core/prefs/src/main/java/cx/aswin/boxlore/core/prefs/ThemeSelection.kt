package cx.aswin.boxlore.core.prefs

/** Opaque theme inputs shared by app, settings, widgets and the cold-start cache. */
data class ThemeSelection(val brand: String, val surfaceStyle: String, val wallpaperColors: Boolean = false)
