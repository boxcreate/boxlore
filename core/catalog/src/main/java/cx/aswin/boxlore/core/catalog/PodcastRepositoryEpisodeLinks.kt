package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodeReply
import cx.aswin.boxlore.core.network.model.V2FeedAddress
import cx.aswin.boxlore.core.network.model.V2FeedReadTarget
import cx.aswin.boxlore.core.network.model.v2Identity
import cx.aswin.boxlore.core.rss.ParsedRssFeed
import cx.aswin.boxlore.core.rss.RssFeedClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse

/**
 * One episode by its ID ([EpisodeLinkResolver] without the feed read): a stored row, the phone's
 * row of the episode the ID is a trusted alias of, then the network ([lookupNetworkEpisode]). The
 * episode keeps the ID the caller asked for, so a served or old ID is never swapped for another.
 *
 * Changed in P4 PR 9 (own-IDs plan section 9: recipe misses never go to Podcast Index) and P4 PR 13
 * (aliases resolve before the network).
 */
internal suspend fun PodcastRepository.getEpisodeImpl(episodeId: String, showId: String?,): Episode? = withContext(Dispatchers.IO) {
    when (val result = episodeLinkResolver().resolve(episodeId, showId, readFeed = false)) {
        is EpisodeLinkResult.Found -> result.episode.takeIf { it.id == episodeId } ?: result.episode.copy(id = episodeId)
        EpisodeLinkResult.NotFound, EpisodeLinkResult.Unavailable -> null
    }
}

/**
 * The episode a link, a push or a deep link names by [episodeId] and, when it carries one, its show
 * ([showId]). Resolves in [EpisodeLinkResolver]'s order: a stored row, a trusted alias, the API, then
 * one read of the show's feed for an episode only the feed lists ([onReadingFeed] runs before that
 * read). An episode the phone stores under another trusted ID comes back with the phone's own ID.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 * COMPAT(own-ids): old and shared links resolve by show. Permanent: never remove (MIGRATION.md §5).
 */
suspend fun PodcastRepository.resolveEpisodeLink(episodeId: String, showId: String?, onReadingFeed: () -> Unit = {},): EpisodeLinkResult =
    withContext(Dispatchers.IO) { episodeLinkResolver().resolve(episodeId, showId, readFeed = true, onReadingFeed = onReadingFeed) }

/**
 * The phone's row of the one episode of show [podcastId] whose trusted aliases carry [recipeId], or
 * null. A push that names an episode by recipe ID uses it after GUID and enclosure matching.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, notifications).
 * COMPAT(own-ids): pushes match by (show, recipe ID). Permanent: never remove (MIGRATION.md §5).
 */
