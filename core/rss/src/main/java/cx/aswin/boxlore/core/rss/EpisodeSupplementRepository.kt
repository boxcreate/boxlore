package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeSupplementEntity
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches a publisher feed and caches feed-only episodes under a Podcast Index podcast id.
 *
 * Never writes `podcasts` / `rss_episodes`, never subscribes, never touches FCM.
 */
class EpisodeSupplementRepository(
    private val database: BoxLoreDatabase,
    private val feedClient: RssFeedClient = RssFeedClient(),
) : EpisodeSupplementPort {
    private val dao = database.episodeSupplementDao()

    override suspend fun refreshFromFeed(
        podcastIndexId: String,
        feedUrl: String,
        baselineEpisodes: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): EpisodeSupplementOutcome = withContext(Dispatchers.IO) {
        try {
            require(podcastIndexId.isNotBlank()) { "Missing podcast id" }
            require(!podcastIndexId.startsWith("rss:")) {
                "Supplement is only for Podcast Index shows"
            }
            val fetched = feedClient.fetch(feedUrl)
            val rssNamespaceId = RssIdGenerator.podcastId(fetched.finalUrl)
            val parsed = feedClient.parse(
                feedUrl = fetched.finalUrl,
                bytes = fetched.body,
                podcastId = rssNamespaceId,
            )
            val feedOnly = parsed.episodes.filterNot { rss ->
                EpisodeSupplementMatcher.isPresentInBaseline(rss, baselineEpisodes)
            }
            val previousIds = dao.getAllNewest(podcastIndexId).map { it.episodeId }.toSet()
            val items = feedOnly.map { it.toSupplementItem(podcastIndexId) }
            dao.replaceAll(
                podcastId = podcastIndexId,
                supplement = EpisodeSupplementEntity(
                    podcastId = podcastIndexId,
                    feedUrl = fetched.finalUrl,
                    rssNamespaceId = rssNamespaceId,
                    feedEtag = fetched.etag,
                    feedLastModified = fetched.lastModified,
                    fetchedAt = System.currentTimeMillis(),
                ),
                items = items,
            )
            EpisodeSupplementOutcome.Success(
                addedCount = items.count { it.episodeId !in previousIds },
                totalSupplementCount = items.size,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            EpisodeSupplementOutcome.Failure(
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "Couldn't load feed",
            )
        }
    }

    override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.getSupplement(podcastIndexId) != null
        }

    override suspend fun getEpisodesForPodcast(
        podcastIndexId: String,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): List<Episode> = withContext(Dispatchers.IO) {
        dao.getAllNewest(podcastIndexId).map {
            it.toEpisode(podcastTitle, podcastImageUrl, podcastGenre, podcastArtist)
        }
    }

    override suspend fun getEpisode(
        episodeId: String,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): Episode? = withContext(Dispatchers.IO) {
        dao.getEpisode(episodeId)?.toEpisode(
            podcastTitle,
            podcastImageUrl,
            podcastGenre,
            podcastArtist,
        )
    }

    override suspend fun search(
        podcastIndexId: String,
        query: String,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): List<Episode> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        dao.search(podcastIndexId, trimmed.escapeForSqlLike()).map {
            it.toEpisode(podcastTitle, podcastImageUrl, podcastGenre, podcastArtist)
        }
    }

    companion object {
        fun create(
            database: BoxLoreDatabase,
            feedClient: RssFeedClient = RssFeedClient(),
        ): EpisodeSupplementRepository = EpisodeSupplementRepository(database, feedClient)
    }
}

private fun RssEpisodeEntity.toSupplementItem(podcastIndexId: String) =
    EpisodeSupplementItemEntity(
        episodeId = episodeId,
        podcastId = podcastIndexId,
        guid = guid,
        title = title,
        description = description,
        audioUrl = audioUrl,
        imageUrl = imageUrl,
        duration = duration,
        publishedDate = publishedDate,
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl,
        transcripts = transcripts,
        persons = persons,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeType = episodeType,
        enclosureType = enclosureType,
    )
