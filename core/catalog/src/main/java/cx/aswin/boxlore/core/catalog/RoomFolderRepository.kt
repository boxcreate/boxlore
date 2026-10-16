package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.FolderDao
import cx.aswin.boxlore.core.database.FolderEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.PodcastGenres
import cx.aswin.boxlore.core.model.SubscriptionFolder
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomFolderRepository(
    private val folderDao: FolderDao,
    private val podcastDao: PodcastDao,
    private val resolveGenreIconKey: ((String) -> String?)? = null,
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
                toSubscriptionFolder(entity, pIds)
            }
        }

    override val folderNames: Flow<List<String>> =
        folderDao.getAllFolderNames().map { names ->
            names.map { if (it.equals("Technology", ignoreCase = true)) "Tech" else it }.distinct()
        }

    override suspend fun getFolders(): List<SubscriptionFolder> {
        val entities = folderDao.getAllFoldersList()
        val subscribedIds = podcastDao.getSubscribedPodcastsList().map { it.podcastId }.toSet()
        val crossRefs = folderDao.getAllCrossRefsList()
            .filter { it.podcastId in subscribedIds }
            .groupBy { it.folderId }
        return entities.map { entity ->
            val pIds = crossRefs[entity.folderId]?.map { it.podcastId } ?: emptyList()
            toSubscriptionFolder(entity, pIds)
        }
    }

    override suspend fun getFolder(folderId: String): SubscriptionFolder? {
        val entity = folderDao.getFolder(folderId) ?: return null
        val subscribedIds = podcastDao.getSubscribedPodcastsList().map { it.podcastId }.toSet()
        val pIds = folderDao.getPodcastIdsForFolderList(folderId).filter { it in subscribedIds }
        return toSubscriptionFolder(entity, pIds)
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
        val rawName = name.trim()
        val trimmedName = if (rawName.equals("Technology", ignoreCase = true)) "Tech" else rawName
        val rawIcon = icon?.trim()?.takeIf { it.isNotEmpty() }
        val isTech = trimmedName.equals("Tech", ignoreCase = true)
        val trimmedIcon = if (isTech && isDefaultFolderIcon(rawIcon)) "tech" else rawIcon
        val rawGenre = linkedGenre?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedGenre = if (rawGenre?.equals("Technology", ignoreCase = true) == true) "Tech" else rawGenre
        val effectiveTargetGenre = trimmedGenre ?: PodcastGenres.canonicalize(trimmedName)?.let { trimmedName }

        val initialPodcastIds = podcastIds.toMutableList()
        if (effectiveTargetGenre != null) {
            val matchingSubscribed = podcastDao.getSubscribedPodcastsList()
                .filter { pod -> matchesGenre(pod, effectiveTargetGenre) }
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
            linkedGenre = trimmedGenre ?: effectiveTargetGenre,
            showPodcastGrid = showPodcastGrid,
            createdAt = createdAt,
        )
        folderDao.upsertFolder(entity)
        if (initialPodcastIds.isNotEmpty()) {
            folderDao.setPodcastsForFolder(id, initialPodcastIds)
        }

        return toSubscriptionFolder(entity, initialPodcastIds)
    }

    override suspend fun restoreFolder(folder: SubscriptionFolder): SubscriptionFolder {
        val rawName = folder.name.trim()
        val trimmedName = if (rawName.equals("Technology", ignoreCase = true)) "Tech" else rawName
        val effectiveIcon = if (trimmedName == "Tech" && folder.icon.isNullOrBlank()) "tech" else folder.icon?.trim()?.takeIf { it.isNotEmpty() }
        val rawGenre = folder.linkedGenre?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedGenre = if (rawGenre?.equals("Technology", ignoreCase = true) == true) "Tech" else rawGenre
        val effectiveCreatedAt = if (folder.createdAt > 0L) folder.createdAt else System.currentTimeMillis()

        val entity = FolderEntity(
            folderId = folder.id.ifBlank { UUID.randomUUID().toString() },
            name = trimmedName,
            icon = effectiveIcon,
            displaySize = folder.displaySize,
            linkedGenre = trimmedGenre,
            showPodcastGrid = folder.showPodcastGrid,
            createdAt = effectiveCreatedAt,
        )
        folderDao.upsertFolder(entity)
        if (folder.podcastIds.isNotEmpty()) {
            folderDao.setPodcastsForFolder(entity.folderId, folder.podcastIds)
        }

        return toSubscriptionFolder(entity, folder.podcastIds)
    }

    override suspend fun updateFolder(folder: SubscriptionFolder) {
        val rawName = folder.name.trim()
        val trimmedName = if (rawName.equals("Technology", ignoreCase = true)) "Tech" else rawName
        val rawIcon = folder.icon?.trim()?.takeIf { it.isNotEmpty() }
        val isTech = trimmedName.equals("Tech", ignoreCase = true)
        val trimmedIcon = if (isTech && isDefaultFolderIcon(rawIcon)) "tech" else rawIcon
        val rawGenre = folder.linkedGenre?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedGenre = if (rawGenre?.equals("Technology", ignoreCase = true) == true) "Tech" else rawGenre
        val effectiveTargetGenre = trimmedGenre ?: PodcastGenres.canonicalize(trimmedName)?.let { trimmedName }

        val entity = FolderEntity(
            folderId = folder.id,
            name = trimmedName,
            icon = trimmedIcon,
            displaySize = folder.displaySize,
            linkedGenre = trimmedGenre ?: effectiveTargetGenre,
            showPodcastGrid = folder.showPodcastGrid,
            createdAt = if (folder.createdAt > 0L) folder.createdAt else System.currentTimeMillis(),
        )
        folderDao.upsertFolder(entity)

        if (effectiveTargetGenre != null) {
            val existingIds = folderDao.getPodcastIdsForFolderList(folder.id).toMutableSet()
            val matchingSubscribed = podcastDao.getSubscribedPodcastsList()
                .filter { pod -> matchesGenre(pod, effectiveTargetGenre) }
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
        folderDao.addPodcastToFolderIfExists(podcastId = podcastId, folderId = folderId)
    }

    override suspend fun removePodcastFromFolder(podcastId: String, folderId: String) {
        folderDao.deleteCrossRef(podcastId = podcastId, folderId = folderId)
    }

    override suspend fun removePodcastFromAllFolders(podcastId: String) {
        folderDao.removePodcastFromAllFolders(podcastId)
    }

    override suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>) {
        folderDao.setPodcastsForFolder(folderId, podcastIds)
    }

    override suspend fun syncLinkedGenres() {
        val allFolders = folderDao.getAllFoldersList()
        migrateLegacyFolders(folderDao, allFolders)

        val folders = allFolders.filter {
            !it.linkedGenre.isNullOrBlank() || PodcastGenres.canonicalize(it.name) != null
        }
        if (folders.isEmpty()) return

        val subscribed = podcastDao.getSubscribedPodcastsList()
        for (folder in folders) {
            val rawTargetGenre = folder.linkedGenre?.trim()?.takeIf { it.isNotEmpty() } ?: folder.name.trim()
            val targetGenre = if (rawTargetGenre.equals("Technology", ignoreCase = true)) "Tech" else rawTargetGenre
            val matchingIds = subscribed
                .filter { pod -> matchesGenre(pod, targetGenre) }
                .map { it.podcastId }
                .distinct()
            val currentIds = folderDao.getPodcastIdsForFolderList(folder.folderId)
            val preservedMatching = currentIds.filter { it in matchingIds }
            val newMatching = matchingIds.filter { it !in preservedMatching }
            val combinedIds = preservedMatching + newMatching
            if (currentIds != combinedIds) {
                folderDao.setPodcastsForFolder(folder.folderId, combinedIds)
            }
            if (folder.linkedGenre.isNullOrBlank() && PodcastGenres.canonicalize(folder.name) != null) {
                folderDao.upsertFolder(folder.copy(linkedGenre = targetGenre))
            }
        }
    }

    override suspend fun autoOrganizeSubscribedShows(
        defaultDisplaySize: FolderDisplaySize?,
        showPodcastGrid: Boolean,
    ) {
        val subscribed = podcastDao.getSubscribedPodcastsList()
        if (subscribed.isEmpty()) return

        val genreToPodcasts = groupSubscribedPodcastsByGenre(subscribed)
        val existingFolders = folderDao.getAllFoldersList()

        for ((genre, podcastIds) in genreToPodcasts) {
            mergeOrCreateGenreFolder(
                folderDao = folderDao,
                resolveGenreIconKey = resolveGenreIconKey,
                genre = genre,
                podcastIds = podcastIds,
                existingFolders = existingFolders,
                defaultDisplaySize = defaultDisplaySize,
                showPodcastGrid = showPodcastGrid,
            )
        }

        syncLinkedGenres()
    }
}

