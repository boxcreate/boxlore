package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import cx.aswin.boxlore.core.catalog.ports.ActivePlaybackSyncPort
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.BoxLoreApi
import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.QueueItemSyncDto
import cx.aswin.boxlore.core.network.model.QueueRemovalSyncDto
import cx.aswin.boxlore.core.network.model.QueueSyncDto
import cx.aswin.boxlore.core.network.model.SyncEpisodeIdentityFields
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import java.lang.reflect.Proxy
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Shared rows for the cloud sync identity tests (docs/EPISODE_IDS.md §5). [SHOW] is a Podcast
 * Index show; [KEY]'s recipe ID is [RECIPE] and [KEY_2]'s is [RECIPE_2]. [PI_ID] is the Podcast
 * Index ID older builds store [KEY]'s episode under.
 */
internal object SyncIdentityFixtures {
    const val SHOW = "77"
    const val KEY = "guid-1"
    const val KEY_2 = "guid-2"
    const val PI_ID = "61842097688"
    val RECIPE: Long = EpisodeRecipeId.recipeId(SHOW.toLong(), KEY)
    val RECIPE_2: Long = EpisodeRecipeId.recipeId(SHOW.toLong(), KEY_2)

    /** An in-memory database whose queries and transactions run on the calling thread. */
    fun directDatabase(context: Context): BoxLoreDatabase = Room
        .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor { it.run() }
        .setTransactionExecutor { it.run() }
        .build()

    @Suppress("LongParameterList")
    fun alias(
        id: String,
        key: String? = KEY,
        recipeId: Long? = key?.let { EpisodeRecipeId.recipeId(SHOW.toLong(), it) },
        state: String = EpisodeAliasState.TRUSTED,
        show: String = SHOW,
        owner: Boolean = false,
        provenance: String = EpisodeAliasProvenance.UPGRADE_CATALOG,
    ) = EpisodeAliasEntity(id, show, key, recipeId, state, owner, provenance, createdAt = 1L)

    @Suppress("LongParameterList")
    fun history(
        id: String,
        show: String = SHOW,
        progress: Long = 0L,
        playedAt: Long = 1_000L,
        liked: Boolean = false,
        likedAt: Long = 0L,
        dirty: Boolean = true,
        syncedAt: Long = 0L,
    ) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = progress,
        durationMs = 60_000L,
        isCompleted = false,
        isLiked = liked,
        likedAt = likedAt,
        lastPlayedAt = playedAt,
        isDirty = dirty,
        syncedAt = syncedAt,
    )

    fun catalogRow(id: String, key: String = KEY, show: String = SHOW) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = key,
        title = "Catalog $id",
        description = "",
        audioUrl = "https://example.com/catalog-$id.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 1L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    fun queueItem(id: String, position: Int = 0, show: String = SHOW, title: String = "Queued $id") = QueueItem(
        episodeId = id,
        title = title,
        podcastId = show,
        podcastTitle = "Show",
        imageUrl = null,
        audioUrl = "https://example.com/$id.mp3",
        duration = 60,
        pubDate = 0L,
        description = null,
        position = position,
    )

    /** A [BoxLoreApi] whose sync calls go to [server]; any other call fails. */
    fun api(server: FakeSyncServer): BoxLoreApi = Proxy.newProxyInstance(
        BoxLoreApi::class.java.classLoader,
        arrayOf(BoxLoreApi::class.java),
    ) { _, method, args ->
        when (method.name) {
            "syncPush" -> ImmediateCall(Response.success(server.push(args[2] as SyncPushRequest)))
            "syncPull" -> ImmediateCall(Response.success(server.pull(args[2] as SyncPullRequest)))
            "hashCode" -> 42
            "equals" -> false
            "toString" -> "FakeSyncApi"
            else -> throw UnsupportedOperationException(method.name)
        }
    } as BoxLoreApi

    /** A finished [Call]. */
    class ImmediateCall<T>(private val response: Response<T>) : Call<T> {
        override fun execute(): Response<T> = response
        override fun enqueue(callback: Callback<T>) = callback.onResponse(this, response)
        override fun isExecuted(): Boolean = true
        override fun cancel() = Unit
        override fun isCanceled(): Boolean = false
        override fun clone(): Call<T> = ImmediateCall(response)
        override fun request(): Request = Request.Builder().url("https://localhost/").build()
        override fun timeout(): Timeout = Timeout.NONE
    }

    /** A [QueueSyncPort] on the Room queue tables, applying a pulled state as `QueueRepository` stores it. */
    class DaoQueueSyncPort(private val database: BoxLoreDatabase) : QueueSyncPort {
        private val dao get() = database.queueDao()

        override suspend fun getQueueSnapshot(): List<QueueItem> = dao.getAllQueueItemsSync()

        override suspend fun getQueueMetadata(): QueueMetadataEntity? = dao.getQueueMetadata()

        override suspend fun applyRemoteQueueState(items: List<QueueItem>, metadata: QueueMetadataEntity) {
            dao.clearQueue()
            dao.insertQueueItems(items.mapIndexed { index, item -> item.copy(id = 0, position = index) })
            dao.upsertQueueMetadata(metadata)
        }

        override suspend fun markQueueSynced(expectedSequence: Long, syncedAt: Long): Boolean =
            dao.markQueueSyncedIfSequenceMatches(expectedSequence, syncedAt) > 0

        override suspend fun markQueueDirty() {
            dao.markQueueDirty()
        }
    }

    /** The playing episode, and the last idle session handed over. */
    class FakePlayback(var activeId: String? = null) : ActivePlaybackSyncPort {
        var handedOff: String? = null

        override fun getActivePlayingEpisodeId(): String? = activeId

        override fun updateIdlePlaybackSession(episodeId: String, positionMs: Long, lastPlayedAt: Long) {
            handedOff = episodeId
        }
    }
}

