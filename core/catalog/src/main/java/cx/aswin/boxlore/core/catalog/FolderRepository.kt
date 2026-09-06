package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    val folders: Flow<List<SubscriptionFolder>>
    val folderNames: Flow<List<String>>

    suspend fun getFolders(): List<SubscriptionFolder>
    suspend fun getFolder(folderId: String): SubscriptionFolder?
    fun getFolderFlow(folderId: String): Flow<SubscriptionFolder?>

    suspend fun createFolder(
        name: String,
        icon: String? = null,
        displaySize: FolderDisplaySize = FolderDisplaySize.COMPACT,
        linkedGenre: String? = null,
        podcastIds: List<String> = emptyList(),
    ): SubscriptionFolder

    suspend fun updateFolder(folder: SubscriptionFolder)
    suspend fun deleteFolder(folderId: String)
    suspend fun addPodcastToFolder(podcastId: String, folderId: String)
    suspend fun removePodcastFromFolder(podcastId: String, folderId: String)
    suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>)
    suspend fun syncLinkedGenres()
}