private suspend fun mergeOrCreateGenreFolder(
    folderDao: FolderDao,
    resolveGenreIconKey: ((String) -> String?)?,
    genre: String,
    podcastIds: List<String>,
    existingFolders: List<FolderEntity>,
    defaultDisplaySize: FolderDisplaySize?,
    showPodcastGrid: Boolean,
) {
    val targetGenre = if (genre.equals("Technology", ignoreCase = true)) "Tech" else genre
    val existing = findMatchingGenreFolder(existingFolders, targetGenre)

    if (existing != null) {
        val updated = resolveUpdatedFolderEntity(existing, targetGenre)
        if (updated != existing) {
            folderDao.upsertFolder(updated)
        }
        val currentIds = folderDao.getPodcastIdsForFolderList(existing.folderId)
        val merged = (currentIds + podcastIds).distinct()
        if (merged.size != currentIds.size) {
            folderDao.setPodcastsForFolder(existing.folderId, merged)
        }
    } else {
        createAutoOrganizeFolder(
            folderDao = folderDao,
            resolveGenreIconKey = resolveGenreIconKey,
            genre = targetGenre,
            podcastIds = podcastIds,
            defaultDisplaySize = defaultDisplaySize,
            showPodcastGrid = showPodcastGrid,
        )
    }
}