/**
 * The sync API's history and queue rules, as the live service applies them: recipe fields are
 * kept when a push omits them, a row whose own ID is a recipe ID is refused (and the push still
 * answers ok) when the stored row names another show or catalog key, a queue push is taken only
 * when it is not older, rich removals remove every stored row of the episode (its ID, and rows of
 * the show with its recipe ID or key) and are stored only when sent, and rich removals are pulled
 * only on request.
 */
internal class FakeSyncServer(var now: Long = 10_000L) {
    val history = LinkedHashMap<String, ListeningHistorySyncDto>()
    val queueItems = LinkedHashMap<String, QueueItemSyncDto>()
    val refused = mutableListOf<String>()
    val pushes = mutableListOf<SyncPushRequest>()
    val pulls = mutableListOf<SyncPullRequest>()
    var queueUpdatedAt = 0L
    var queueSequence = 0L
    var legacyRemovals: List<String> = emptyList()
    var richRemovals: List<QueueRemovalSyncDto> = emptyList()

    fun push(request: SyncPushRequest): SyncPushResponse {
        pushes += request
        for (item in request.history) {
            val recipeId = SyncEpisodeIdentityFields.recipeIdOrNull(item.recipeId)
            val key = SyncEpisodeIdentityFields.catalogKeyOrNull(item.catalogKey)
            val stored = history[item.episodeId]
            if (isRecipeRange(item.episodeId) && conflicts(stored?.podcastId, stored?.catalogKey, item.podcastId, key)) {
                refused += item.episodeId
                continue
            }
            if (stored == null || item.updatedAt >= stored.updatedAt) {
                history[item.episodeId] = item.copy(recipeId = recipeId ?: stored?.recipeId, catalogKey = key ?: stored?.catalogKey)
            }
        }
        request.queue?.let(::pushQueue)
        return SyncPushResponse(status = "ok", syncedAt = now)
    }

    /** Every stored queue ID a rich removal names: its own, and rows of its show with its recipe ID or key. */
    private fun expanded(rich: QueueRemovalSyncDto): List<String> {
        if (rich.show == null || (rich.recipeId == null && rich.catalogKey == null)) return listOf(rich.id)
        val sameEpisode = queueItems.values.filter { item ->
            item.podcastId == rich.show && (sameValue(item.recipeId, rich.recipeId) || sameValue(item.catalogKey, rich.catalogKey))
        }
        return listOf(rich.id) + sameEpisode.map { it.episodeId }
    }

    private fun sameValue(stored: Any?, removal: Any?): Boolean = removal != null && stored == removal

    private fun pushQueue(queue: QueueSyncDto) {
        val removed = LinkedHashSet(queue.recentRemovedEpisodeIds)
        queue.recentRemovedEpisodes.orEmpty().flatMapTo(removed, ::expanded)
        if (queue.queueUpdatedAt < queueUpdatedAt) return
        queueUpdatedAt = queue.queueUpdatedAt
        queueSequence = queue.queueSequence
        legacyRemovals = removed.toList()
        queue.recentRemovedEpisodes?.let { richRemovals = it }
        for (item in queue.items) {
            val stored = queueItems[item.episodeId]
            val key = SyncEpisodeIdentityFields.catalogKeyOrNull(item.catalogKey)
            if (isRecipeRange(item.episodeId) && conflicts(stored?.podcastId, stored?.catalogKey, item.podcastId, key)) {
                refused += item.episodeId
                continue
            }
            queueItems[item.episodeId] = item.copy(
                recipeId = SyncEpisodeIdentityFields.recipeIdOrNull(item.recipeId) ?: stored?.recipeId,
                catalogKey = key ?: stored?.catalogKey,
            )
        }
        removed.forEach { queueItems.remove(it) }
    }

    fun pull(request: SyncPullRequest): SyncPullResponse {
        pulls += request
        val queue = QueueSyncDto(
            items = queueItems.values.toList(),
            queueUpdatedAt = queueUpdatedAt,
            queueSequence = queueSequence,
            recentRemovedEpisodeIds = legacyRemovals,
            recentRemovedEpisodes = if (request.richRemovals) richRemovals else null,
        )
        return SyncPullResponse(
            history = history.values.filter { it.updatedAt > request.since },
            queue = queue.takeIf { queueSequence > 0L || queueItems.isNotEmpty() },
            syncedAt = now,
        )
    }

    private fun conflicts(storedShow: String?, storedKey: String?, show: String, key: String?): Boolean {
        if (storedShow == null) return false
        if (storedShow != show) return true
        return storedKey != null && key != null && storedKey != key
    }

    private fun isRecipeRange(id: String): Boolean = SyncEpisodeIdentityFields.recipeIdOrNull(id.toLongOrNull()) != null
}
