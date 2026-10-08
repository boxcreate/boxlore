package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser
import cx.aswin.boxlore.core.model.CrossPromotionConfidence
import cx.aswin.boxlore.core.model.CrossPromotionIndicator
import cx.aswin.boxlore.core.model.CrossPromotionResult
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.core.model.ShowNotes
import java.util.Locale

class CrossPromotionDetector {
    private val strict = Regex("""^(?:\[|\(|\*)?(feed drop|trailer swap|promo drop|bonus drop|feed swap|feed share|promo swap|guest feed|companion show|network premiere|cross.?promo|promo episode)(?:\]|\)|\*)?\s*[:–—|\-]+\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val introducing = Regex("""^(?:\[|\(|\*)?(introducing|listen now|listen to|special preview|sneak peek|we recommend)(?:\]|\)|\*)?\s*[:–—|\-]+\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val weak = Regex("""^(?:discover|meet|check out|announcing|try|sample|preview|(?:brand )?(?:new|next) (?:season|sesson|seaton)|sesson)\s*[:–—|\-]+\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val presents = Regex("""^(?:.+\s)?(?:presents|presenting|presented by|from the creators of|from the makers of|from the team behind|brought to you by)(?:\s+[^:|]+)?\s*[:|]\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val seamless = Regex("""^introducing\s+(?!season\b)(.+)$""", RegexOption.IGNORE_CASE)
    private val promoLanguage = Regex("""\b(?:subscribe to|listen to|check out|follow|introducing|new podcast|new show|feed drop|trailer swap)\b""", RegexOption.IGNORE_CASE)
    private val recommendation = Regex("""\b(?:subscribe\s+to|listen\s+to|check\s+out|follow|introducing)\s+""", RegexOption.IGNORE_CASE)
    private val showPrefix = Regex("""^(?:our new podcast|our podcast|the podcast|new podcast|our new show|our show|the show|new show)\s+""", RegexOption.IGNORE_CASE)
    private val continuation = Regex("""^\s+(?:wherever|where you|on|for|every|daily|weekly|now|today|as)\b""", RegexOption.IGNORE_CASE)
    private val quoted = Regex("""(?:subscribe\s+to|listen\s+to|check\s+out|follow|introducing)(?:\s+(?:our|the|new|podcast|show|series))*\s+["“‘']([^"”’']{3,120})["”’']""", RegexOption.IGNORE_CASE)
    private val named = Regex("""(?:subscribe to|listen to|check out|follow)(?:\s+(?:our new podcast|our podcast|the podcast|our new show|our show))*\s+(.{3,120}?)(?=\s+(?:wherever|where you|on Apple|on Spotify|for more|for new|every\s+(?:week|day|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b)|[!\n]|$)""", RegexOption.IGNORE_CASE)
    private val seasonOnly = Regex("""(?i)^(?:(?:brand )?(?:new|next) )?(?:season|series|episode|part|s)\s*\d*$""")

    private data class TitleCandidate(val name: String, val indicator: CrossPromotionIndicator, val weak: Boolean = false)
    private data class Evidence(val title: TitleCandidate?, val descriptionSupports: Boolean, val short: Boolean, val trailer: Boolean, val bonus: Boolean, val previewTitle: Boolean)

    fun detect(episode: Episode, hostPodcastTitle: String, notes: ShowNotes = ShowNotesParser.parse(episode.description)): CrossPromotionResult {
        val title = episode.title.trim()
        strict.matchEntire(title)?.let {
            return result(it.groupValues[2], hostPodcastTitle, CrossPromotionConfidence.HIGH, CrossPromotionIndicator.TITLE_DELIMITER_PATTERN)
        }
        val descriptionName = descriptionName(notes, hostPodcastTitle)
        val candidate = titleCandidate(title)?.takeIf { isOtherShow(it.name, hostPodcastTitle) }
        val name = candidate?.name ?: descriptionName ?: return none()
        val evidence = Evidence(
            candidate,
            descriptionName?.let { sameShow(it, name) } == true || candidate?.let { descriptionPromotes(notes, it.name) } == true,
            episode.duration in 30..300,
            episode.episodeType.equals("trailer", true),
            episode.episodeType.equals("bonus", true),
            Regex("(?i)\\b(?:preview|trailer|introducing|feed drop)\\b").containsMatchIn(title),
        )
        if (!supported(evidence)) return none()
        val confidence = if (evidence.descriptionSupports && (evidence.trailer || candidate != null)) CrossPromotionConfidence.HIGH else CrossPromotionConfidence.MEDIUM
        return CrossPromotionResult(true, confidence, name, indicators(evidence))
    }

    private fun descriptionName(notes: ShowNotes, host: String): String? = notes.links.firstNotNullOfOrNull { link ->
        link.title?.takeIf { link.kind == EpisodeLinkKind.PODCAST && promoLanguage.containsMatchIn(link.context) && isOtherShow(it, host) }
    } ?: notes.plainText.lines().firstNotNullOfOrNull { line ->
        val name = quoted.find(line)?.groupValues?.get(1) ?: named.find(line)?.groupValues?.get(1)
        name?.let(::clean)?.takeIf { isOtherShow(it, host) }
    }

    private fun descriptionPromotes(notes: ShowNotes, name: String): Boolean {
        val words = normalizedName(name).split(' ').filter(String::isNotBlank)
        if (words.isEmpty()) return false
        val pattern = words.joinToString("[^\\p{L}\\p{N}]+") { if (it == "and") "(?:and|&)" else Regex.escape(it) }
        val wholeName = Regex("""^["“‘']?$pattern(?=$|[^\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
        return notes.plainText.lines().any { line ->
            recommendation.findAll(line).any phrase@{ phrase ->
                val original = line.substring(phrase.range.last + 1)
                val tail = original.takeIf { wholeName.containsMatchIn(it) } ?: original.replaceFirst(showPrefix, "")
                val match = wholeName.find(tail) ?: return@phrase false
                val rest = tail.substring(match.range.last + 1).trimStart('"', '”', '’', '\'')
                rest.isBlank() || rest.trimStart().firstOrNull() in listOf('.', '!', '?', ',', ';', ':') || continuation.containsMatchIn(rest)
            }
        }
    }

    private fun titleCandidate(title: String): TitleCandidate? {
        introducing.matchEntire(title)?.let { return TitleCandidate(clean(it.groupValues[2]), CrossPromotionIndicator.TITLE_DELIMITER_PATTERN) }
        weak.matchEntire(title)?.let { return TitleCandidate(clean(it.groupValues[1]), CrossPromotionIndicator.TITLE_DELIMITER_PATTERN, weak = true) }
        presents.matchEntire(title)?.let { return TitleCandidate(clean(it.groupValues[1]), CrossPromotionIndicator.TITLE_PRESENTS_PATTERN) }
        return seamless.matchEntire(title)?.let { TitleCandidate(clean(it.groupValues[1]), CrossPromotionIndicator.TITLE_SEAMLESS_INTRODUCING) }
    }

    private fun supported(evidence: Evidence): Boolean {
        // Missing numbering and a Bonus tag are common metadata, not promotion evidence.
        if (evidence.title?.weak == true) return evidence.descriptionSupports
        val corroborated = evidence.trailer || evidence.short || evidence.descriptionSupports
        return evidence.title != null && corroborated || evidence.descriptionSupports && (evidence.trailer || evidence.short && evidence.previewTitle)
    }

    private fun indicators(evidence: Evidence): List<CrossPromotionIndicator> = buildList {
        evidence.title?.let { add(it.indicator) }
        if (evidence.descriptionSupports) add(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE)
        if (evidence.short) add(CrossPromotionIndicator.SHORT_DURATION)
        if (evidence.trailer || evidence.bonus) add(CrossPromotionIndicator.TRAILER_OR_BONUS_TYPE)
    }

    private fun result(name: String, host: String, confidence: CrossPromotionConfidence, indicator: CrossPromotionIndicator): CrossPromotionResult {
        val cleanName = clean(name)
        return if (isOtherShow(cleanName, host)) CrossPromotionResult(true, confidence, cleanName, listOf(indicator)) else none()
    }

    private fun isOtherShow(name: String, host: String): Boolean = name.length in 3..120 && !seasonOnly.matches(name) && !sameShow(name, host) && !Regex("(?i)^(?:us|me|our|the show|this show|our show|this podcast|our podcast)(?:\\s|$)").containsMatchIn(name) && !name.contains("://")
    private fun clean(name: String): String = name.trim().trim('"', '\'', '“', '”', '‘', '’', '*').replace(Regex("\\s+"), " ")
    private fun none() = CrossPromotionResult(false, CrossPromotionConfidence.NONE, null, emptyList())

    companion object {
        internal fun normalizedName(text: String): String = text.lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().removeSuffix(" podcast").trim()
        internal fun sameShow(first: String, second: String): Boolean = normalizedName(first) == normalizedName(second)
    }
}