suspend fun PodcastRepository.localEpisodeForRecipe(podcastId: String, recipeId: Long,): Episode? = withContext(Dispatchers.IO) {
    if (!EpisodeRecipeId.isRecipeId(recipeId)) return@withContext null
    val key = try {
        episodeAliases.trustedKeyForRecipe(podcastId, recipeId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("PodcastRepository", "alias lookup failed for show $podcastId", e)
        null
    } ?: return@withContext null
    val stored = key.memberIds.firstNotNullOfOrNull { storedEpisodeById(it) }
        ?: storedEpisodeForKey(key.podcastId, key.catalogKey)
    stored?.copy(recipeId = stored.recipeId ?: recipeId, catalogKey = stored.catalogKey ?: key.catalogKey)
}

internal fun PodcastRepository.episodeLinkResolver(): EpisodeLinkResolver = EpisodeLinkResolver(
    storedEpisode = { id -> storedEpisodeById(id) },
    aliases = episodeAliases,
    episodeForKey = { podcastId, catalogKey -> storedEpisodeForKey(podcastId, catalogKey) },
    lookup = { id, show -> lookupNetworkEpisode(id, show) },
    feedReader = episodeFeedReader,
)

/** A stored row with this ID: the local catalog, then the phone's feed-only rows for a negative ID. */
private suspend fun PodcastRepository.storedEpisodeById(episodeId: String): Episode? {
    getLocalCatalogEpisode(episodeId)?.let { return it }
    val negative = episodeId.toLongOrNull()?.let { it < 0L } == true
    return if (negative) getSupplementEpisode(episodeId) ?: getRssEpisode(episodeId) else null
}

/**
 * The stored row of show [podcastId]'s episode with [catalogKey]: the local catalog, where the key is
 * the row's GUID or, for an episode without one, its enclosure address; then the show's feed-only
 * rows by enclosure address (their GUIDs aren't read back). Null when none matches.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links and pushes).
 */
internal suspend fun PodcastRepository.storedEpisodeForKey(podcastId: String, catalogKey: String): Episode? {
    val key = EpisodeRecipeId.trimKey(catalogKey) ?: return null
    localEpisodeCatalog?.findByCatalogKey(podcastId, key, key)?.let { return it }
    val supplements = try {
        episodeSupplementRepository?.getEpisodesForPodcast(podcastId).orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("PodcastRepository", "feed-only rows unreadable for show $podcastId", e)
        emptyList()
    }
    return supplements.firstOrNull { it.catalogKey == key || EpisodeRecipeId.trimKey(it.audioUrl) == key }
}

/**
 * A network lookup of an ID no stored row holds. Negative IDs exist only on this phone, so they are
 * never looked up. Every canonical positive ID goes to `/v2/episode` with its show ([showId]): a
 * recipe-range ID, and an old Podcast Index ID, which the route answers with its recipe ID and
 * catalog key so the phone's own row of the episode can be found. Only a Podcast Index ID the v2
 * route can't answer right now falls back to v1, and other legacy IDs keep v1.
 *
 * Changed in P4 PR 13 of the own-episode-IDs plan (plan section 9: links resolve through
 * `/v2/episode`).
 */
private suspend fun PodcastRepository.lookupNetworkEpisode(episodeId: String, showId: Long?,): EpisodeLookup {
    val numeric = episodeId.toLongOrNull()
    return when {
        numeric != null && numeric < 0L -> EpisodeLookup.Missing
        numeric != null && EpisodeRecipeId.isRecipeId(numeric) -> lookupV2Episode(episodeId, showId)
        V2EpisodeIds.isLookupEpisodeId(episodeId) ->
            lookupV2Episode(episodeId, showId).takeUnless { it == EpisodeLookup.Failed } ?: lookupV1Episode(episodeId)
        else -> lookupV1Episode(episodeId)
    }
}

/**
 * `/v2/episode?id=&show=`. The episode keeps the ID the caller asked for, with its recipe ID and
 * catalog key attached, so a served ID is never swapped for the recipe ID.
 *
 * Added in P4 PR 9 (own-IDs plan section 9, `/v2/episode`); `needsFeed` and failures told apart in PR 13.
 * COMPAT(own-ids): recipe-range IDs resolve through the v2 route. Permanent: never remove (MIGRATION.md §5).
 */
private suspend fun PodcastRepository.lookupV2Episode(episodeId: String, showId: Long?,): EpisodeLookup {
    if (!V2EpisodeIds.isLookupEpisodeId(episodeId)) return EpisodeLookup.Missing
    return try {
        val response = apiV2.getEpisode(episodeId, showId).awaitResponse()
        val reply = response.body()
        when {
            !response.isSuccessful -> response.code().toLookupFailure()
            reply is V2EpisodeReply.Found -> {
                val identity = reply.episode.v2Identity()
                mapToEpisode(reply.episode)
                    ?.copy(id = episodeId, recipeId = identity?.recipeId, catalogKey = identity?.catalogKey)
                    ?.let(EpisodeLookup::Found)
                    ?: EpisodeLookup.Missing
            }
            reply is V2EpisodeReply.NeedsFeed -> EpisodeLookup.NeedsFeed(reply)
            else -> EpisodeLookup.Failed
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("PodcastRepository", "v2 episode lookup failed for $episodeId", e)
        EpisodeLookup.Failed
    }
}

/**
 * v1 `/episode` for a Podcast Index ID. Its answer has no recipe ID or catalog key; the resolver
 * matches it to a stored row by its enclosure address.
 *
 * LEGACY(own-ids): the v1 rung for links. Remove at sunset, once v1 `/episode` is retired.
 */
private suspend fun PodcastRepository.lookupV1Episode(episodeId: String): EpisodeLookup = try {
    val response = api.getEpisode(publicKey, episodeId).awaitResponse()
    if (response.isSuccessful) {
        response.body()?.episode?.let(::mapToEpisode)?.let(EpisodeLookup::Found) ?: EpisodeLookup.Missing
    } else {
        response.code().toLookupFailure()
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    EpisodeLookup.Failed
}

/**
 * 400, 404 and 410 mean the API can't take or doesn't list the ID. Anything else (401 or 403 without
 * a session, 408, 429 from the rate limiter, a server error) is worth trying again later, so the page
 * shows its load error rather than "not found".
 */
internal fun Int.toLookupFailure(): EpisodeLookup = if (this in HTTP_MISSING) EpisodeLookup.Missing else EpisodeLookup.Failed

private val HTTP_MISSING = setOf(400, 404, 410)

/**
 * Reads a show's feed for [EpisodeLinkResolver]: the publisher's HTTPS address with the app's RSS
 * client, or an http-only address only through `/v2/feed/<id>` ([V2FeedReadTarget.ViaApi]).
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
 */
internal class RssEpisodeFeedReader(
    private val feedClient: RssFeedClient,
    private val api: () -> BoxLoreApiV2,
) : EpisodeFeedReader {
    override suspend fun read(showId: Long, address: V2FeedAddress): ParsedRssFeed? = when (val target = address.readTarget(showId)) {
        is V2FeedReadTarget.Direct -> {
            val fetched = feedClient.fetch(target.url)
            // The parser's own IDs are minted under the feed address and never kept: a match takes
            // the link's recipe ID.
            feedClient.parse(fetched.finalUrl, fetched.body)
        }
        is V2FeedReadTarget.ViaApi -> {
            val response = api().getFeed(target.showId).awaitResponse()
            val body = response.body()?.takeIf { response.isSuccessful } ?: error("feed proxy answered ${response.code()}")
            // The same size cap as a direct read (P4 PR 16): a huge document never fills memory.
            val bytes = body.use { RssFeedClient.readFeedBody(it.byteStream()) }
            feedClient.parse(requireNotNull(address.transportUrl), bytes)
        }
        null -> null
    }
}
