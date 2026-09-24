package cx.aswin.boxlore.core.playback

import androidx.room.withTransaction
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ports.QueueSyncPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.entities.QueueItem
import cx.aswin.boxlore.core.database.entities.QueueMetadataEntity
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Transcript
import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

@Suppress("TooManyFunctions")
class QueueRepository(
    private val database: BoxLoreDatabase,
    private val podcastRepository: PodcastRepository,
    private val deviceIdentityPort: DeviceIdentityPort? = null,
) : QueueSyncPort {
    private val TAG = "QueueRepository"
    private val queueDao = database.queueDao()

    val queue: Flow<List<EpisodeItem>> =
        queueDao
            .getAllQueueItems()
            .map { items ->
                items.distinctBy { it.episodeId }.map { it.toEpisodeItem() }
            }

    suspend fun addToQueue(
        episode: EpisodeItem,
        podcast: cx.aswin.boxlore.core.model.Podcast?,
        contextType: String? = null,
        contextSourceId: String? = null,
    ) {
        android.util.Log.d(
            TAG,
            "addToQueue: episodeId=${episode.id}, title=${episode.title}, contextType=$contextType, contextSourceId=$contextSourceId",
        )
        // Check for duplicates using String ID
        val episodeIdStr = episode.id.toString()
        val podcastTitle = podcast?.title ?: "Unknown Podcast"
        val podcastId = podcast?.id ?: ""

        val resolvedTranscriptUrl =
            episode.transcripts
                ?.firstOrNull {
                    it.type == "application/srt" ||
                        it.type == "text/vtt" ||
                        it.type == "application/x-subrip" ||
                        it.url.contains(".srt", ignoreCase = true) ||
                        it.url.contains(".vtt", ignoreCase = true)
                }?.url
                ?: episode.transcriptUrl?.takeIf {
                    it.contains(".srt", ignoreCase = true) ||
                        it.contains(".vtt", ignoreCase = true)
                }
                ?: episode.transcriptUrl
                ?: episode.transcripts?.firstOrNull()?.url

        val newItem =
            QueueItem(
                episodeId = episodeIdStr,
                title = episode.title,
                podcastId = podcastId,
                podcastTitle = podcastTitle,
                podcastGenre = podcast?.genre ?: "",
                podcastArtist = podcast?.artist ?: "",
                podcastImageUrl = podcast?.imageUrl,
                imageUrl = episode.image ?: podcast?.imageUrl,
                audioUrl = episode.enclosureUrl ?: "",
                duration = episode.duration ?: 0,
                pubDate = episode.datePublished ?: 0L,
                description = episode.description,
                position = 0,
                contextType = contextType ?: "MANUAL",
                contextSourceId = contextSourceId,
                // Podcast 2.0
                chaptersUrl = episode.chaptersUrl,
                transcriptUrl = resolvedTranscriptUrl,
                personsJson = encodePersons(episode.persons?.map { Person(name = it.name, role = it.role, img = it.img, href = it.href) }),
                transcriptsJson = encodeTranscripts(episode.transcripts?.map { Transcript(url = it.url, type = it.type) }),
                episodeType = episode.episodeType,
                seasonNumber = episode.season,
                episodeNumber = episode.episodeNumber,
                enclosureType = episode.enclosureType,
            )

        val localDeviceId = deviceIdentityPort?.getDeviceId()
        val inserted = database.withTransaction {
            val existingCount = queueDao.countEpisode(episodeIdStr)
            if (existingCount > 0) {
                return@withTransaction false
            }
            val maxPos = queueDao.getMaxPosition() ?: 0
            val itemWithPos = newItem.copy(position = maxPos + 1)
            android.util.Log.d(TAG, "addToQueue: Inserting newItem at position ${maxPos + 1}")
            queueDao.insertQueueItem(itemWithPos)
            queueDao.bumpQueueVersion(
                updatedAt = System.currentTimeMillis(),
                deviceId = localDeviceId,
                restoredEpisodeIds = listOf(newItem.episodeId),
            )
            true
        }

        if (!inserted) {
            android.util.Log.w(TAG, "addToQueue: Episode ${episode.title} ($episodeIdStr) already in queue. Skipping.")
            return
        }

        if (newItem.contextType == "MANUAL" || newItem.contextType == QueueMath.CONTEXT_TYPE_LORE) {
            RankingFeedbackRepository.getIfInitialized()?.recordAction(
                target =
                FeedbackTarget(
                    episodeId = newItem.episodeId,
                    podcastId = newItem.podcastId,
                    genre = newItem.podcastGenre,
                    source =
                    if (newItem.contextType == QueueMath.CONTEXT_TYPE_LORE) {
                        CandidateSource.CURATED_INTENT
                    } else {
                        null
                    },
                ),
                action = RankingAction.EXPLICIT_QUEUE,
            )
        }
    }

    suspend fun clearQueue() {
        android.util.Log.d(TAG, "clearQueue: Clearing all queue items")
        val localDeviceId = deviceIdentityPort?.getDeviceId()
        database.withTransaction {
            val removedIds = queueDao.getAllQueueItemsSync().map { it.episodeId }
            queueDao.clearQueue()
            queueDao.bumpQueueVersion(
                updatedAt = System.currentTimeMillis(),
                deviceId = localDeviceId,
                removedEpisodeIds = removedIds,
            )
        }
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackQueueModified(
            action = "clear",
            queueSize = 0,
        )
    }

    /**
     * Replace the entire queue with the provided items.
     * Used to sync in-memory queue state back to DB.
     */
    suspend fun replaceQueue(episodes: List<cx.aswin.boxlore.core.model.Episode>) {
        val uniqueEpisodes = episodes.distinctBy { it.id }
        if (uniqueEpisodes.size != episodes.size) {
            android.util.Log.w(
                TAG,
                "replaceQueue: Removed ${episodes.size - uniqueEpisodes.size} duplicate episode IDs",
            )
        }
        val localDeviceId = deviceIdentityPort?.getDeviceId()
        database.withTransaction {
            val oldIds = queueDao.getAllQueueItemsSync().map { it.episodeId }
            val newIds = uniqueEpisodes.map { it.id }.toSet()
            val removedIds = oldIds.filter { it !in newIds }
            replaceQueueItems(uniqueEpisodes)
            queueDao.bumpQueueVersion(
                updatedAt = System.currentTimeMillis(),
                deviceId = localDeviceId,
                removedEpisodeIds = removedIds,
                restoredEpisodeIds = uniqueEpisodes.map { it.id },
            )
        }
    }

    private suspend fun replaceQueueItems(episodes: List<cx.aswin.boxlore.core.model.Episode>) {
        queueDao.clearQueue()
        episodes.forEachIndexed { index, ep ->
            val item =
                QueueItem(
                    episodeId = ep.id,
                    title = ep.title,
                    podcastId = ep.podcastId ?: "",
                    podcastTitle = ep.podcastTitle ?: "",
                    podcastGenre = ep.podcastGenre ?: "",
                    podcastArtist = ep.podcastArtist ?: "",
                    podcastImageUrl = ep.podcastImageUrl,
                    imageUrl = ep.imageUrl,
                    audioUrl = ep.audioUrl,
                    duration = ep.duration,
                    pubDate = ep.publishedDate,
                    description = ep.description,
                    position = index,
                    contextType = ep.contextType ?: "MANUAL",
                    contextSourceId = ep.contextSourceId,
                    // Podcast 2.0
                    chaptersUrl = ep.chaptersUrl,
                    transcriptUrl = ep.transcriptUrl,
                    personsJson = encodePersons(ep.persons),
                    transcriptsJson = encodeTranscripts(ep.transcripts),
                    episodeType = ep.episodeType,
                    seasonNumber = ep.seasonNumber,
                    episodeNumber = ep.episodeNumber,
                    enclosureType = ep.enclosureType,
                )
            queueDao.insertQueueItem(item)
        }
    }

    override suspend fun getQueueSnapshot(): List<QueueItem> =
        queueDao.getAllQueueItemsSync()

    suspend fun getQueueEpisodeSnapshot(): List<cx.aswin.boxlore.core.model.Episode> = database.withTransaction {
        android.util.Log.d(TAG, "getQueueEpisodeSnapshot: Fetching sync")
        val items = queueDao.getAllQueueItemsSync()
        android.util.Log.d(TAG, "getQueueEpisodeSnapshot: Got ${items.size} items")
        val episodes = items.map { it.toDomainEpisode() }
        val uniqueEpisodes = episodes.distinctBy { it.id }
        if (uniqueEpisodes.size != episodes.size) {
            android.util.Log.w(
                TAG,
                "getQueueEpisodeSnapshot: Repairing ${episodes.size - uniqueEpisodes.size} duplicate queue rows",
            )
            replaceQueueItems(uniqueEpisodes)
        }
        uniqueEpisodes
    }

    private fun cx.aswin.boxlore.core.database.entities.QueueItem.toDomainEpisode(): cx.aswin.boxlore.core.model.Episode = cx.aswin.boxlore.core.model.Episode(
        id = this.episodeId,
        title = this.title,
        description = this.description ?: "",
        audioUrl = this.audioUrl,
        imageUrl = this.imageUrl,
        podcastImageUrl = this.podcastImageUrl ?: this.imageUrl,
        podcastTitle = this.podcastTitle,
        podcastId = this.podcastId,
        podcastGenre = this.podcastGenre,
        podcastArtist = this.podcastArtist,
        duration = this.duration,
        publishedDate = this.pubDate,
        // Podcast 2.0
        chaptersUrl = this.chaptersUrl,
        transcriptUrl = this.transcriptUrl,
        persons = decodePersons(this.personsJson),
        transcripts = decodeTranscripts(this.transcriptsJson),
        episodeType = this.episodeType,
        seasonNumber = this.seasonNumber,
        episodeNumber = this.episodeNumber,
        contextType = this.contextType,
        contextSourceId = this.contextSourceId,
        enclosureType = this.enclosureType,
    )

    // --- P2.0 JSON helpers (using Android's org.json) ---

    private fun encodePersons(persons: List<Person>?): String? {
        if (persons.isNullOrEmpty()) return null
        val arr = JSONArray()
        persons.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            p.role?.let { obj.put("role", it) }
            p.img?.let { obj.put("img", it) }
            p.href?.let { obj.put("href", it) }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodePersons(json: String?): List<Person>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Person(
                    name = obj.getString("name"),
                    role = if (obj.isNull("role")) null else obj.optString("role"),
                    img = if (obj.isNull("img")) null else obj.optString("img"),
                    href = if (obj.isNull("href")) null else obj.optString("href"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeTranscripts(transcripts: List<Transcript>?): String? {
        if (transcripts.isNullOrEmpty()) return null
        val arr = JSONArray()
        transcripts.forEach { t ->
            val obj = JSONObject()
            obj.put("url", t.url)
            obj.put("type", t.type)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeTranscripts(json: String?): List<Transcript>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Transcript(
                    url = obj.getString("url"),
                    type = obj.optString("type"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getQueueItemByEpisodeId(episodeId: String): cx.aswin.boxlore.core.database.entities.QueueItem? = queueDao.getQueueItemByEpisodeId(episodeId)

    suspend fun removeFromQueue(episodeId: String) {
        val localDeviceId = deviceIdentityPort?.getDeviceId()
        database.withTransaction {
            queueDao.deleteQueueItemByEpisodeId(episodeId)
            queueDao.bumpQueueVersion(
                updatedAt = System.currentTimeMillis(),
                deviceId = localDeviceId,
                removedEpisodeId = episodeId,
            )
        }
    }

    /**
     * Rewrites row positions to match the given episode-id order (a Room transaction via
     * the DAO), preserving each row's contextType/contextSourceId provenance.
     * Ids not present in the DB are ignored; rows not present in the list keep their
     * position but are pushed after the reordered block.
     */
    suspend fun reorderQueue(orderedEpisodeIds: List<String>) {
        val localDeviceId = deviceIdentityPort?.getDeviceId()
        database.withTransaction {
            val items = queueDao.getAllQueueItemsSync()
            if (items.isEmpty() || orderedEpisodeIds.isEmpty()) return@withTransaction

            val byEpisodeId = items.associateBy { it.episodeId }
            val reordered = mutableListOf<QueueItem>()
            orderedEpisodeIds.distinct().forEach { episodeId ->
                byEpisodeId[episodeId]?.let { reordered.add(it) }
            }
            // Keep any rows that weren't part of the provided order (defensive) at the tail.
            val coveredIds = reordered.map { it.episodeId }.toSet()
            items.filter { it.episodeId !in coveredIds }.forEach { reordered.add(it) }

            val updated = reordered.mapIndexed { index, item -> item.copy(position = index) }
            android.util.Log.d(TAG, "reorderQueue: Rewriting positions for ${updated.size} items")
            queueDao.updateQueuePositions(updated)
            queueDao.bumpQueueVersion(
                updatedAt = System.currentTimeMillis(),
                deviceId = localDeviceId,
            )
        }
    }

    override suspend fun getQueueMetadata(): QueueMetadataEntity? =
        queueDao.getQueueMetadata()

    val queueMetadataFlow: Flow<QueueMetadataEntity?> =
        queueDao.getQueueMetadataFlow()

    suspend fun markQueueSynced(timestamp: Long) {
        queueDao.markQueueSynced(timestamp)
    }

    override suspend fun markQueueSynced(expectedSequence: Long, syncedAt: Long): Boolean =
        queueDao.markQueueSyncedIfSequenceMatches(expectedSequence, syncedAt) > 0

    override suspend fun markQueueDirty() {
        queueDao.markQueueDirty()
    }

    override suspend fun applyRemoteQueueState(
        items: List<QueueItem>,
        metadata: QueueMetadataEntity,
    ) {
        database.withTransaction {
            queueDao.clearQueue()
            if (items.isNotEmpty()) {
                val reindexed = items.mapIndexed { index, item ->
                    item.copy(position = index)
                }
                queueDao.insertQueueItems(reindexed)
            }
            queueDao.upsertQueueMetadata(metadata)
        }
    }

    suspend fun bumpQueueVersion(
        updatedAt: Long = System.currentTimeMillis(),
        deviceId: String? = null,
        removedEpisodeId: String? = null,
        removedEpisodeIds: Collection<String>? = null,
        restoredEpisodeIds: Collection<String>? = null,
    ) {
        val resolvedDevice = deviceId ?: deviceIdentityPort?.getDeviceId()
        queueDao.bumpQueueVersion(updatedAt, resolvedDevice, removedEpisodeId, removedEpisodeIds, restoredEpisodeIds)
    }
}