private suspend fun createAutoOrganizeFolder(
    folderDao: FolderDao,
    resolveGenreIconKey: ((String) -> String?)?,
    genre: String,
    podcastIds: List<String>,
    defaultDisplaySize: FolderDisplaySize?,
    showPodcastGrid: Boolean,
) {
    val targetName = if (genre.equals("Technology", ignoreCase = true)) "Tech" else genre
    val folderId = UUID.randomUUID().toString()
    val iconKey = if (targetName.equals("Tech", ignoreCase = true)) "tech" else defaultIconForGenre(targetName, resolveGenreIconKey)
    val distinctPodcastIds = podcastIds.distinct()
    val chosenSize = defaultDisplaySize ?: when {
        distinctPodcastIds.size <= 2 -> FolderDisplaySize.COMPACT
        distinctPodcastIds.size <= 5 -> FolderDisplaySize.SHELF
        else -> FolderDisplaySize.PANEL
    }
    val resolvedGrid = chosenSize == FolderDisplaySize.COMPACT &&
        (defaultDisplaySize == null || showPodcastGrid)
    val entity = FolderEntity(
        folderId = folderId,
        name = targetName,
        icon = iconKey,
        displaySize = chosenSize,
        linkedGenre = targetName,
        showPodcastGrid = resolvedGrid,
        createdAt = System.currentTimeMillis(),
    )
    folderDao.upsertFolder(entity)
    folderDao.setPodcastsForFolder(folderId, distinctPodcastIds)
}

private fun defaultIconForGenre(genre: String, resolveGenreIconKey: ((String) -> String?)?): String? {
    if (genre.equals("Technology", ignoreCase = true) || genre.equals("Tech", ignoreCase = true)) return "tech"
    resolveGenreIconKey?.invoke(genre)?.let { return it }
    val canonical = PodcastGenres.canonicalize(genre) ?: genre
    return GENRE_DEFAULT_ICON_MAP[canonical.lowercase().trim()] ?: "folder"
}

private val GENRE_DEFAULT_ICON_MAP = mapOf(
    "news" to "news",
    "tech" to "tech",
    "technology" to "tech",
    "comedy" to "comedy",
    "sports" to "sports",
    "sport" to "sports",
    "business" to "business",
    "science" to "science",
    "music" to "music",
    "health" to "health",
    "history" to "history",
    "true crime" to "mic",
    "crime" to "mic",
    "tv & film" to "movie",
    "film" to "movie",
    "movies" to "movie",
    "fiction" to "book",
    "gaming" to "gaming",
    "games" to "gaming",
    "coding" to "code",
    "code" to "code",
    "ideas" to "bulb",
    "philosophy" to "bulb",
    "finance" to "finance",
    "money" to "finance",
)

private fun groupSubscribedPodcastsByGenre(subscribed: List<PodcastEntity>): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()
    for (pod in subscribed) {
        val genreName = pod.customGenre?.trim()?.takeIf { it.isNotEmpty() }
            ?: pod.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        if (!genreName.isNullOrBlank()) {
            val rawCanonical = PodcastGenres.canonicalize(genreName) ?: genreName.replaceFirstChar { it.uppercase() }
            val canonical = if (rawCanonical.equals("Technology", ignoreCase = true) || rawCanonical.equals("Tech", ignoreCase = true)) {
                "Tech"
            } else {
                rawCanonical
            }
            result.getOrPut(canonical) { mutableListOf() }.add(pod.podcastId)
        }
    }
    return result
}

