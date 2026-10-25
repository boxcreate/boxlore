package cx.aswin.boxlore.core.rss

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import org.junit.After
import org.junit.Before

/**
 * Shared set-up for the catalog refresh tests that run on a real Room database (Robolectric,
 * in-memory `BoxLoreDatabase`): a scripted feed, the real refresh with an optional forced recipe
 * hash, and helpers that seed rows, aliases and listener state exactly as stored data looks.
 */
abstract class RoomCatalogTestBase {
    protected lateinit var database: BoxLoreDatabase
    protected val feed = ScriptedFeedClient()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    protected suspend fun refresh(
        show: String,
        url: String = feedUrl(show),
        serverEpNs: Long? = null,
        repo: LocalEpisodeCatalogRepository = repo(RecipeIdHash.RECIPE),
    ): RefreshOutcome = repo.refresh(
        LocalEpisodeCatalogPort.RefreshRequest(
            podcastIndexId = show,
            feedUrl = url,
            reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
            serverEpNs = serverEpNs,
        ),
    )

    protected fun repo(hash: RecipeIdHash) = LocalEpisodeCatalogRepository(database, feed, DownloadCacheRelinker { _, _ -> false }, {}, hash)

    protected suspend fun follow(show: String, epNs: Long? = show.toLong()) {
        database.podcastDao().upsert(PodcastEntity(podcastId = show, title = "Show $show", author = "", imageUrl = "", description = null, isSubscribed = true, epNs = epNs))
    }

