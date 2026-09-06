package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    suspend fun getAllFoldersList(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE folderId = :folderId")
    suspend fun getFolder(folderId: String): FolderEntity?

    @Query("DELETE FROM folders WHERE folderId = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<PodcastFolderCrossRef>)

    @Query("DELETE FROM podcast_folder_cross_ref WHERE podcastId = :podcastId AND folderId = :folderId")
    suspend fun deleteCrossRef(podcastId: String, folderId: String)

    @Query("DELETE FROM podcast_folder_cross_ref WHERE folderId = :folderId")
    suspend fun deleteCrossRefsForFolder(folderId: String)

    @Query("SELECT podcastId FROM podcast_folder_cross_ref WHERE folderId = :folderId")
    suspend fun getPodcastIdsForFolderList(folderId: String): List<String>

    @Query("SELECT * FROM podcast_folder_cross_ref")
    fun getAllCrossRefs(): Flow<List<PodcastFolderCrossRef>>

    @Query("SELECT * FROM podcast_folder_cross_ref")
    suspend fun getAllCrossRefsList(): List<PodcastFolderCrossRef>

    @Query("SELECT DISTINCT name FROM folders WHERE trim(name) != '' ORDER BY name ASC")
    fun getAllFolderNames(): Flow<List<String>>

    @Transaction
    suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>) {
        deleteCrossRefsForFolder(folderId)
        val refs = podcastIds.distinct().map { PodcastFolderCrossRef(podcastId = it, folderId = folderId) }
        if (refs.isNotEmpty()) {
            insertCrossRefs(refs)
        }
    }
}
