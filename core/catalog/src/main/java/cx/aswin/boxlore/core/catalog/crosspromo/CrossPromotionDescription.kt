package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.core.model.ShowNotes
import java.util.Locale

/** What an episode's show notes say about a promoted show. */
internal object CrossPromotionDescription {
    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)
    private const val Quote = """["“”‘’'«»]?"""
    private const val Invitation = """(?:follow|subscribe(?:\s+to)?|listen(?:\s+to)?|check\s+out|search(?:\s+for)?|find|introducing|""" +
        """introduce\s+you\s+to|preview\s+of|trailer\s+for|(?:first\s+)?episodes?(?:\s+\d+)?\s+of|season(?:\s+\w+)?\s+of|""" +
        """friends\s+(?:at|from|over\s+at)|called|titled|named|presenting|announcing|enjoy|binge|(?:you['’]ll|you\s+will|gonna)\s+love)"""
    private const val Filler = """(?:(?:the|our|my|their|new|brand[- ]new|all[- ]new|podcast|show|series|original)\s+){0,3}"""
    private const val ShowNoun = """(?:podcast|show|series|drama|docuseries|original)"""
    private const val Continuation = """(?=\s*(?:$|[.!?,;:)\]—–\-]|(?:wherever|where|on|for|every|each|daily|weekly|now|today|as|in|at|from|is|was|""" +
        """and|are|available|starting|out|anywhere|via|here|by|with|this|right|to|podcast)\b))"""
    private val premiumMarkers = listOf(
        Regex("""\bpatr(?:eon|ons?)\b""", ignoreCase),
        Regex("""\bpremium\b""", ignoreCase),
        Regex("""\b(?:supporters?|members?|subscribers?)[- ]only\b""", ignoreCase),
        Regex("""\bpaid subscribers?\b""", ignoreCase),
        Regex("""\bunlock (?:the )?full\b""", ignoreCase),
        Regex("""\bfull episode (?:on|at)\b""", ignoreCase),
    )

    private val promoLanguage = Regex("""\b(?:subscribe to|listen to|check out|follow|introducing|new podcast|new show|feed drop|trailer swap)\b""", ignoreCase)
    private val recommendation = Regex("""\b(?:subscribe\s+to|listen\s+to|check\s+out|follow|introducing)\s+""", ignoreCase)
    private val quotedName = Regex("""^(?:(?:our|the|new|podcast|show|series)\s+)*["“‘']([^"”’']{3,120})["”’']""", ignoreCase)
    private val namedRecommendation = Regex("""\b(?:subscribe to|listen to|check out|follow)\s+""", ignoreCase)
    private val namedPrefix = Regex("""^(?:(?:our new podcast|our podcast|the podcast|our new show|our show)\s+)*""", ignoreCase)
    private val newShow = Regex("""\b(?:my|our)\s+(?:brand[- ])?new\s+(?:show|podcast|series)\s*[,:]?\s+""", ignoreCase)
    private val nameBoundaries = listOf(
        Regex("""\s+(?:wherever|where you|on Apple|on Spotify|for more|for new)""", ignoreCase),
        Regex("""\s+every\s+(?:week|day|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b""", ignoreCase),
        Regex("""[!\n]|:\s*https?://|\s+https?://|\s+and\s+(?:follow|subscribe|listen|check)\b""", ignoreCase),
    )

    private val friendsAt = Regex("""\b(?:from|by|of)\s+our\s+friends\s+(?:over\s+)?(?:at|from)\s+""", ignoreCase)
    private val friendsNameLead = Regex("""^(?:the\s+)?(?:podcast\s+)?""", ignoreCase)
    private val friendsNameEnds = setOf('.', ',', '!', '?', '\n', ':', '(')
    private val friendsStopWords = setOf("who", "where", "and", "in", "on", "about", "for", "called", "with")

    /** The show named in "an episode from our friends at Modern CTO", the clearest way notes name another show. */
    fun friendsName(text: String): String? {
        val phrase = friendsAt.find(text) ?: return null
        val tail = text.substring(phrase.range.last + 1).replaceFirst(friendsNameLead, "")
        val words = tail.takeWhile { it !in friendsNameEnds }.trim().split(Regex("\\s+"))
        val nameWords = words.take(1) + words.drop(1).takeWhile { it.lowercase(Locale.ROOT) !in friendsStopWords }
        val name = nameWords.dropLastWhile { it.equals("podcast", ignoreCase = true) }.joinToString(" ")
        return name.takeIf { it.length in 2..61 && it.first().isUpperCase() }?.let(CrossPromotionNames::clean)
    }

    /** Whether [text] invites listeners to [name] or describes [name] as a show. */
    fun supports(text: String, name: String): Boolean {
        val show = CrossPromotionNames.pattern(name) ?: return false
        val named = "$Quote$show$Quote"
        return listOf(
            """\b$Invitation\s+$Filler$named$Continuation""",
            """$named,?\s+(?:is|was)\s+(?:a|an|the|available|out)\b""",
            """$named,\s+(?:a|an|the)\s+(?:[\w-]+\s+){0,4}$ShowNoun""",
            """\b$ShowNoun,?\s+$named""",
        ).any { Regex(it, ignoreCase).containsMatchIn(text) }
    }

    /** Whether [text] places the episode inside [host]: its season, series or episode, or the host show returning or investigating. */
    fun ownShow(text: String, host: String): Boolean {
        val show = CrossPromotionNames.pattern(host) ?: return false
        return listOf(
            """\b(?:season|series|episode|chapter|installment)s?\s+(?:[\w-]+\s+){0,2}of\s+$Quote$show""",
            """$show$Quote(?:['’]s)?\s+(?:(?:new|next|latest|upcoming)\s+)?(?:season|series|returns|is back|investigates|explores|presents)\b""",
            """\bwelcome\s+(?:back\s+)?to\s+$show""",
        ).any { Regex(it, ignoreCase).containsMatchIn(text) }
    }

    fun mentions(text: String, name: String): Boolean =
        CrossPromotionNames.pattern(name)?.let { Regex(it, ignoreCase).containsMatchIn(text) } == true

    fun premium(text: String): Boolean = premiumMarkers.any { it.containsMatchIn(text) }

    /** A show name the notes invite listeners to, from a labelled podcast link or an invitation. */
    fun name(notes: ShowNotes, host: String): String? = notes.links.firstNotNullOfOrNull { link ->
        link.title?.takeIf { link.kind == EpisodeLinkKind.PODCAST && promoLanguage.containsMatchIn(link.context) && isOther(it, host) }
    } ?: notes.plainText.lines().firstNotNullOfOrNull { line ->
        lineName(line)?.let(CrossPromotionNames::clean)?.takeIf { isOther(it, host) && !it.first().isLowerCase() }
    }

    private fun isOther(name: String, host: String) = CrossPromotionNames.plausible(name) && !CrossPromotionNames.sameShow(name, host)

    private fun lineName(line: String): String? {
        recommendation.findAll(line).firstNotNullOfOrNull { action ->
            quotedName.find(line.substring(action.range.last + 1))?.groupValues?.get(1)
        }?.let { return it }
        val invitations = namedRecommendation.findAll(line) + newShow.findAll(line)
        return invitations.firstNotNullOfOrNull { action ->
            val tail = line.substring(action.range.last + 1).replaceFirst(namedPrefix, "")
            val end = nameBoundaries.mapNotNull { it.find(tail)?.range?.first }.minOrNull() ?: tail.length
            tail.take(end).takeIf { it.length in 3..120 }
        }
    }
}
