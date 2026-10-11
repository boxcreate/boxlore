package cx.aswin.boxlore.core.designsystem.briefing

import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.BriefingArtwork
import java.time.LocalDate
import java.time.format.DateTimeParseException

const val BRIEFING_COVER_ASPECT_RATIO = 1.6f

/** Named resource URIs remain valid when bundled resources are reordered in a later build. */
fun briefingPlayerArtworkUri(applicationId: String, region: String): String =
    BriefingArtwork.forRegion(region).uri(applicationId)

/** The player's branded square stays fixed, independently of the edition cover. */
fun briefingPlayerResource(region: String): Int = when (BriefingArtwork.forRegion(region)) {
    BriefingArtwork.INDIA -> R.drawable.daily_briefing_india
    BriefingArtwork.UK -> R.drawable.daily_briefing_uk
    BriefingArtwork.USA -> R.drawable.daily_briefing_usa
    BriefingArtwork.GLOBAL -> R.drawable.daily_briefing_global
}

/** Monday is cover 01; Sunday is cover 07. Invalid dates use Monday deterministically. */
fun briefingCoverResource(region: String, editionDate: String): Int {
    val covers = when (BriefingArtwork.forRegion(region)) {
        BriefingArtwork.INDIA -> indiaCovers
        BriefingArtwork.UK -> ukCovers
        BriefingArtwork.USA -> usaCovers
        BriefingArtwork.GLOBAL -> globalCovers
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
