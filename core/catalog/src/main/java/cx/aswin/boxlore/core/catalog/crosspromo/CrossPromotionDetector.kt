package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser
import cx.aswin.boxlore.core.model.CrossPromotionConfidence
import cx.aswin.boxlore.core.model.CrossPromotionIndicator
import cx.aswin.boxlore.core.model.CrossPromotionResult
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.ShowNotes

/**
 * Decides whether an episode promotes another show and names it. The title's wording says how
 * likely that is ([TitleCue]); the episode's own artwork, a trailer's length and the show notes
 * corroborate it. [CrossPromotionResolver] still has to find the named show before anything shows.
 */
class CrossPromotionDetector {
    private val previewTitle = Regex(
        """(?i)^\W*(?:(?:a|an|the)\s+)?(?:special\s+|sneak\s+|exclusive\s+)?(?:preview|trailer|introducing|feed drop|sneak peek|announcing|""" +
            """coming soon|listen now|announcement|(?:(?:my|our)\s+)?(?:brand[- ])?new (?:show|podcast|series))\b|\b(?:preview|trailer|feed drop)\W*$""",
    )
    private val newShowTitle = Regex("""(?i)^\W*(?:(?:my|our)\s+)?(?:brand[- ])?new (?:show|podcast|series)\W*$""")
    private val lowercaseLead = Regex("""^(?:the|a|an|our|my|this|these|those|some|all)\s""")
    private val ownSeriesTitle = Regex("""(?i)\bour (?:new )?(?:\d+-part )?(?:series|season|episode|mini-?series)\b|\bnew season (?:trailer|teaser|preview)\b""")
    private val endsWithYear = Regex("""\b(?:19|20)\d{2}$""")

    private data class Signals(
        val text: String,
        val promoText: String,
        val short: Boolean,
        val otherArtwork: Boolean,
        val trailer: Boolean,
        val bonus: Boolean,
    )

    fun detect(episode: Episode, hostPodcastTitle: String, notes: ShowNotes = ShowNotesParser.parse(episode.description)): CrossPromotionResult {
        val signals = Signals(
            text = notes.plainText,
            promoText = "${episode.title}\n${notes.plainText}",
            short = episode.duration in 15..420,
            otherArtwork = otherArtwork(episode.imageUrl, episode.podcastImageUrl),
            trailer = episode.episodeType.equals("trailer", true),
            bonus = episode.episodeType.equals("bonus", true),
        )
        val candidate = CrossPromotionTitles.candidate(episode.title)
        if (candidate?.cue == TitleCue.EXCHANGE) return exchanged(candidate, episode.title, signals, hostPodcastTitle)
        if (ownSeriesTitle.containsMatchIn(episode.title) || CrossPromotionDescription.premium(episode.title)) return none()
        if (candidate != null) return titled(candidate, signals, hostPodcastTitle)
        return mentioned(episode.title, signals, hostPodcastTitle) ?: described(episode.title, notes, signals, hostPodcastTitle) ?: none()
    }

    /** "Feed Drop: Serial" names another show outright; only the host's own name is refused. */
    private fun exchanged(candidate: TitleCandidate, title: String, signals: Signals, host: String): CrossPromotionResult {
        if (CrossPromotionNames.sameShow(candidate.name, host)) return none()
        val supported = supportedName(candidate.name, signals) != null
        // "Feed Drop: Why Only 10% of Companies Succeed" names an episode; the notes or the rest of the title name its show.
        val named = if (supported) {
            null
        } else {
            CrossPromotionDescription.friendsName(signals.text)?.takeIf { !CrossPromotionNames.sameShow(it, host) }
                ?: CrossPromotionTitles.pieces(title).firstOrNull { !CrossPromotionNames.related(it, host) && CrossPromotionDescription.supports(signals.text, it) }
        }
        return promoted(named ?: candidate.name, CrossPromotionConfidence.HIGH, candidate.indicator, supported || named != null, signals)
    }

    /** A title that introduces [candidate], which the host also does for its own seasons and series. */
    private fun titled(candidate: TitleCandidate, signals: Signals, host: String): CrossPromotionResult {
        val name = candidate.name
        if (CrossPromotionNames.related(name, host) || notAShow(candidate)) return none()
        val supported = supportedName(name, signals) != null
        if (!supported && hostsOwn(candidate, signals, host)) return none()
        // "Ken Burns Presents Willa Cather’s America" and "Archive 81 Presents… The NoSleep Podcast" read alike, so presenters need evidence.
        val evidence = candidate.explicit || signals.otherArtwork || signals.short || signals.trailer
        if (!supported && (candidate.cue == TitleCue.WEAK || !evidence)) return none()
        val confidence = if (supported) CrossPromotionConfidence.HIGH else CrossPromotionConfidence.MEDIUM
        return promoted(name, confidence, candidate.indicator, supported, signals)
    }

