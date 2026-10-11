package cx.aswin.boxlore.core.designsystem.briefing

import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.BriefingArtwork
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

const val BRIEFING_COVER_ASPECT_RATIO = 1.6f

/** Named resource URIs remain valid when bundled resources are reordered in a later build. */
fun briefingPlayerArtworkUri(applicationId: String, region: String): String =
    BriefingArtwork.forRegion(region).uri(applicationId)

/** The player's branded square stays fixed, independently of the edition cover. */
fun briefingPlayerResource(region: String): Int = when (region.lowercase(Locale.ROOT)) {
    "in", "ind" -> R.drawable.daily_briefing_india
    "gb", "uk" -> R.drawable.daily_briefing_uk
    "us", "usa" -> R.drawable.daily_briefing_usa
    else -> R.drawable.daily_briefing_global
}

/** Monday is cover 01; Sunday is cover 07. Invalid dates use Monday deterministically. */
fun briefingCoverResource(region: String, editionDate: String): Int {
    val covers = when (region.lowercase(Locale.ROOT)) {
        "in", "ind" -> indiaCovers
        "gb", "uk" -> ukCovers
        "us", "usa" -> usaCovers
        else -> globalCovers
    }
    return covers[briefingWeekdayIndex(editionDate)]
}

internal fun briefingWeekdayIndex(editionDate: String): Int = try {
    LocalDate.parse(editionDate).dayOfWeek.value - 1
} catch (_: DateTimeParseException) {
    0
}

private val globalCovers = intArrayOf(
    R.drawable.briefing_cover_global_01,
    R.drawable.briefing_cover_global_02,
    R.drawable.briefing_cover_global_03,
    R.drawable.briefing_cover_global_04,
    R.drawable.briefing_cover_global_05,
    R.drawable.briefing_cover_global_06,
    R.drawable.briefing_cover_global_07,
)
private val indiaCovers = intArrayOf(
    R.drawable.briefing_cover_india_01,
    R.drawable.briefing_cover_india_02,
    R.drawable.briefing_cover_india_03,
    R.drawable.briefing_cover_india_04,
    R.drawable.briefing_cover_india_05,
    R.drawable.briefing_cover_india_06,
    R.drawable.briefing_cover_india_07,
)
private val ukCovers = intArrayOf(
    R.drawable.briefing_cover_uk_01,
    R.drawable.briefing_cover_uk_02,
    R.drawable.briefing_cover_uk_03,
    R.drawable.briefing_cover_uk_04,
    R.drawable.briefing_cover_uk_05,
    R.drawable.briefing_cover_uk_06,
    R.drawable.briefing_cover_uk_07,
)
private val usaCovers = intArrayOf(
    R.drawable.briefing_cover_usa_01,
    R.drawable.briefing_cover_usa_02,
    R.drawable.briefing_cover_usa_03,
    R.drawable.briefing_cover_usa_04,
    R.drawable.briefing_cover_usa_05,
    R.drawable.briefing_cover_usa_06,
    R.drawable.briefing_cover_usa_07,
)
