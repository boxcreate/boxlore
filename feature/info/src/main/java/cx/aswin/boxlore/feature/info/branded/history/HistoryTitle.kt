package cx.aswin.boxlore.feature.info.branded.history

import androidx.compose.runtime.Immutable

/**
 * An episode title as the show writes it, "720. Revolution in the Soviet Empire: Revenge of the Red
 * Army (Part 2)", split the way the card sets it: the episode's own name as the headline, its series
 * and part below it, and its number on the frame's nameplate.
 */
@Immutable
internal data class HistoryTitle(val headline: String, val series: String?, val number: Int?)

internal fun historyTitle(title: String, episodeNumber: Int? = null): HistoryTitle {
    var rest = title.trim()
    val numbered = LeadingNumber.find(rest)
    val number = episodeNumber?.takeIf { it > 0 } ?: numbered?.groupValues?.get(1)?.toIntOrNull()
    if (numbered != null) rest = rest.substring(numbered.range.last + 1).trim()
    val part = TrailingPart.find(rest)
    if (part != null) rest = rest.substring(0, part.range.first).trim()
    val colon = rest.indexOf(':')
    val series = if (colon > 0) rest.substring(0, colon).trim() else null
    val headline = if (colon > 0) rest.substring(colon + 1).trim() else rest
    // "Part 2", as the title words it.
    val partLabel = part?.groupValues?.get(1)?.replace(Whitespace, " ")
    val below = listOfNotNull(series, partLabel).joinToString(", ").ifEmpty { null }
    return if (headline.isEmpty()) HistoryTitle(title.trim(), null, number) else HistoryTitle(headline, below, number)
}

private val LeadingNumber = Regex("""^(\d{1,5})\.\s+""")
private val TrailingPart = Regex("""\(\s*(Part\s+\d+)\s*\)\s*$""", RegexOption.IGNORE_CASE)
private val Whitespace = Regex("""\s+""")
