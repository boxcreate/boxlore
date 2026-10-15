package cx.aswin.boxlore.core.prefs

import java.util.Locale

/** How strongly the page blurs behind the floating navigation bar. */
object NavigationBlurLevel {
    const val OFF = "off"
    const val SOFT = "soft"
    const val MEDIUM = "medium"
    const val STRONG = "strong"
    const val DEFAULT = SOFT
    const val PREF_KEY = "navigation_blur_level"

    val entries = listOf(OFF, SOFT, MEDIUM, STRONG)

    fun sanitize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in entries } ?: DEFAULT
}
