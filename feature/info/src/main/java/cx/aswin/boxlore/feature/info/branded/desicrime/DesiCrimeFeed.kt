package cx.aswin.boxlore.feature.info.branded.desicrime

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.branded.brandedShowPicture
import cx.aswin.boxlore.feature.info.branded.episodeOwnPicture

/**
 * The Desi Crime Podcast's feed, read by fixed rules that work on any of its episodes. Its cases
 * are numbered: "212. The Most Hated Indian Killer In America | Mundill Mahil & Gagandip Singh".
 * The listeners' stories (Chai & Chithi) and specials carry no number. Every episode's picture
 * follows one template: the case's photo across the top, the DESI CRIME lettering under it, and a
 * torn edge above the hosts.
 */
internal object DesiCrimeFeed {
    /** A title's parts: the case number, the case, and what follows "|" (the people in it, or a guest). */
    fun case(title: String): DesiCrimeCase {
        val numbered = Numbered.find(title.trim())
        val number = numbered?.groupValues?.get(1)?.toIntOrNull()
        val rest = numbered?.groupValues?.get(2) ?: title.trim()
        val main = rest.substringBefore('|').trim()
        val after = rest.substringAfter('|', "").trim()
        return DesiCrimeCase(number, main.ifEmpty { rest.trim() }, after)
    }

    /**
     * A print's caption: the names after "|" (the case's people), or the case itself when there are
     * none, or when "|" names a guest ("Ft. …") rather than the case.
     */
    fun caption(case: DesiCrimeCase): String =
        case.after.takeUnless { it.isEmpty() || it.startsWith("Ft.", ignoreCase = true) } ?: case.main

    /** The board, newest first: the latest case and its picture, then earlier cases that have pictures of their own. */
    fun board(feed: List<Episode>, podcast: Podcast, prints: Int = DesiCrimeArt.MAX_PRINTS): DesiCrimeBoard {
        val cover = brandedShowPicture(podcast)
        val covers = listOf(podcast.imageUrl, podcast.fallbackImageUrl)
        val cases = feed.mapNotNull { episode -> case(episode.title).takeIf { it.number != null }?.let { episode to it } }
        val newest = cases.firstOrNull()
        val earlier = cases.drop(1).mapNotNull { (episode, case) ->
            episodeOwnPicture(episode, covers)?.let { picture -> DesiCrimePrint(episode.id, case.number ?: 0, caption(case), picture) }
        }
        return DesiCrimeBoard(
            newestNumber = newest?.second?.number,
            // A case whose picture is the show's cover shows the cover's top: the same template.
            newestPicture = newest?.first?.let { episodeOwnPicture(it, covers) } ?: cover,
            cover = cover,
            prints = earlier.take(prints),
        )
    }

    private val Numbered = Regex("""^(\d+)\.\s+(.*)$""")
}

/** One title's parts ([DesiCrimeFeed.case]). */
@Immutable
internal data class DesiCrimeCase(val number: Int?, val main: String, val after: String)

/** An earlier case pinned on the board: its number, its caption and its own picture. */
@Immutable
internal data class DesiCrimePrint(val id: String, val number: Int, val caption: String, val picture: String)

/** What the board shows from the feed ([DesiCrimeFeed.board]). */
@Immutable
internal data class DesiCrimeBoard(val newestNumber: Int?, val newestPicture: String?, val cover: String?, val prints: List<DesiCrimePrint>)
