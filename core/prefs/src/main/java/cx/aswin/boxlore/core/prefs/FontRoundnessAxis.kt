package cx.aswin.boxlore.core.prefs

import android.content.Context

/** Shared lettering roundness contract (`font_roundness` pref / ROND axis). */
object FontRoundnessAxis {
    const val THEME_FAST_CACHE = "boxlore_theme_fast_cache"
    const val PREF_KEY = "font_roundness"

    const val CRISP = "crisp"
    const val SOFT = "soft"
    const val ROUND = "round"

    const val AXIS_CRISP = 0
    const val AXIS_SOFT = 50
    const val AXIS_ROUND = 100

    fun sanitizeKey(key: String?): String {
        val normalized = key?.trim()?.lowercase()
        return when (normalized) {
            CRISP, ROUND -> normalized
            else -> SOFT
        }
    }

    fun axisValue(key: String?): Int =
        when (sanitizeKey(key)) {
            CRISP -> AXIS_CRISP
            ROUND -> AXIS_ROUND
            else -> AXIS_SOFT
        }

    fun cachedAxisValue(context: Context): Int {
        val prefs = context.getSharedPreferences(THEME_FAST_CACHE, Context.MODE_PRIVATE)
        return axisValue(prefs.getString(PREF_KEY, null))
    }
}