private fun matchesGenre(pod: PodcastEntity, targetGenre: String): Boolean {
    val effectiveGenre = pod.customGenre?.takeIf { it.isNotBlank() }
        ?: pod.genre?.takeIf { it.isNotBlank() }
        ?: return false
    return effectiveGenre.split(",").any { token ->
        isGenreTokenMatch(token, targetGenre)
    }
}

private fun isGenreTokenMatch(candidate: String, target: String): Boolean {
    val trimmedCandidate = candidate.trim()
    val trimmedTarget = target.trim()
    if (trimmedCandidate.equals(trimmedTarget, ignoreCase = true)) return true

    val isTechSynonym = (trimmedTarget.equals("Tech", ignoreCase = true) && trimmedCandidate.equals("Technology", ignoreCase = true)) ||
        (trimmedTarget.equals("Technology", ignoreCase = true) && trimmedCandidate.equals("Tech", ignoreCase = true))
    if (isTechSynonym) return true

    val canonicalTarget = PodcastGenres.canonicalize(trimmedTarget)
    val canonicalCandidate = PodcastGenres.canonicalize(trimmedCandidate)

    if (canonicalTarget != null && (canonicalTarget.equals(canonicalCandidate, ignoreCase = true) || canonicalTarget.equals(trimmedCandidate, ignoreCase = true))) {
        return true
    }
    if (canonicalCandidate != null && canonicalCandidate.equals(trimmedTarget, ignoreCase = true)) {
        return true
    }
    return false
}

private fun findMatchingGenreFolder(existingFolders: List<FolderEntity>, genre: String): FolderEntity? =
    existingFolders.firstOrNull { folder ->
        isFolderMatchForGenre(folder, genre)
    }

private fun isFolderMatchForGenre(folder: FolderEntity, genre: String): Boolean {
    if (folder.linkedGenre?.equals(genre, ignoreCase = true) == true) return true
    if (folder.name.equals(genre, ignoreCase = true)) return true
    if (isGenreTokenMatch(folder.name, genre)) return true
    val linked = folder.linkedGenre
    return linked != null && isGenreTokenMatch(linked, genre)
}

private fun isDefaultFolderIcon(icon: String?): Boolean =
    icon == null || icon == "folder" || icon == "technology"

private fun resolveUpdatedFolderEntity(existing: FolderEntity, targetGenre: String): FolderEntity {
    val shouldRenameToTech = existing.name.equals("Technology", ignoreCase = true)
    val updatedName = if (shouldRenameToTech) "Tech" else existing.name
    val updatedIcon = if (shouldRenameToTech && isDefaultFolderIcon(existing.icon)) "tech" else existing.icon
    val isLegacyLinked = existing.linkedGenre.isNullOrBlank() || existing.linkedGenre.equals("Technology", ignoreCase = true)
    val updatedLinked = if (isLegacyLinked) targetGenre else existing.linkedGenre

    return existing.copy(
        name = updatedName,
        icon = updatedIcon,
        linkedGenre = updatedLinked,
    )
}

private fun toSubscriptionFolder(entity: FolderEntity, podcastIds: List<String>): SubscriptionFolder {
    val isTech = entity.name.equals("Technology", ignoreCase = true)
    val displayName = if (isTech) "Tech" else entity.name
    val displayIcon = if (isTech && isDefaultFolderIcon(entity.icon)) "tech" else entity.icon
    val isLegacyLinked = entity.linkedGenre?.equals("Technology", ignoreCase = true) == true
    val displayLinkedGenre = if (isLegacyLinked) "Tech" else entity.linkedGenre

    return SubscriptionFolder(
        id = entity.folderId,
        name = displayName,
        icon = displayIcon,
        displaySize = entity.displaySize,
        linkedGenre = displayLinkedGenre,
        showPodcastGrid = entity.showPodcastGrid,
        createdAt = entity.createdAt,
        podcastCount = podcastIds.size,
        podcastIds = podcastIds,
    )
}

private suspend fun migrateLegacyFolders(folderDao: FolderDao, allFolders: List<FolderEntity>) {
    for (folder in allFolders) {
        val isTech = folder.name.equals("Technology", ignoreCase = true)
        val isTechLinked = folder.linkedGenre?.equals("Technology", ignoreCase = true) == true
        if (isTech || isTechLinked) {
            val targetGenre = if (isTechLinked || isTech) "Tech" else (folder.linkedGenre ?: folder.name)
            val updated = resolveUpdatedFolderEntity(folder, targetGenre)
            if (updated != folder) {
                folderDao.upsertFolder(updated)
            }
        }
    }
}
