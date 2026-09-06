package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "podcast_folder_cross_ref",
    primaryKeys = ["podcastId", "folderId"],
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["folderId"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["podcastId"]),
    ],
)
data class PodcastFolderCrossRef(
    val podcastId: String,
    val folderId: String,
)
