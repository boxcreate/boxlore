package cx.aswin.boxlore.feature.info.branded.conspiracy

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * What the evidence board pins up, all from the feed: the newest episode as a newspaper clipping,
 * the one before as a typed index card, the shortest of the next four as a handwritten sticky note,
 * and up to three more peeking in from the screen's edges. Any of them is null (or the list shorter)
 * when the feed has fewer episodes.
 */
@Immutable
internal data class BoardClues(val clipping: String?, val indexCard: String?, val sticky: String?, val peeks: List<String>)

/**
 * The headline a scrap shows: the part of the title before its colon ("Priests vs. Presidents: Who
 * Really…" → "Priests vs. Presidents"). A label before the colon ("BEST OF 2025: …") or a very short
 * one gives way to what follows it. A title without a colon shows whole.
 */
internal fun boardHeadline(title: String): String {
    val colon = title.indexOf(':')
    if (colon < 0) return title.trim()
    val before = title.substring(0, colon).trim()
    val after = title.substring(colon + 1).trim()
    val label = before.uppercase(Locale.ROOT).startsWith("BEST OF") || before.length < MIN_HEADLINE
    return when {
        label && after.isNotEmpty() -> after
        before.isNotEmpty() -> before
        else -> after
    }
}

/** The board's clues from episode [titles], newest first. */
internal fun boardClues(titles: List<String>): BoardClues {
    val heads = titles.take(CONSIDERED).map(::boardHeadline).filter(String::isNotBlank)
    val clipping = heads.getOrNull(0)
    val indexCard = heads.getOrNull(1)
    val sticky = heads.drop(2).take(STICKY_CHOICES).minByOrNull { it.length }
    val used = setOfNotNull(clipping, indexCard, sticky)
    val peeks = heads.drop(2).filterNot { it in used }.distinct().take(PEEKS)
    return BoardClues(clipping, indexCard, sticky, peeks)
}

private const val MIN_HEADLINE = 4
private const val CONSIDERED = 12
private const val STICKY_CHOICES = 4
private const val PEEKS = 3
