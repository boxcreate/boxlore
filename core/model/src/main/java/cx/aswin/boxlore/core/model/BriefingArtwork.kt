package cx.aswin.boxlore.core.model

import java.util.Locale

/** Stable bundled artwork names shared by persistence and UI; never persist numeric resource IDs. */
enum class BriefingArtwork(val resourceName: String, vararg val regionAliases: String) {
    GLOBAL("daily_briefing_global", "global"),
    INDIA("daily_briefing_india", "in", "ind"),
    UK("daily_briefing_uk", "gb", "uk"),
    USA("daily_briefing_usa", "us", "usa"),
    ;

    fun uri(applicationId: String): String = "android.resource://$applicationId/drawable/$resourceName"

    companion object {
        fun forRegion(region: String): BriefingArtwork {
            val normalized = region.lowercase(Locale.ROOT)
            return entries.firstOrNull { normalized in it.regionAliases } ?: GLOBAL
        }
    }
}
