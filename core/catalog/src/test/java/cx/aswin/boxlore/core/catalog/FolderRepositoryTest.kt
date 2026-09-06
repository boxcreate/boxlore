package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.FolderDao
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.FolderDisplaySize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying the [FolderRepository] contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var podcastDao: PodcastDao
    private lateinit var repository: FolderRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        folderDao = database.folderDao()
        podcastDao = database.podcastDao()
        repository = RoomFolderRepository(folderDao, podcastDao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertPodcast(
        id: String,
        title: String,
        genre: String? = null,
        customGenre: String? = null,
    ) {
        podcastDao.upsert(
            PodcastEntity(
                podcastId = id,
                title = title,
                author = "Author",
                imageUrl = "https://example.com/art.jpg",
                description = "Desc",
                isSubscribed = true,
                genre = genre,
                customGenre = customGenre,
            ),
        )
    }

    @Test
    fun createFolder_supportsOptionalIcon() = runTest {
        val folder = repository.createFolder(
            name = "No Icon Folder",
            icon = null,
            displaySize = FolderDisplaySize.COMPACT,
        )

        assertNotNull(folder.id)
        assertEquals("No Icon Folder", folder.name)
        assertNull(folder.icon)
        assertFalse(folder.hasIcon)

        val retrieved = repository.getFolder(folder.id)
        assertNotNull(retrieved)
        assertNull(retrieved?.icon)
    }

    @Test
    fun observeFolderFlow_emitsUpdates() = runTest {
        val folder = repository.createFolder(
            name = "Tech Shows",
            icon = "tech",
            displaySize = FolderDisplaySize.FEATURED,
        )

        val flowFirst = repository.getFolderFlow(folder.id).first()
        assertNotNull(flowFirst)
        assertEquals("Tech Shows", flowFirst?.name)

        repository.updateFolder(folder.copy(name = "Updated Tech Shows"))
        val flowUpdated = repository.getFolderFlow(folder.id).first()
        assertEquals("Updated Tech Shows", flowUpdated?.name)
    }

    @Test
    fun deleteFolder_removesFolderAndPreservesPodcasts() = runTest {
        insertPodcast("pod-1", "Coding Daily")
        val folder = repository.createFolder(
            name = "Programming",
            podcastIds = listOf("pod-1"),
        )

        assertEquals(1, repository.getFolders().size)
        assertEquals(listOf("pod-1"), repository.getFolder(folder.id)?.podcastIds)

        repository.deleteFolder(folder.id)
        assertEquals(0, repository.getFolders().size)
        assertNull(repository.getFolder(folder.id))

        // Podcast entity remains intact
        val podcast = podcastDao.getPodcast("pod-1")
        assertNotNull(podcast)
        assertEquals("Coding Daily", podcast?.title)
    }

    @Test
    fun observeFolderNames_emitsSortedNames() = runTest {
        repository.createFolder(name = "Zebra")
        repository.createFolder(name = "Apple")
        repository.createFolder(name = "Mango")

        val names = repository.folderNames.first()
        assertEquals(listOf("Apple", "Mango", "Zebra"), names)
    }
}