    /** "INTRODUCING the RAMPAGE ROOKIES!" introduces people and "Announcing ROOTCON 2023!" an event; shows are named in title case. */
    private fun notAShow(candidate: TitleCandidate): Boolean =
        candidate.cue == TitleCue.SEAMLESS && (lowercaseLead.containsMatchIn(candidate.name) || endsWithYear.containsMatchIn(candidate.name))

    /** The host's own series ("Gangster Presents: The Story of Ronnie Biggs" in Gangster), season or premium preview. */
    private fun hostsOwn(candidate: TitleCandidate, signals: Signals, host: String): Boolean =
        candidate.presenter?.let { CrossPromotionNames.sameShow(it, host) } == true ||
            CrossPromotionDescription.premium(signals.promoText) ||
            hostOnly(signals.text, host, candidate.name)

    /** A title that is, or contains, the name of a show its notes and artwork describe as another show. */
    private fun mentioned(title: String, signals: Signals, host: String): CrossPromotionResult? {
        if (!signals.otherArtwork && !signals.short && !signals.trailer) return null
        if (CrossPromotionDescription.premium(signals.promoText)) return null
        val name = CrossPromotionTitles.pieces(title).firstOrNull { piece ->
            !CrossPromotionNames.related(piece, host) && CrossPromotionDescription.supports(signals.text, piece) && !hostOnly(signals.text, host, piece)
        } ?: return null
        return promoted(name, CrossPromotionConfidence.MEDIUM, CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE, supports = true, signals)
    }

    /** A preview whose title does not name the show but whose notes do. */
    private fun described(title: String, notes: ShowNotes, signals: Signals, host: String): CrossPromotionResult? {
        val preview = previewTitle.containsMatchIn(title)
        val corroborated = signals.trailer || preview && (signals.short || signals.otherArtwork || newShowTitle.containsMatchIn(title))
        if (!corroborated || CrossPromotionDescription.premium(signals.promoText)) return null
        val name = CrossPromotionDescription.friendsName(notes.plainText)?.takeIf { !CrossPromotionNames.sameShow(it, host) }
            ?: CrossPromotionDescription.name(notes, host) ?: return null
        if (CrossPromotionNames.related(name, host)) return null
        val confidence = if (signals.trailer) CrossPromotionConfidence.HIGH else CrossPromotionConfidence.MEDIUM
        return promoted(name, confidence, CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE, supports = true, signals)
    }

    /** The first form of [name] that the notes invite listeners to, if any. */
    private fun supportedName(name: String, signals: Signals): String? =
        CrossPromotionNames.variants(name).firstOrNull { CrossPromotionDescription.supports(signals.text, it) }

    /** The notes call the episode part of the host show ("a preview of the new season of You Must Remember This") and never name [name]. */
    private fun hostOnly(text: String, host: String, name: String): Boolean =
        CrossPromotionDescription.ownShow(text, host) && !CrossPromotionDescription.mentions(text, name)

    private fun promoted(
        name: String,
        confidence: CrossPromotionConfidence,
        indicator: CrossPromotionIndicator,
        supports: Boolean,
        signals: Signals,
    ): CrossPromotionResult = CrossPromotionResult(
        isCrossPromotion = true,
        confidence = confidence,
        extractedShowName = name,
        matchedIndicators = buildList {
            add(indicator)
            if (supports && indicator != CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE) add(CrossPromotionIndicator.DESCRIPTION_PROMO_LANGUAGE)
            if (signals.short) add(CrossPromotionIndicator.SHORT_DURATION)
            if (signals.trailer || signals.bonus) add(CrossPromotionIndicator.TRAILER_OR_BONUS_TYPE)
            if (signals.otherArtwork) add(CrossPromotionIndicator.DISTINCT_ARTWORK)
        },
    )

    private fun otherArtwork(episodeArt: String?, showArt: String?): Boolean {
        val episode = episodeArt?.substringBefore('?')?.trim().orEmpty()
        val show = showArt?.substringBefore('?')?.trim().orEmpty()
        return episode.isNotEmpty() && show.isNotEmpty() && !episode.equals(show, ignoreCase = true)
    }

    private fun none() = CrossPromotionResult(false, CrossPromotionConfidence.NONE, null, emptyList())

    companion object {
        internal fun normalizedName(text: String): String = CrossPromotionNames.normalized(text)
        internal fun sameShow(first: String, second: String): Boolean = CrossPromotionNames.sameShow(first, second)
    }
}
