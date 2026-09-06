package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.FolderDao
import cx.aswin.boxlore.core.database.FolderEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.PodcastFolderCrossRef
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomFolderRepository(
    private val folderDao: FolderDao,
    private val podcastDao: PodcastDao,
) : FolderRepository {

    override val folders: Flow<List<SubscriptionFolder>> =
        combine(
            folderDao.getAllFolders(),
            folderDao.getAllCrossRefs(),
            podcastDao.getSubscribedPodcasts(),
        ) { folderEntities, crossRefs, subscribedPods ->
            val subscribedIds = subscribedPods.map { it.podcastId }.toSet()
            val refsByFolder = crossRefs
                .filter { it.podcastId in subscribedIds }
                .groupBy { it.folderId }
            folderEntities.map { entity ->
                val pIds = refsByFolder[entity.folderId]?.map { it.podcastId } ?: emptyList()
                SubscriptionFolder(
                    id = entity.folderId,
                    name = entity.name,
                    icon = entity.icon,
                    displaySize = entity.displaySize,
                    linkedGenre = entity.linkedGenre,
                    createdAt = entity.createdAt,
                    podcastCount = pIds.size,
                    podcastIds = pIds,
                )
            }
        }

    override val folderNames: Flow<List<String>> =
        folderDao.getAllFolderNames()

    override suspend fun getFolders(): List<SubscriptionFolder> {
        val entities = folderDao.getAllFoldersList()
        val subscribedIds = podcastDao.getSubscribedPodcastsList().map { it.podcastId }.toSet()
        val crossRefs = folderDao.getAllCrossRefsList()
            .filter { it.podcastId in subscribedIds }
            .groupBy { it.folderId }
        return entities.map { entity ->
            val pIds = crossRefs[entity.folderId]?.map { it.podcastId } ?: emptyList()
            SubscriptionFolder(
                id = entity.folderId,
                name = entity.name,
                icon = entity.icon,
                displaySize = entity.displaySize,
                linkedGenre = entity.linkedGenre,
                showPodcastGrid = entity.showPodcastGrid,
                createdAt = entity.createdAt,
                podcastCount = pIds.size,
                podcastIds = pIds,
            )
        }
    }

    override suspend fun getFolder(folderId: String): SubscriptionFolder? {
        val entity = folderDao.getFolder(folderId) ?: return null
        val subscribedIds = podcastDao.getSubscribedPodcastsList().map { it.podcastId }.toSet()
        val pIds = folderDao.getPodcastIdsForFolderList(folderId).filter { it in subscribedIds }
        return SubscriptionFolder(
            id = entity.folderId,
            name = entity.name,
            icon = entity.icon,
            displaySize = entity.displaySize,
            linkedGenre = entity.linkedGenre,
            showPodcastGrid = entity.showPodcastGrid,
            createdAt = entity.createdAt,
            podcastCount = pIds.size,
            podcastIds = pIds,
        )
    }

    override fun getFolderFlow(folderId: String): Flow<SubscriptionFolder?> =
        folders.map { list -> list.firstOrNull { it.id == folderId } }

    override suspend fun createFolder(
        name: String,
        icon: String?,
        displaySize: FolderDisplaySize,
        linkedGenre: String?,
        showPodcastGrid: Boolean,
        podcastIds: List<String>,
    ): SubscriptionFolder {
        val id = UUID.randomUUID().toString()
        val trimmedName = name.trim()
        val trimmedIcon = icon?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedGenre = linkedGenre?.trim()?.takeIf { it.isNotEmpty() }

        val initialPodcastIds = podcastIds.toMutableList()
        if (trimmedGenre != null) {
            val matchingSubscribed = podcastDao.getSubscribedPodcastsList()
                .filter { pod -> matchesGenre(pod, trimmedGenre) }
                .map { it.podcastId }
            for (matchingId in matchingSubscribed) {
                if (matchingId !in initialPodcastIds) {
                    initialPodcastIds.add(matchingId)
                }
            }
        }

        val createdAt = System.currentTimeMillis()
        val entity = FolderEntity(
            folderId = id,
            name = trimmedName,
            icon = trimmedIcon,
            displaySize = displaySize,
            linkedGenre = trimmedGenre,
            showPodcastGrid = showPodcastGrid,
            createdAt = createdAt,
        )
        folderDao.upsertFolder(entity)
        if (initialPodcastIds.isNotEmpty()) {
            folderDao.setPodcastsForFolder(id, initialPodcastIds)
        }

        return SubscriptionFolder(
            id = id,
            name = trimmedName,
            icon = trimmedIcon,
            displaySize = displaySize,
            linkedGenre = trimmedGenre,
            showPodcastGrid = showPodcastGrid,
            createdAt = createdAt,
            podcastCount = initialPodcastIds.size,
            podcastIds = initialPodcastIds,
        )
    }

    override suspend fun updateFolder(folder: SubscriptionFolder) {
        val trimmedName = folder.name.trim()
        val trimmedIcon = folder.icon?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedGenre = folder.linkedGenre?.trim()?.takeIf { it.isNotEmpty() }

        val entity = FolderEntity(
            folderId = folder.id,
            name = trimmedName,
            icon = trimmedIcon,
            displaySize = folder.displaySize,
            linkedGenre = trimmedGenre,
            showPodcastGrid = folder.showPodcastGrid,
            createdAt = if (folder.createdAt > 0L) folder.createdAt else System.currentTimeMillis(),
        )
        folderDao.upsertFolder(entity)

        if (trimmedGenre != null) {
            val existingIds = folderDao.getPodcastIdsForFolderList(folder.id).toMutableSet()
            val matchingSubscribed = podcastDao.getSubscribedPodcastsList()
                .filter { pod -> matchesGenre(pod, trimmedGenre) }
                .map { it.podcastId }
            val added = matchingSubscribed.filter { existingIds.add(it) }
            if (added.isNotEmpty()) {
                folderDao.setPodcastsForFolder(folder.id, existingIds.toList())
            }
        }
    }

    override suspend fun deleteFolder(folderId: String) {
        folderDao.deleteFolder(folderId)
    }

    override suspend fun addPodcastToFolder(podcastId: String, folderId: String) {
        folderDao.insertCrossRefs(listOf(PodcastFolderCrossRef(podcastId = podcastId, folderId = folderId)))
    }

    override suspend fun removePodcastFromFolder(podcastId: String, folderId: String) {
        folderDao.deleteCrossRef(podcastId = podcastId, folderId = folderId)
    }

    override suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>) {
        folderDao.setPodcastsForFolder(folderId, podcastIds)
    }

    override suspend fun syncLinkedGenres() {
        val folders = folderDao.getAllFoldersList().filter { !it.linkedGenre.isNullOrBlank() }
        if (folders.isEmpty()) return

        val subscribed = podcastDao.getSubscribedPodcastsList()
        val subscribedIds = subscribed.map { it.podcastId }.toSet()
        for (folder in folders) {
            val targetGenre = folder.linkedGenre!!.trim()
            val matching = subscribed.filter { pod -> matchesGenre(pod, targetGenre) }.map { it.podcastId }
            val currentValidIds = folderDao.getPodcastIdsForFolderList(folder.folderId).filter { it in subscribedIds }.toSet()
            val newIds = (currentValidIds + matching).toList()
            if (newIds.size != currentValidIds.size || currentValidIds.size != folderDao.getPodcastIdsForFolderList(folder.folderId).size) {
                folderDao.setPodcastsForFolder(folder.folderId, newIds)
            }
        }
    }

    private fun matchesGenre(pod: PodcastEntity, targetGenre: String): Boolean {
        val candidates = listOfNotNull(pod.customGenre, pod.genre)
        return candidates.any { genreField ->
            genreField.split(",").any { it.trim().equals(targetGenre, ignoreCase = true) }
        }
    }
}
