package cx.aswin.boxlore.core.catalog.crosspromo

import java.util.Locale

/**
 * Turns the text around a promoted show's name into the name itself, and compares names with the
 * host show. Episode titles decorate names with seasons, episode numbers, trailer labels, studios
 * and hosts ("Blood Trails, Season Three", "'Back Issue' from Pineapple Street Studios").
 */
internal object CrossPromotionNames {
    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)
    private const val Numbers = """(?:\d+|one|two|three|four|five|six|seven|eight|nine|ten)"""
    private val quoted = Regex("""^["“‘'«]([^"”’'»]{2,120})["”’'»]""")
    private val leadingNumber = Regex("""^(?:(?:episode|ep\.?)\s*)?#?\d+\s*[.):|–—\-]+\s+""", ignoreCase)
    private val leadingFillers = listOf(
        Regex("""^(?:(?:an?|our|my|the|this)\s+)?(?:(?:brand|all)[- ]?new\s+|new\s+)(?:weekly\s+|daily\s+|original\s+)?(?:podcast|show|series|audio drama|season)(?:\s+of)?\s*[,:–—\-]?\s*""", ignoreCase),
        Regex("""^(?:an?\s+|the\s+)?(?:special\s+|first\s+)?(?:preview|trailer|sneak peek|taste|episode)\s+(?:of|for|from)\s+""", ignoreCase),
        Regex("""^(?:the\s+)?(?:season|series)\s+$Numbers\s+of\s+""", ignoreCase),
        Regex("""^[\p{L}.]+['’]s\s+(?:(?:brand[- ])?new\s+)(?:podcast|show|series)\s*[,:–—\-]?\s*""", ignoreCase),
        Regex("""^(?:our|my)\s+(?:podcast|show|series)\s*[,:–—\-]\s*""", ignoreCase),
        Regex("""^(?:the\s+)?(?:first\s+)?episodes?\s+\d+(?:\s*(?:&|and|,|-)\s*\d+)*\s+of\s+""", ignoreCase),
    )

    // Where a name gives way to an episode title or a tagline. Searched from the second character so
    // a name that starts with a bracket ("[REDACTED]") survives.
    private val cut = Regex("""\s*\||\s+[-–]\s+|\s*—\s*|\s+[(\[]|\.{3,}|…|!\s+|\s+/\s+|\s*;""")
    private val trailing = listOf(
        Regex(""",?\s*[\p{L}.]+['’]s\s+(?:(?:brand|all)[- ]?)?new\s+.*$""", ignoreCase),
        Regex("""\s+(?:is|are)\s+(?:back|here|coming|returning|live|out)\b.*$""", ignoreCase),
        Regex("""\s+presents?$""", ignoreCase),
        Regex("""[\s,:;|(\[–—\-]*\b(?:(?:season|series|book|volume|vol\.?|part|chapter|ep\.?|eps|episode)\s*#?$Numbers|s\d+(?:e\d+)?)\b.*$""", ignoreCase),
        Regex("""\s*#\d+.*$"""),
        Regex("""[\s:]+\d+(?:\.\d+)?\s*[:.]\s+.*$"""),
        Regex("""[\s,:;|(\[–—\-]*\b(?:official\s+)?(?:trailer|teaser|preview|sneak peek|promo)(?:\s+swap)?[\s)\]]*$""", ignoreCase),
        Regex("""[\s,:;|–—\-]*\b(?:is\s+)?(?:out now|available now|now available|coming soon|listen now|is here)[\s!.]*$""", ignoreCase),
        Regex(""",\s*(?:an?|the)\s+(?:[\w-]+\s+){0,3}(?:podcast|show|series|audio drama|docu-?series|original)\b.*$""", ignoreCase),
        Regex("""\s+(?:an?\s+)?(?:(?:brand|all)[- ]?new|new)\s+(?:[\w-]+\s+){0,2}(?:podcast|show|series|audio drama|docu-?series)\b.*$""", ignoreCase),
        Regex("""\s+(?:docu-?series|podcast series|audio series)\b.*$""", ignoreCase),
        Regex("""\s+from\s+(?:the\s+)?(?:makers|creators|team|producers|people|folks)\b.*$""", ignoreCase),
        Regex("""\s+from\s+[^,]*\b(?:studios?|media|productions?|network|podcasts|radio|news|audio|entertainment|originals?|npr|bbc|wondery|audible|iheart(?:radio)?|spotify|cbc|abc|pbs)\b.*$""", ignoreCase),
        Regex("""\s+(?:hosted by|starring|featuring|feat\.|presented by)\s+.+$""", ignoreCase),
        Regex(""",\s*(?:by|with|hosted by|starring|from)\s+.+$""", ignoreCase),
        Regex("""[\s,:;|–—\-!.…*]+$"""),
    )
    private val withHost = Regex("""^(.+?)\s+with\s+\S.*$""", ignoreCase)
    private val notAName = listOf(
        Regex("""^(?:us|me|the show|this show|this podcast|our (?:new )?(?:show|podcast|series|season|episode)|this week|patreon|premium)(?:\s|$)""", ignoreCase),
        Regex("""^(?:season|series|episode|ep|part|chapter|s)\s*(?:#?\d|$Numbers\b|$)""", ignoreCase),
        Regex("""^(?:a|the)\s+new\s+(?:season|episode|series|show|podcast)\b""", ignoreCase),
        Regex("""^(?:(?:brand )?(?:new|next) )?(?:season|series|episode|part|s)\s*\d*$""", ignoreCase),
        Regex("""\b(?:episode|episodes|trailer|preview|bonus|reaction|recap)$""", ignoreCase),
        Regex("""^(?:feed drop|feed swap|trailer swap|(?:special )?crossover|announcement|new show|new podcast|teaser)$""", ignoreCase),
    )

    /** The show name inside [raw], or null when what is left cannot be a show's name. */
    fun clean(raw: String): String? {
        var name = raw.trim().replace(Regex("\\s+"), " ").replaceFirst(leadingNumber, "")
        leadingFillers.forEach { name = name.replaceFirst(it, "") }
        quoted.find(name)?.let { name = it.groupValues[1] }
        cut.takeIf { name.length > 1 }?.find(name, 1)?.let { name = name.take(it.range.first) }
        var previous: String
        do {
            previous = name
            trailing.forEach { name = name.replaceFirst(it, "") }
        } while (name != previous && name.isNotEmpty())
        name = name.trim().trim('"', '\'', '“', '”', '‘', '’', '*', '«', '»').trim()
        return name.takeIf(::plausible)
    }

    /** [name] and the shorter names it may stand for: before a colon ("Fiasco: Benghazi") or a host ("NeferTV with Ann Foster"). */
    fun variants(name: String): List<String> = listOfNotNull(
        name,
        name.substringBefore(':').trim().takeIf { ':' in name },
        withHost.find(name)?.groupValues?.get(1)?.trim(),
    ).filter(::plausible).distinct()

    fun plausible(name: String): Boolean = name.length in 3..90 &&
        name.split(' ').size <= 12 &&
        name.any(Char::isLetter) &&
        !name.contains("://") &&
        notAName.none { it.containsMatchIn(name) }

    /**
     * Whether [name] is the host show or one of its own series: the same name, or one name starting
     * or ending with the other ("Nightingale Halloween" in Nightingale, "Insomniac" in Monster
     * Presents: Insomniac). Explicit feed exchanges use [sameShow] instead.
     */
    fun related(name: String, host: String): Boolean {
        val first = normalized(name)
        val second = normalized(host)
        if (first.isEmpty() || second.isEmpty()) return false
        val (shorter, longer) = if (first.length <= second.length) first to second else second to first
        return shorter == longer || longer.startsWith("$shorter ") || longer.endsWith(" $shorter")
    }

    fun sameShow(first: String, second: String): Boolean = normalized(first) == normalized(second)

    fun normalized(text: String): String = text.lowercase(Locale.ROOT)
        .replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        .removePrefix("the ")
        .removeSuffix(" podcast").trim()

    /** A pattern matching [name] in running text, however its words are punctuated. */
    fun pattern(name: String): String? {
        val words = normalized(name).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return null
        val gap = """[^\p{L}\p{N}]+"""
        val body = words.joinToString(gap) { if (it == "and") "(?:and|&)" else Regex.escape(it) }
        return """(?<![\p{L}\p{N}])(?:the$gap)?$body(?:${gap}podcast)?(?![\p{L}\p{N}])"""
    }
}
