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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomFolderRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var podcastDao: PodcastDao
    private lateinit var repository: RoomFolderRepository

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

    private suspend fun insertSubscribedPodcast(
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
    fun createFolderWithoutIconUsesNullIconGracefully() = runTest {
        val folder = repository.createFolder(
            name = "Minimalist Folder",
            icon = null,
            displaySize = FolderDisplaySize.COMPACT,
        )

        assertNotNull(folder.id)
        assertEquals("Minimalist Folder", folder.name)
        assertNull(folder.icon)
        assertFalse(folder.hasIcon)
        assertEquals(FolderDisplaySize.COMPACT, folder.displaySize)

        val retrieved = repository.getFolder(folder.id)
        assertNotNull(retrieved)
        assertNull(retrieved?.icon)
    }

    @Test
    fun createFolderWithLinkedGenreAutoAddsMatchingSubscribedShows() = runTest {
        insertSubscribedPodcast("p1", "Tech 1", genre = "Technology")
        insertSubscribedPodcast("p2", "Tech 2", customGenre = "Technology")
        insertSubscribedPodcast("p3", "Comedy Show", genre = "Comedy")

        val folder = repository.createFolder(
            name = "Tech Zone",
            icon = "tech",
            displaySize = FolderDisplaySize.FEATURED,
            linkedGenre = "Technology",
        )

        assertEquals(2, folder.podcastCount)
        assertTrue(folder.podcastIds.contains("p1"))
        assertTrue(folder.podcastIds.contains("p2"))
        assertFalse(folder.podcastIds.contains("p3"))
    }

    @Test
    fun deleteFolderRemovesFolderAndCrossRefsWithoutUnsubscribingPodcasts() = runTest {
        insertSubscribedPodcast("p1", "Show 1", genre = "News")

        val folder = repository.createFolder(
            name = "News Daily",
            icon = "news",
            podcastIds = listOf("p1"),
        )

        assertEquals(1, repository.getFolders().size)
        assertEquals(1, repository.getFolder(folder.id)?.podcastCount)

        repository.deleteFolder(folder.id)

        assertNull(repository.getFolder(folder.id))
        assertTrue(repository.getFolders().isEmpty())

        // Ensure podcast remains in database and subscribed
        val podcast = podcastDao.getPodcast("p1")
        assertNotNull(podcast)
        assertTrue(podcast?.isSubscribed == true)
    }

    @Test
    fun addAndRemovePodcastFromFolderUpdatesFlow() = runTest {
        insertSubscribedPodcast("p1", "Show 1")
        insertSubscribedPodcast("p2", "Show 2")

        val folder = repository.createFolder(
            name = "Mixed",
            icon = "folder",
        )

        repository.addPodcastToFolder("p1", folder.id)
        var updated = repository.getFolder(folder.id)
        assertEquals(1, updated?.podcastCount)
        assertTrue(updated?.podcastIds?.contains("p1") == true)

        repository.addPodcastToFolder("p2", folder.id)
        updated = repository.getFolder(folder.id)
        assertEquals(2, updated?.podcastCount)

        repository.removePodcastFromFolder("p1", folder.id)
        updated = repository.getFolder(folder.id)
        assertEquals(1, updated?.podcastCount)
        assertTrue(updated?.podcastIds?.contains("p2") == true)
    }

    @Test
    fun syncLinkedGenresPicksUpNewShows() = runTest {
        val folder = repository.createFolder(
            name = "Science Hub",
            linkedGenre = "Science",
        )
        assertEquals(0, repository.getFolder(folder.id)?.podcastCount)

        insertSubscribedPodcast("p-sci", "Science Show", genre = "Science")
        repository.syncLinkedGenres()

        val updated = repository.getFolder(folder.id)
        assertEquals(1, updated?.podcastCount)
        assertTrue(updated?.podcastIds?.contains("p-sci") == true)
    }

    @Test
    fun folderNamesFlowEmitsDistinctSortedNames() = runTest {
        repository.createFolder(name = "Zeta")
        repository.createFolder(name = "Alpha")
        repository.createFolder(name = "   ")

        val names = repository.folderNames.first()
        assertEquals(listOf("Alpha", "Zeta"), names)
    }

    @Test
    fun unsubscribedPodcastsAreExcludedFromFoldersFlowAndCount() = runTest {
        insertSubscribedPodcast("p1", "Show 1", genre = "Tech")
        val folder = repository.createFolder(
            name = "Tech Zone",
            podcastIds = listOf("p1"),
        )

        val before = repository.folders.first()
        assertEquals(1, before.first().podcastCount)
        assertEquals(listOf("p1"), before.first().podcastIds)

        // Simulate unsubscribe: isSubscribed set to false
        val podcast = podcastDao.getPodcast("p1")!!
        podcastDao.upsert(podcast.copy(isSubscribed = false))

        val afterFlow = repository.folders.first()
        assertEquals(0, afterFlow.first().podcastCount)
        assertTrue(afterFlow.first().podcastIds.isEmpty())

        val afterGet = repository.getFolder(folder.id)
        assertEquals(0, afterGet?.podcastCount)
        assertTrue(afterGet?.podcastIds?.isEmpty() == true)
    }

    @Test
    fun autoSyncMatchesBothCatalogGenreAndCustomGenreTags() = runTest {
        insertSubscribedPodcast("p-cat", "Tech Catalog", genre = "Technology", customGenre = "Favorites")
        insertSubscribedPodcast("p-custom", "Tech Custom", genre = "Society", customGenre = "Technology")

        val folder = repository.createFolder(
            name = "All Tech",
            linkedGenre = "Technology",
        )

        assertEquals(2, folder.podcastCount)
        assertTrue(folder.podcastIds.contains("p-cat"))
        assertTrue(folder.podcastIds.contains("p-custom"))
    }
}
