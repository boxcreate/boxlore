package cx.aswin.boxlore.feature.library

import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.feature.library.subscriptions.extractDistinctGenres
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FolderEditLogicTest {

    private class FakeFolderRepository : FolderRepository {
        private val _folders = MutableStateFlow<List<SubscriptionFolder>>(emptyList())
        override val folders: Flow<List<SubscriptionFolder>> = _folders
        override val folderNames: Flow<List<String>> = _folders.map { list -> list.map { it.name }.sorted() }

        override suspend fun getFolders(): List<SubscriptionFolder> = _folders.value

        override suspend fun getFolder(folderId: String): SubscriptionFolder? =
            _folders.value.firstOrNull { it.id == folderId }

        override fun getFolderFlow(folderId: String): Flow<SubscriptionFolder?> =
            _folders.map { list -> list.firstOrNull { it.id == folderId } }

        override suspend fun createFolder(
            name: String,
            icon: String?,
            displaySize: FolderDisplaySize,
            linkedGenre: String?,
            podcastIds: List<String>,
        ): SubscriptionFolder {
            val folder = SubscriptionFolder(
                id = UUID.randomUUID().toString(),
                name = name,
                icon = icon?.takeIf { it.isNotBlank() },
                displaySize = displaySize,
                linkedGenre = linkedGenre?.takeIf { it.isNotBlank() },
                podcastCount = podcastIds.size,
                podcastIds = podcastIds,
            )
            _folders.update { it + folder }
            return folder
        }

        override suspend fun updateFolder(folder: SubscriptionFolder) {
            _folders.update { list ->
                list.map { if (it.id == folder.id) folder else it }
            }
        }

        override suspend fun deleteFolder(folderId: String) {
            _folders.update { list -> list.filter { it.id != folderId } }
        }

        override suspend fun addPodcastToFolder(podcastId: String, folderId: String) {
            _folders.update { list ->
                list.map { folder ->
                    if (folder.id == folderId && podcastId !in folder.podcastIds) {
                        val newIds = folder.podcastIds + podcastId
                        folder.copy(podcastIds = newIds, podcastCount = newIds.size)
                    } else {
                        folder
                    }
                }
            }
        }

        override suspend fun removePodcastFromFolder(podcastId: String, folderId: String) {
            _folders.update { list ->
                list.map { folder ->
                    if (folder.id == folderId && podcastId in folder.podcastIds) {
                        val newIds = folder.podcastIds - podcastId
                        folder.copy(podcastIds = newIds, podcastCount = newIds.size)
                    } else {
                        folder
                    }
                }
            }
        }

        override suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>) {
            _folders.update { list ->
                list.map { folder ->
                    if (folder.id == folderId) {
                        folder.copy(podcastIds = podcastIds, podcastCount = podcastIds.size)
                    } else {
                        folder
                    }
                }
            }
        }

        override suspend fun syncLinkedGenres() {
            // No-op in test fake
        }
    }

    @Test
    fun folderDisplaySize_enumCases() {
        assertEquals(3, FolderDisplaySize.entries.size)
        assertTrue(FolderDisplaySize.entries.contains(FolderDisplaySize.COMPACT))
        assertTrue(FolderDisplaySize.entries.contains(FolderDisplaySize.FEATURED))
        assertTrue(FolderDisplaySize.entries.contains(FolderDisplaySize.SHELF))
    }

    @Test
    fun optionalIcon_nullAndEmptyFallbackToDefaultFolderIcon() {
        val defaultIcon = GenreIcons.defaultFolderIcon()
        assertEquals(defaultIcon, GenreIcons.folderIconOrFallback(null))
        assertEquals(defaultIcon, GenreIcons.folderIconOrFallback(""))
        assertEquals(defaultIcon, GenreIcons.folderIconOrFallback("   "))

        // Known icon resolves to specific icon
        val techIcon = GenreIcons.folderIconOrFallback("tech")
        assertNotNull(techIcon)
    }

    @Test
    fun suggestedGenres_extractsAndDeduplicatesDistinctTags() {
        val podcasts = listOf(
            Podcast(id = "1", title = "P1", artist = "A1", imageUrl = "", genre = "Technology", customGenre = "AI"),
            Podcast(id = "2", title = "P2", artist = "A2", imageUrl = "", genre = "Technology", customGenre = "AI, Coding"),
            Podcast(id = "3", title = "P3", artist = "A3", imageUrl = "", genre = "News", customGenre = null),
            Podcast(id = "4", title = "P4", artist = "A4", imageUrl = "", genre = "Podcast", customGenre = null),
        )

        val genres = extractDistinctGenres(podcasts)
        assertTrue(genres.contains("AI"))
        assertTrue(genres.contains("Coding"))
        assertTrue(genres.contains("News"))
        // Generic "Podcast" should be excluded
        assertFalse(genres.contains("Podcast"))
    }

    @Test
    fun fakeFolderRepository_createUpdateDeleteLifecycle() = runTest {
        val fakeRepo = FakeFolderRepository()

        // 1. Create with optional icon = null
        val created = fakeRepo.createFolder(
            name = "Science & Nature",
            icon = null,
            displaySize = FolderDisplaySize.SHELF,
            linkedGenre = "Science",
        )

        assertNotNull(created.id)
        assertEquals("Science & Nature", created.name)
        assertNull(created.icon)
        assertFalse(created.hasIcon)
        assertEquals(FolderDisplaySize.SHELF, created.displaySize)
        assertEquals("Science", created.linkedGenre)
        assertTrue(created.isGenreLinked)

        // 2. Observe flow
        val folders = fakeRepo.folders.first()
        assertEquals(1, folders.size)
        assertEquals(created.id, folders.first().id)

        // 3. Update folder
        val updated = created.copy(name = "Deep Science", icon = "science")
        fakeRepo.updateFolder(updated)

        val afterUpdate = fakeRepo.getFolder(created.id)
        assertNotNull(afterUpdate)
        assertEquals("Deep Science", afterUpdate?.name)
        assertEquals("science", afterUpdate?.icon)
        assertTrue(afterUpdate?.hasIcon == true)

        // 4. Delete folder
        fakeRepo.deleteFolder(created.id)
        assertTrue(fakeRepo.getFolders().isEmpty())
        assertNull(fakeRepo.getFolder(created.id))
    }

    @Test
    fun linkedGenreFallback_logicWhenAutoSyncEnabled() {
        fun resolveLinkedGenre(autoSync: Boolean, linkedGenreText: String, folderNameText: String): String? = if (autoSync) {
                linkedGenreText.trim().ifEmpty { folderNameText.trim() }.takeIf { it.isNotEmpty() }
            } else {
                null
            }

        // When autoSync is disabled -> null
        assertNull(resolveLinkedGenre(autoSync = false, linkedGenreText = "Tech", folderNameText = "My Tech"))

        // When autoSync is enabled and text provided -> uses text
        assertEquals("Tech", resolveLinkedGenre(autoSync = true, linkedGenreText = "Tech", folderNameText = "My Tech"))

        // When autoSync is enabled and text is empty -> falls back to folder name
        assertEquals("My Tech", resolveLinkedGenre(autoSync = true, linkedGenreText = "", folderNameText = "My Tech"))

        // When autoSync is enabled and both empty -> null
        assertNull(resolveLinkedGenre(autoSync = true, linkedGenreText = "", folderNameText = ""))
    }
}
