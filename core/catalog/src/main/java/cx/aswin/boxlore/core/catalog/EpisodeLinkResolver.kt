package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodeReply
import cx.aswin.boxlore.core.network.model.V2FeedAddress
import cx.aswin.boxlore.core.rss.ParsedFeedItem
import cx.aswin.boxlore.core.rss.ParsedRssFeed
import kotlinx.coroutines.CancellationException

/**
 * One network lookup of an episode by ID: `/v2/episode` for every canonical positive ID (a recipe
 * ID, a served ID or a Podcast Index ID), v1 `/episode` only for other legacy IDs and when the v2
 * route can't answer for a Podcast Index ID.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 */
sealed interface EpisodeLookup {
    data class Found(val episode: Episode) : EpisodeLookup

    /** A recipe ID only the show's feed lists: read the feed on the phone and match by recipe ID. */
    data class NeedsFeed(val reply: V2EpisodeReply.NeedsFeed) : EpisodeLookup

    /** The API doesn't know the episode (400, 404 or 410: an ID it can't take or doesn't list). */
    data object Missing : EpisodeLookup

    /** The API couldn't answer (offline, a timeout, no session, a rate limit, a server error). */
    data object Failed : EpisodeLookup
}

/**
 * The outcome of [EpisodeLinkResolver.resolve].
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 */
sealed interface EpisodeLinkResult {
    /**
     * The episode. A link to an ID the phone stores under another trusted ID resolves to the phone's
     * own row, with that row's ID; an episode only the show's feed lists keeps the link's ID.
     */
    data class Found(val episode: Episode) : EpisodeLinkResult

    /** Nothing lists the episode: no local row or alias, the API doesn't know it, or the show's feed no longer has it. */
    data object NotFound : EpisodeLinkResult

    /** A source the answer depends on couldn't be reached. */
    data object Unavailable : EpisodeLinkResult
}

/**
 * Reads show [showId]'s feed document from the address a `/v2` answer gave. Null when there is no
 * address to read; throws when the read fails.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 */
fun interface EpisodeFeedReader {
    suspend fun read(showId: Long, address: V2FeedAddress): ParsedRssFeed?
}

/**
 * Finds the episode an ID names, for a link, a push, Android Auto or any caller that has only an ID
 * and maybe its show. In order:
 * 1. a stored row with that ID ([storedEpisode]: the local catalog, then the phone's feed-only rows);
 * 2. the phone's own row of the episode that ID is a trusted alias of, or, for a recipe ID with its
 *    show, of the one episode of that show whose trusted aliases carry it ([EpisodeAliasReads]);
 * 3. the API ([lookup]); an answer for an episode the phone stores under another trusted ID or the
 *    same catalog key resolves to the phone's row;
 * 4. for a `needsFeed` answer, and only when the caller asks for it, one read of the show's feed,
 *    matched by recipe ID in the show's namespace ([matchingItem]). A recipe ID two keys of the feed
 *    share matches nothing (docs/EPISODE_IDS.md §4).
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9: episode page; links, pushes, Auto).
 * COMPAT(own-ids): old and shared links resolve by show. Permanent: never remove (MIGRATION.md §5).
 */
