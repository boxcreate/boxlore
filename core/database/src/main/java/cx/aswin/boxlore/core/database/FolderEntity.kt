package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import cx.aswin.boxlore.core.model.FolderDisplaySize

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey
    val folderId: String,
    val name: String,
    val icon: String? = null,
    val displaySize: FolderDisplaySize = FolderDisplaySize.COMPACT,
    val linkedGenre: String? = null,
    val createdAt: Long = 0L,
)