    protected suspend fun seedAlias(id: String, show: String, key: String) {
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(
                EpisodeAliasEntity(
                    aliasId = id,
                    podcastId = show,
                    catalogKey = key,
                    recipeId = EpisodeRecipeId.forNamespace(show.toLong(), key),
                    state = EpisodeAliasState.TRUSTED,
                    owner = true,
                    provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
                    createdAt = 1L,
                ),
            ),
        )
    }

    protected suspend fun history(id: String, show: String, audio: String = "https://cdn.example/$id.mp3", title: String = "Ep $id") {
        database.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = id,
                podcastId = show,
                episodeTitle = title,
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = audio,
                podcastName = "Show",
                progressMs = 5_000L,
                durationMs = 60_000L,
                isCompleted = false,
                isLiked = true,
                lastPlayedAt = 1_000L,
            ),
        )
    }

    /** An alias exactly as the v39 upgrade seeds an ID with no usable key. */
    protected suspend fun seedUnresolved(id: String, show: String, provenance: String) {
        database.episodeIdentityDao().insertAliasesIfAbsent(
            listOf(EpisodeAliasEntity(id, show, null, null, EpisodeAliasState.UNRESOLVED, false, provenance, 1L)),
        )
    }

    protected suspend fun completedDownload(id: String, show: String, title: String) {
        database.downloadedEpisodeDao().insert(
            DownloadedEpisodeEntity(
                episodeId = id,
                podcastId = show,
                episodeTitle = title,
                episodeDescription = null,
                episodeImageUrl = null,
                podcastName = "Show",
                podcastImageUrl = null,
                durationMs = 60_000L,
                publishedDate = 10L,
                localFilePath = "/downloads/$id.mp3",
                downloadId = 1L,
                downloadedAt = 2_000L,
                sizeBytes = 1_000L,
                status = DownloadedEpisodeEntity.STATUS_COMPLETED,
            ),
        )
    }

    protected suspend fun queue(id: String, show: String, audio: String, title: String) {
        database.queueDao().insertQueueItem(
            QueueItem(
                episodeId = id,
                title = title,
                podcastId = show,
                podcastTitle = "Show",
                imageUrl = null,
                audioUrl = audio,
                duration = 60,
                pubDate = 10L,
                description = null,
                position = 0,
                addedAt = 3_000L,
            ),
        )
    }

    protected fun supplementRow(id: String, show: String, key: String) = EpisodeSupplementItemEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
        title = "Ep $key",
        description = "",
        audioUrl = "https://cdn.example/$key.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 10L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    protected fun target() = Podcast(id = SHOW, title = "Show", artist = "", imageUrl = "", feedUrl = RSS_FEED_URL)

    /** The feed namespace a refresh of [SHOW] mints phone IDs in. */
    protected fun namespace(url: String = feedUrl(SHOW)) = RssIdGenerator.podcastId(url)

    protected suspend fun alias(id: String): EpisodeAliasEntity? = database.episodeIdentityDao().getAlias(id)

    protected suspend fun ids(show: String): List<String> = database.localEpisodeCatalogDao().listIdentities(show).map { it.episodeId }

    /** Catalog key → stored ID for one show. */
    protected suspend fun rows(show: String): Map<String, String> = database.localEpisodeCatalogDao().listIdentities(show).associate { it.guid to it.episodeId }

    protected fun recipe(show: String, key: String): String = EpisodeRecipeId.forNamespace(show.toLong(), key).toString()

    /** The reserved ID P4a minted under the feed address (development installs only). */
    protected fun p4aReservedId(show: String, key: String): String = RssIdGenerator.episodeIdForPodcast(
        podcastId = RssIdGenerator.podcastId(feedUrl(show)),
        guid = "$key\u0000reserved:$show:1",
        enclosureUrl = null,
        publishedDate = 0L,
        title = "",
    )

    protected fun row(id: String, show: String, key: String, audio: String = "https://cdn.example/$key.mp3", title: String = "Ep $key") = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
        title = title,
        description = "",
        audioUrl = audio,
        imageUrl = null,
        duration = 60,
        publishedDate = 10L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    /** A feed item; [enclosure] is its first playable `<enclosure url>`, which GUID-less items are keyed on. */
    protected data class Item(
        val guid: String?,
        val title: String = "Ep $guid",
        val audio: String = "https://cdn.example/$guid.mp3",
        val enclosure: String? = audio,
    )

    /** Serves [items] as the feed at every address, parsed like a feed with the item order given. */
    protected class ScriptedFeedClient : RssFeedClient() {
        var items: List<Item> = emptyList()

        override suspend fun fetch(url: String) = RssFetchResult(finalUrl = url, etag = null, lastModified = null, body = ByteArray(1))

        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,): ParsedRssFeed {
            val episodes = items.map { item -> episode(item, podcastId) }
            return ParsedRssFeed(
                title = "Show",
                author = "",
                description = null,
                imageUrl = null,
                genre = null,
                podcastType = "episodic",
                podcastGuid = null,
                declaredUpdatedAt = null,
                episodes = episodes,
                items =
                episodes.mapIndexed { index, episode ->
                    ParsedFeedItem(episode, index, enclosureUrls = listOfNotNull(items[index].enclosure), audioUrls = listOf(items[index].audio))
                },
            )
        }

        private fun episode(item: Item, podcastId: String) = RssEpisodeEntity(
            episodeId = RssIdGenerator.episodeIdForPodcast(podcastId, item.guid, item.audio, 10L, item.title),
            podcastId = podcastId,
            guid = item.guid,
            title = item.title,
            description = "",
            audioUrl = item.audio,
            imageUrl = null,
            duration = 60,
            publishedDate = 10L,
            chaptersUrl = null,
            transcriptUrl = null,
            transcripts = null,
            persons = null,
            seasonNumber = null,
            episodeNumber = null,
            episodeType = null,
            enclosureType = "audio/mpeg",
        )
    }

    companion object {
        const val SHOW = "920666"
        const val OTHER_SHOW = "920667"
        const val THIRD_SHOW = "920668"
        const val FORCED = 4_600_000_000_000_000L
        const val SAME_AUDIO = "https://cdn.example/same.mp3"
        const val MOVED_URL = "https://moved.example/feed.xml"
        const val RSS_FEED_URL = "https://rss.example/feed.xml"
        val FORCED_HASH = RecipeIdHash { _, _ -> FORCED }

        fun feedUrl(show: String) = "https://feeds.example/$show.xml"
    }
}