class EpisodeLinkResolver(
    private val storedEpisode: suspend (String) -> Episode?,
    private val aliases: EpisodeAliasReads,
    private val episodeForKey: suspend (podcastId: String, catalogKey: String) -> Episode?,
    private val lookup: suspend (episodeId: String, showId: Long?) -> EpisodeLookup,
    private val feedReader: EpisodeFeedReader,
) {
    /**
     * Resolves [episodeId], with the show a link or push named ([showId], ignored unless it is a
     * Podcast Index show ID). [readFeed] allows step 4; [onReadingFeed] runs just before that read.
     */
    suspend fun resolve(episodeId: String, showId: String?, readFeed: Boolean, onReadingFeed: () -> Unit = {},): EpisodeLinkResult {
        storedEpisode(episodeId)?.let { return EpisodeLinkResult.Found(it) }
        val show = V2EpisodeIds.parseShowId(showId)?.takeIf(V2EpisodeIds::isEpisodeShowId)
        aliasEpisode(episodeId, show)?.let { return EpisodeLinkResult.Found(it) }
        return when (val answer = lookup(episodeId, show)) {
            is EpisodeLookup.Found -> EpisodeLinkResult.Found(localCopyOf(answer.episode) ?: answer.episode)
            is EpisodeLookup.NeedsFeed ->
                if (readFeed) {
                    onReadingFeed()
                    fromFeed(episodeId, answer.reply)
                } else {
                    EpisodeLinkResult.NotFound
                }
            EpisodeLookup.Missing -> EpisodeLinkResult.NotFound
            EpisodeLookup.Failed -> EpisodeLinkResult.Unavailable
        }
    }

    private suspend fun aliasEpisode(episodeId: String, show: Long?): Episode? {
        safely { aliases.trustedKeyOf(episodeId) }?.let { key -> fromKey(key, skip = episodeId)?.let { return it } }
        val recipeId = episodeId.recipeIdOrNull() ?: return null
        if (show == null) return null
        return safely { aliases.trustedKeyForRecipe(show.toString(), recipeId) }?.let { fromKey(it, skip = episodeId) }
    }

    /**
     * The phone's row of an episode the API answered for, when it stores one under another ID: by
     * the answer's recipe ID, then its catalog key. A v1 answer carries neither, so its enclosure
     * address is tried as a key; that match keeps the stored row's own identity.
     */
    private suspend fun localCopyOf(episode: Episode): Episode? {
        val podcastId = episode.podcastId?.takeIf(String::isNotBlank) ?: return null
        episode.recipeId?.let { recipeId ->
            safely { aliases.trustedKeyForRecipe(podcastId, recipeId) }?.let { key -> fromKey(key, skip = null)?.let { return it } }
        }
        episode.catalogKey?.let { catalogKey ->
            return safely { episodeForKey(podcastId, catalogKey) }?.withIdentity(episode.recipeId, catalogKey)
        }
        val enclosure = EpisodeRecipeId.trimKey(episode.audioUrl) ?: return null
        return safely { episodeForKey(podcastId, enclosure) }?.withIdentity(episode.recipeId, null)
    }

    private suspend fun fromKey(key: EpisodeAliasKey, skip: String?): Episode? {
        val stored = key.memberIds.filter { it != skip }.firstNotNullOfOrNull { storedEpisode(it) }
            ?: safely { episodeForKey(key.podcastId, key.catalogKey) }
        return stored?.withIdentity(key.recipeId, key.catalogKey)
    }

    private suspend fun fromFeed(episodeId: String, reply: V2EpisodeReply.NeedsFeed): EpisodeLinkResult {
        val recipeId = episodeId.recipeIdOrNull() ?: return EpisodeLinkResult.NotFound
        val showId = reply.show.takeIf(V2EpisodeIds::isEpisodeShowId) ?: return EpisodeLinkResult.NotFound
        val feed =
            try {
                feedReader.read(showId, reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "feed read for a shared episode failed", e)
                null
            } ?: return EpisodeLinkResult.Unavailable
        val namespace = safely { aliases.namespaceOf(showId.toString()) } ?: showId
        val item = matchingItem(feed.items, namespace, recipeId) ?: return EpisodeLinkResult.NotFound
        val catalogKey = requireNotNull(item.catalogKey)
        val podcastId = showId.toString()
        safely { episodeForKey(podcastId, catalogKey) }?.let { return EpisodeLinkResult.Found(it.withIdentity(recipeId, catalogKey)) }
        val episode = item.episode
            .toEpisode(podcastTitle = feed.title, podcastImageUrl = feed.imageUrl, podcastGenre = feed.genre, podcastArtist = feed.author)
            .copy(id = episodeId, podcastId = podcastId, recipeId = recipeId, catalogKey = catalogKey)
        return EpisodeLinkResult.Found(episode)
    }

    private suspend fun <T> safely(read: suspend () -> T?): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(TAG, "local episode lookup failed", e)
        null
    }

    private companion object {
        const val TAG = "EpisodeLinkResolver"
    }
}

private fun String.recipeIdOrNull(): Long? = EpisodeRecipeId.showIdOrNull(this)?.takeIf(EpisodeRecipeId::isRecipeId)

/**
 * The feed item [recipeId] names. A feed that lists the same item twice (one GUID, so one key) gives
 * its first copy in document order, as the phone's own refresh does; a recipe ID two different keys
 * share matches nothing (docs/EPISODE_IDS.md §4).
 */
internal fun matchingItem(items: List<ParsedFeedItem>, namespace: Long, recipeId: Long): ParsedFeedItem? {
    val matches = items.filter { it.hasRecipeId(namespace, recipeId) }
    if (matches.mapTo(HashSet()) { it.catalogKey }.size != 1) return null
    return matches.minByOrNull(ParsedFeedItem::documentIndex)
}

private fun ParsedFeedItem.hasRecipeId(namespace: Long, recipeId: Long): Boolean {
    val key = catalogKey ?: return false
    return keyFollowsContract && EpisodeRecipeId.forNamespace(namespace, key) == recipeId
}

private fun Episode.withIdentity(recipeId: Long?, catalogKey: String?): Episode =
    copy(recipeId = this.recipeId ?: recipeId, catalogKey = this.catalogKey ?: catalogKey)
