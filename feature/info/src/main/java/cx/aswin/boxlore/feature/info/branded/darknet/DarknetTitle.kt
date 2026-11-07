package cx.aswin.boxlore.feature.info.branded.darknet

import androidx.compose.runtime.Immutable

/** "182: Blackshades" as the card sets it: the name in capitals, the number in the label above it. */
@Immutable
internal data class DarknetTitle(val headline: String, val number: Int?)

internal fun darknetTitle(title: String, episodeNumber: Int? = null): DarknetTitle {
    val trimmed = title.trim()
    val numbered = LeadingNumber.find(trimmed)
    val number = episodeNumber?.takeIf { it > 0 } ?: numbered?.groupValues?.get(1)?.toIntOrNull()
    val headline = numbered?.let { trimmed.substring(it.range.last + 1).trim() }?.takeIf(String::isNotEmpty) ?: trimmed
    return DarknetTitle(headline, number)
}

private val LeadingNumber = Regex("""^(?:ep(?:isode)?\.?\s*)?(\d{1,4})\s*[:.\-–]\s*""", RegexOption.IGNORE_CASE)
