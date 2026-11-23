package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.PodcastFeed
import cx.aswin.boxlore.core.network.model.SearchFeed
import cx.aswin.boxlore.core.network.model.TrendingFeed
import cx.aswin.boxlore.tv.model.TvEpisode
import cx.aswin.boxlore.tv.model.TvShow

/** API answers and feed items as the TV's shows and episodes. Pure, so it can be tested on its own. */
internal object TvMapping {
    private const val SECONDS_PER_MINUTE = 60
    private const val MILLIS_PER_SECOND = 1_000L
    private const val NOTES_LIMIT = 4_000
    private const val LINK_LIMIT = 4

    fun show(feed: SearchFeed, subscribed: Boolean): TvShow =
        TvShow(feed.id.toString(), feed.title, feed.author.orEmpty(), artwork(feed.artwork, feed.image), notes(feed.description), genres(feed.categories), subscribed = subscribed)

    fun show(feed: PodcastFeed, subscribed: Boolean): TvShow =
        TvShow(feed.id.toString(), feed.title, feed.author.orEmpty(), artwork(feed.artwork, feed.image), notes(feed.description), genres(feed.categories), subscribed = subscribed)

    fun show(feed: TrendingFeed, subscribed: Boolean): TvShow =
        TvShow(feed.id.toString(), feed.title, feed.author.orEmpty(), artwork(feed.artwork, feed.image), notes(feed.description), genres(feed.categories), subscribed = subscribed)

    /** An episode from the API's list, or null when it has no audio address. */
    fun episode(item: EpisodeItem, show: TvShow?): TvEpisode? {
        val audio = item.enclosureUrl?.trim()?.takeIf(::isWebAddress) ?: return null
        val showId = item.feedId?.toString() ?: show?.id ?: return null
        return TvEpisode(
            id = item.id.toString(),
            showId = showId,
            title = item.title,
            showTitle = item.feedTitle?.takeIf { it.isNotBlank() } ?: show?.title.orEmpty(),
            artworkUrl = artwork(item.image, item.feedImage) ?: show?.artworkUrl,
            durationMinutes = minutes(item.duration),
            releasedAtMillis = item.datePublished?.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
            description = notes(item.description),
            links = links(item.description),
            video = item.enclosureType?.startsWith("video/") == true,
            audioUrl = audio,
            catalogKey = item.catalogKey,
        )
    }

    /** An episode read from the show's own feed. Feed items give no show name, so [show] supplies it. */
    fun episode(feed: TvFeedEpisode, showId: String, show: TvShow?): TvEpisode =
        TvEpisode(
            id = feed.id.toString(),
            showId = showId,
            title = feed.title,
            showTitle = show?.title.orEmpty(),
            artworkUrl = feed.imageUrl ?: show?.artworkUrl,
            durationMinutes = minutes(feed.durationSeconds),
            releasedAtMillis = feed.publishedAtSeconds.takeIf { it > 0 }?.times(MILLIS_PER_SECOND),
            description = notes(feed.notes),
            links = links(feed.notes),
            audioUrl = feed.audioUrl,
            catalogKey = feed.catalogKey,
        )

    /** Whole minutes, rounded up, so a short episode never reads "0 min". Unknown lengths are 0. */
    fun minutes(durationSeconds: Int?): Int {
        val seconds = durationSeconds ?: return 0
        return if (seconds <= 0) 0 else (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
    }

    /** Show notes as plain text: tags dropped, paragraphs kept, common entities read, long notes cut. */
    fun notes(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val text =
            html.replace(BREAK, "\n")
                .replace(TAG, "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .lines()
                .joinToString("\n") { it.replace(SPACES, " ").trim() }
                .replace(BLANK_LINES, "\n\n")
                .trim()
        return if (text.length > NOTES_LIMIT) text.take(NOTES_LIMIT).trimEnd() + "…" else text
    }

    /** The first web addresses in the notes, each once, for the chips that open them on the phone. */
    fun links(html: String?): List<String> {
        if (html.isNullOrBlank()) return emptyList()
        return LINK.findAll(html).map { it.value.trimEnd('.', ',', ')', ';') }.distinct().take(LINK_LIMIT).toList()
    }

    private fun isWebAddress(value: String): Boolean = value.startsWith("http://") || value.startsWith("https://")

    private fun genres(categories: Map<String, String>?): List<String> = categories?.values?.filter { it.isNotBlank() }?.distinct().orEmpty()

    private fun artwork(first: String?, second: String?): String? = first?.takeIf { it.isNotBlank() } ?: second?.takeIf { it.isNotBlank() }

    private val BREAK = Regex("(?i)<\\s*(br|/p|/div|/li|/h[1-6])\\s*/?>")
    private val TAG = Regex("<[^>]*>")
    private val SPACES = Regex("[ \\t\\x0B\\f\\r]+")
    private val BLANK_LINES = Regex("\n{3,}")
    private val LINK = Regex("https?://[^\\s\"'<>]+")
}
