package cx.aswin.boxlore.core.prefs

import android.content.Context

/**
 * Home-screen widget chrome: follow the in-app Appearance theme (default) or the
 * launcher’s system light/dark and wallpaper accents.
 */
object WidgetAppearance {
    const val APP = "app"
    const val SYSTEM = "system"
    const val PREF_KEY = "widget_appearance"

    fun sanitize(value: String?): String {
        val normalized = value?.trim()?.lowercase()
        return if (normalized == SYSTEM) SYSTEM else APP
    }

    fun followsAppTheme(value: String?): Boolean = sanitize(value) == APP

    fun cached(context: Context): String {
        val prefs =
            context.getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)
        return sanitize(prefs.getString(PREF_KEY, null))
    }
}
