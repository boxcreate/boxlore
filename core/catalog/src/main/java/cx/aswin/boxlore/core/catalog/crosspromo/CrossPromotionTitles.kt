package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.model.CrossPromotionIndicator

/** How much a title's wording says about the episode belonging to another show. */
internal enum class TitleCue {
    /** Feed drops, swaps, recommendations and "New show:": the wording itself points at another show. */
    EXCHANGE,

    /** Introductions, trailers and presentations, which shows also use for their own seasons. */
    INTRODUCTION,

    /** "Introducing Ice Lions" without a delimiter, which also introduces guests and topics. */
    SEAMLESS,

    /** Words that lead into a topic or a guest more often than into a show. */
    WEAK,
}

internal data class TitleCandidate(
    val name: String,
    val cue: TitleCue,
    val indicator: CrossPromotionIndicator,
    val presenter: String? = null,
    /** "Introducing: X" with a delimiter, rather than a trailer label or a presenter. */
    val explicit: Boolean = false,
)

/** Finds the show an episode title promotes, and how the title says so. */
internal object CrossPromotionTitles {
    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)
    private const val Delimiter = """\s*(?:[:|–—\-!]|\.{2,}|…)(?:[\s:|–—\-!]|\.{2,}|…)*"""
    private val leadingNumber = Regex("""^(?:(?:episode|ep\.?)\s*)?#?\d+\s*[.):|–—\-]+\s+""", ignoreCase)

    // "Special Bonus - Introducing: Civilian or Spy" wraps an introduction in the feed's own label.
    private val leadingLabel = Regex(
        """^[\[(]?(?:special\s+)?(?:bonus(?:\s+episode)?|special)[\])]?\s*[:|–—\-]+\s*(?=(?:introducing|announcing|presenting|listen now|new show|new podcast)\s*[:|–—\-])""",
        ignoreCase,
    )

    private fun prefixed(cues: String) = Regex("""^[\[(*]?(?:$cues)[\])*]?$Delimiter(.+)$""", ignoreCase)
    private fun suffixed(cues: String) =
        Regex("""^(.+?)\s*(?:[:|–—\-]+|[(\[])\s*(?:season\s+\d+\s*[:|–—\-]?\s*)?(?:$cues)\s*[\])!]*$""", ignoreCase)

    private val exchange = prefixed(
        """feed drop|feed swap|feed share|trailer swap|promo swap|promo drop|bonus drop|guest feed|companion show|""" +
            """network premiere|cross.?promo|promo episode|what to listen to next|we recommend|(?:brand )?new (?:show|podcast)(?: announcement| alert)?|""" +
            """(?:our|my) new (?:show|podcast)|(?:[\w&+'’.]+\s+){1,4}feed (?:drop|swap)|(?:[\w'’.]+\s+){1,3}recommends""",
    )
    private val presentedBy = Regex(
        """^(?:presented by|brought to you by|from the (?:creators|makers|team|producers|people) (?:of|behind))\s+[^:|]+[:|]\s*(.+)$""",
        ignoreCase,
    )
    private val introduction = prefixed(
        """introducing|announcing|presenting|presents|listen now|now listen|out now|available now|sneak peek|""" +
            """sneak preview|(?:a )?special preview|first look|(?:brand )?new series|just launched|""" +
            """(?:a\s+)?(?:brand[- ])?new\s+(?:show|podcast|series)\s+from\s+[^:|–—]+?""",
    )
    private val possessiveNewShow = Regex(
        """^(?:[\p{L}&.]+(?:\s+[\p{L}&.]+){0,3}['’]s\s+)?(?:brand[- ])?new\s+(?:podcast|show|series|podcast series|audio series)""" +
            """(?:\s+(?:about|on)\s+[^:|]{1,40})?$Delimiter(.+)$""",
        ignoreCase,
    )
    private val newShowCalled = Regex("""\bnew\s+(?:podcast|show|series)\s+(?:called|titled|named)\s+(.+)$""", ignoreCase)
    private val mightLike = Regex("""^(?:i think\s+)?you(?:['’]ll| will| might| may)\s+(?:also\s+)?(?:like|love|enjoy)\s+(.+)$""", ignoreCase)
    private val comingSoon = prefixed("""coming soon""")
    private val listenTo = Regex(
        """^(?i:listen\s+to)\s+(?!(?i:this|that|these|those|my|me|us|you|your|it|what|why|how|when|if|before|after)\b)(\p{Lu}.+)$""",
    )
    private val mix = Regex("""\b(?:dj|mix|freemix|remix|mixtape|live set|radio show)\b|\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}\b""", ignoreCase)
    private val numbered = Regex("""\b(?:episode|ep\.?|nr\.?|no\.|vol\.?)\s*\d+|#\d+""", ignoreCase)
    private val quotedIntroduction = Regex("""^(?:introducing|announcing|presenting|listen to|check out)\s+(["“‘'].+)$""", ignoreCase)
    private val newSeason = Regex("""^(?:(?:a|the)\s+)?(?:brand[- ])?new season of\s+(.+)$""", ignoreCase)
    private val networkPresents = Regex(
        """^(?!(?:introducing|announcing|presenting|listen|check out|bonus|trailer|preview)\b)([^:|]{2,40}?)\s+(?:presents|presenting)(?:$Delimiter|\s+)(.+)$""",
        ignoreCase,
    )
    private val weak = prefixed(
        """discover|meet|check out|try|sample|preview|(?:official )?trailer|teaser|bonus|bonus episode|special episode|spotlight|listen|""" +
            """(?:special |official )?announcement""",
    )
    private val exchangeSuffix = suffixed("""feed drop|feed swap|trailer swap|promo swap""")
    private val newShowSuffix = suffixed(
        """(?:episode\s+\w+\s+of\s+)?(?:an?\s+)?(?:brand\s+)?new\s+(?:weekly\s+)?(?:podcast|show|series)(?:\s+from\s+.+)?""",
    )
    private val introductionSuffix = suffixed("""(?:official\s+)?(?:trailer|teaser)|coming soon""")
    private val trailerSuffix = Regex("""^(.+?)(?<!podcast)\s+(?:season\s+\d+\s+)?(?:official\s+)?trailer$""", ignoreCase)
    private val weakSuffix = Regex("""^(.+?)\s+(?:returns|is back)[!.]*$""", ignoreCase)
    private val fromFriends = Regex("""\bfrom our friends (?:at|over at)\s+(?:the\s+(?:podcast|show)\s+)?(.+?)[!.]*$""", ignoreCase)
    private val seamless = Regex(
        """^(?:introducing|announcing|presenting)\s+(?!(?:to|you|your|yourself|season|episode|new|the new|a new|me|us|him|her|them)\b)(.+)$""",
        ignoreCase,
    )
    private val pieceBreak = Regex("""\s*[|:–—]\s*|\s+-\s+""")
    private val quotedPiece = Regex("""["“‘]([^"”’]{3,90})["”’]""")
    private val inPiece = Regex("""\s+in\s+(.{3,90})$""")

    private class Rule(
        val regex: Regex,
        val cue: TitleCue,
        val indicator: CrossPromotionIndicator,
        val group: Int = 1,
        val explicit: Boolean = false,
    )

    private val rules = listOf(
        Rule(exchange, TitleCue.EXCHANGE, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(presentedBy, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_PRESENTS_PATTERN),
        Rule(introduction, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN, explicit = true),
        Rule(quotedIntroduction, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING, explicit = true),
        Rule(possessiveNewShow, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN, explicit = true),
        Rule(newShowCalled, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN, explicit = true),
        Rule(mightLike, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN, explicit = true),
        Rule(comingSoon, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(newSeason, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(networkPresents, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_PRESENTS_PATTERN, group = 2),
        Rule(weak, TitleCue.WEAK, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(exchangeSuffix, TitleCue.EXCHANGE, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(newShowSuffix, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN, explicit = true),
        Rule(introductionSuffix, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(trailerSuffix, TitleCue.INTRODUCTION, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(weakSuffix, TitleCue.WEAK, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(fromFriends, TitleCue.EXCHANGE, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN),
        Rule(seamless, TitleCue.SEAMLESS, CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING),
        Rule(listenTo, TitleCue.SEAMLESS, CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING),
    )

    /** The first rule whose wording matches [title] and leaves a usable show name. */
    fun candidate(title: String): TitleCandidate? {
        val text = title.trim().replace(Regex("\\s+"), " ").replaceFirst(leadingNumber, "").replaceFirst(leadingLabel, "")
        return rules.firstNotNullOfOrNull { rule ->
            val anywhere = rule.regex == fromFriends || rule.regex == newShowCalled
            val match = (if (anywhere) rule.regex.find(text) else rule.regex.matchEntire(text)) ?: return@firstNotNullOfOrNull null
            val presenter = if (rule.regex == networkPresents) match.groupValues[1].trim() else null
            // "Deep Ghosh presents United We Rise Nr. 197" is a numbered mix series, not a show being introduced.
            val numberedSeries = presenter != null && (numbered.containsMatchIn(text) || mix.containsMatchIn(text))
            if (presenter != null && (numberedSeries || presenter.split(' ').size > 4)) return@firstNotNullOfOrNull null
            CrossPromotionNames.clean(match.groupValues[rule.group])?.let { TitleCandidate(it, rule.cue, rule.indicator, presenter, rule.explicit) }
        }
    }

    /** Names a title could be carrying without any promotional wording: the whole title, quotes and parts. */
    fun pieces(title: String): List<String> {
        val text = title.trim().replace(Regex("\\s+"), " ")
        val raw = listOf(text) + quotedPiece.findAll(text).map { it.groupValues[1] } +
            listOfNotNull(inPiece.find(text)?.groupValues?.get(1)) + text.split(pieceBreak)
        return raw.mapNotNull(CrossPromotionNames::clean).distinctBy(CrossPromotionNames::normalized)
    }
}
