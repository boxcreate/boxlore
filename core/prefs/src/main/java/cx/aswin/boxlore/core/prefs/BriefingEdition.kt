package cx.aswin.boxlore.core.prefs

import cx.aswin.boxlore.core.model.ContentRegions
import java.util.Locale

/** Saved Brief edition is independent of discovery region; explicit entry links win. */
object BriefingEdition {
    const val FOLLOW_APP = "follow_app"
    val regions = listOf("global", "in", "us", "gb")

    fun sanitize(value: String?): String = when (val key = value.orEmpty().trim().lowercase(Locale.ROOT)) {
        "uk" -> "gb"
        "ind" -> "in"
        "usa" -> "us"
        in regions -> key
        else -> FOLLOW_APP
    }

    fun resolve(defaultEdition: String?, appRegion: String, explicitRegion: String? = null): String {
        if (!explicitRegion.isNullOrBlank()) return ContentRegions.briefingMarket(explicitRegion)
        val saved = sanitize(defaultEdition)
        return if (saved == FOLLOW_APP) ContentRegions.briefingMarket(appRegion) else saved
    }
}
