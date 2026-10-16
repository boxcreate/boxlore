package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.FolderDao
import cx.aswin.boxlore.core.database.FolderEntity
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
    fun linkingShowsToADeletedFolderIsIgnoredInsteadOfCrashing() = runTest {
        // Regression: the cross-ref foreign key threw SQLiteConstraintException when a show was
        // linked to a folder deleted moments earlier.
        val folder = repository.createFolder(name = "Gone", icon = null, displaySize = FolderDisplaySize.COMPACT)
        repository.deleteFolder(folder.id)

        repository.addPodcastToFolder(podcastId = "pod-1", folderId = folder.id)
        repository.setPodcastsForFolder(folder.id, listOf("pod-1", "pod-2"))

        assertTrue(folderDao.getAllCrossRefsList().isEmpty())
        assertFalse(folderDao.addPodcastToFolderIfExists(podcastId = "pod-1", folderId = folder.id))
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
    fun createFolderWithTechLinkedGenreAutoAddsTechnologyShows() = runTest {
        insertSubscribedPodcast("p1", "Tech 1", genre = "Technology")
        insertSubscribedPodcast("p2", "Tech 2", customGenre = "Tech")
        insertSubscribedPodcast("p3", "Coding Pod", customGenre = "Coding")
        insertSubscribedPodcast("p4", "Comedy Show", genre = "Comedy")

        val folder = repository.createFolder(
            name = "My Tech",
            icon = "tech",
            displaySize = FolderDisplaySize.SHELF,
            linkedGenre = "Tech",
        )

        assertEquals(3, folder.podcastCount)
        assertTrue(folder.podcastIds.contains("p1"))
        assertTrue(folder.podcastIds.contains("p2"))
        assertTrue(folder.podcastIds.contains("p3"))
        assertFalse(folder.podcastIds.contains("p4"))
    }

    @Test
    fun createFolderWithTechNameWithoutExplicitGenreAutoAddsTechnologyShows() = runTest {
        insertSubscribedPodcast("p1", "Tech 1", genre = "Technology")
        insertSubscribedPodcast("p2", "Comedy Show", genre = "Comedy")

        val folder = repository.createFolder(
            name = "Tech",
            icon = "tech",
            displaySize = FolderDisplaySize.SHELF,
            linkedGenre = null,
        )

        assertEquals(1, folder.podcastCount)
        assertTrue(folder.podcastIds.contains("p1"))
        assertFalse(folder.podcastIds.contains("p2"))
        assertEquals("Tech", folder.linkedGenre)
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
        insertSubscribedPodcast("p-cat", "Tech Catalog", genre = "Technology")
        insertSubscribedPodcast("p-custom", "Tech Custom", genre = "Society", customGenre = "Technology")
        insertSubscribedPodcast("p-other", "Tech Overridden", genre = "Technology", customGenre = "Favorites")

        val folder = repository.createFolder(
            name = "All Tech",
            linkedGenre = "Technology",
        )

        assertEquals(2, folder.podcastCount)
        assertTrue(folder.podcastIds.contains("p-cat"))
        assertTrue(folder.podcastIds.contains("p-custom"))
        assertFalse(folder.podcastIds.contains("p-other"))
    }

    @Test
    fun legacyTechnologyFolderIsReadAndMigratedToTech() = runTest {
        val legacyEntity = FolderEntity(
            folderId = "f-legacy-tech",
            name = "Technology",
            icon = "folder",
            displaySize = FolderDisplaySize.COMPACT,
            linkedGenre = "Technology",
            showPodcastGrid = false,
            createdAt = 1000L,
        )
        folderDao.upsertFolder(legacyEntity)

        // Read paths dynamically resolve to "Tech" and "tech"
        val folderFromFlow = repository.folders.first().first()
        assertEquals("Tech", folderFromFlow.name)
        assertEquals("tech", folderFromFlow.icon)
        assertEquals("Tech", folderFromFlow.linkedGenre)

        val folderFromGet = repository.getFolder("f-legacy-tech")
        assertNotNull(folderFromGet)
        assertEquals("Tech", folderFromGet?.name)
        assertEquals("tech", folderFromGet?.icon)
        assertEquals("Tech", folderFromGet?.linkedGenre)

        val foldersList = repository.getFolders()
        assertEquals("Tech", foldersList.first().name)
        assertEquals("tech", foldersList.first().icon)

        val folderNames = repository.folderNames.first()
        assertEquals(listOf("Tech"), folderNames)

        // syncLinkedGenres permanently updates the database entity
        repository.syncLinkedGenres()

        val dbEntity = folderDao.getFolder("f-legacy-tech")
        assertNotNull(dbEntity)
        assertEquals("Tech", dbEntity?.name)
        assertEquals("tech", dbEntity?.icon)
        assertEquals("Tech", dbEntity?.linkedGenre)
    }

    @Test
    fun createFolder_supportsShowPodcastGrid() = runTest {
        val folder = repository.createFolder(
            name = "Grid Folder",
            icon = "tech",
            displaySize = FolderDisplaySize.COMPACT,
            showPodcastGrid = true,
        )

        assertNotNull(folder.id)
        assertTrue(folder.showPodcastGrid)
        assertTrue(folder.effectiveShowPodcastGrid)

        val retrieved = repository.getFolder(folder.id)
        assertNotNull(retrieved)
        assertTrue(retrieved?.showPodcastGrid == true)
    }

    @Test
    fun autoOrganizeSubscribedShows_createsDefaultShelfFoldersAndPopulatesShows() = runTest {
        insertSubscribedPodcast("pod-1", "Laugh Out Loud", genre = "Comedy")
        insertSubscribedPodcast("pod-2", "Silicon Talk", genre = "Technology")
        insertSubscribedPodcast("pod-3", "Code Bytes", genre = "Technology")

        repository.autoOrganizeSubscribedShows(
            defaultDisplaySize = FolderDisplaySize.SHELF,
            showPodcastGrid = false,
        )

        val folders = repository.getFolders()
        assertEquals(2, folders.size)

        val techFolder = folders.firstOrNull { it.name == "Tech" }
        assertNotNull(techFolder)
        assertEquals(FolderDisplaySize.SHELF, techFolder?.displaySize)
        assertEquals(listOf("pod-2", "pod-3"), techFolder?.podcastIds?.sorted())
        assertEquals("tech", techFolder?.icon)

        val comedyFolder = folders.firstOrNull { it.name == "Comedy" }
        assertNotNull(comedyFolder)
        assertEquals(FolderDisplaySize.SHELF, comedyFolder?.displaySize)
        assertEquals(listOf("pod-1"), comedyFolder?.podcastIds)
        assertEquals("comedy", comedyFolder?.icon)
    }

    @Test
    fun autoOrganizeSubscribedShows_preservesExistingGenreAndCustomFolders() = runTest {
        insertSubscribedPodcast("pod-1", "My Fav", genre = "Comedy")
        insertSubscribedPodcast("pod-2", "Tech News", genre = "Technology")

        val custom = repository.createFolder(
            name = "Favorites",
            icon = "star",
            displaySize = FolderDisplaySize.COMPACT,
            podcastIds = listOf("pod-1"),
        )

        val existingTech = repository.createFolder(
            name = "Technology",
            icon = "custom_icon",
            displaySize = FolderDisplaySize.PANEL,
            linkedGenre = "Technology",
        )

        repository.autoOrganizeSubscribedShows()

        val allFolders = repository.getFolders()
        assertEquals(3, allFolders.size)

        val retrievedCustom = repository.getFolder(custom.id)
        assertNotNull(retrievedCustom)
        assertEquals(FolderDisplaySize.COMPACT, retrievedCustom?.displaySize)
        assertEquals("star", retrievedCustom?.icon)
        assertEquals(listOf("pod-1"), retrievedCustom?.podcastIds)

        val retrievedTech = repository.getFolder(existingTech.id)
        assertNotNull(retrievedTech)
        assertEquals("Tech", retrievedTech?.name)
        assertEquals(FolderDisplaySize.PANEL, retrievedTech?.displaySize)
        assertEquals("custom_icon", retrievedTech?.icon)
        assertEquals(listOf("pod-2"), retrievedTech?.podcastIds)

        val comedy = allFolders.firstOrNull { it.name == "Comedy" }
        assertNotNull(comedy)
        assertEquals(listOf("pod-1"), comedy?.podcastIds)
    }

    @Test
    fun syncLinkedGenres_movesShowWhenGenreChanges() = runTest {
        val comedyFolder = repository.createFolder(
            name = "Comedy Shows",
            linkedGenre = "Comedy",
        )
        val techFolder = repository.createFolder(
            name = "Tech Hub",
            linkedGenre = "Tech",
        )
        val manualFolder = repository.createFolder(
            name = "My Commute",
            podcastIds = listOf("pod-1"),
        )

        insertSubscribedPodcast("pod-1", "Dynamic Show", genre = "Comedy")

        repository.syncLinkedGenres()

        assertEquals(listOf("pod-1"), repository.getFolder(comedyFolder.id)?.podcastIds)
        assertEquals(emptyList<String>(), repository.getFolder(techFolder.id)?.podcastIds)
        assertEquals(listOf("pod-1"), repository.getFolder(manualFolder.id)?.podcastIds)

        insertSubscribedPodcast("pod-1", "Dynamic Show", genre = "Comedy", customGenre = "Tech")

        repository.syncLinkedGenres()

        assertEquals(emptyList<String>(), repository.getFolder(comedyFolder.id)?.podcastIds)
        assertEquals(listOf("pod-1"), repository.getFolder(techFolder.id)?.podcastIds)
        assertEquals(listOf("pod-1"), repository.getFolder(manualFolder.id)?.podcastIds)
    }

    @Test
    fun syncLinkedGenres_preservesExistingOrderOfMatchingShows() = runTest {
        insertSubscribedPodcast("pod-tech-1", "First Tech", genre = "Tech")
        insertSubscribedPodcast("pod-tech-2", "Second Tech", genre = "Tech")
        insertSubscribedPodcast("pod-tech-3", "Third Tech", genre = "Tech")

        val techFolder = repository.createFolder(
            name = "Tech Hub",
            linkedGenre = "Tech",
            podcastIds = listOf("pod-tech-2", "pod-tech-1"),
        )

        repository.syncLinkedGenres()

        val retrieved = repository.getFolder(techFolder.id)
        assertNotNull(retrieved)
        assertEquals(listOf("pod-tech-2", "pod-tech-1", "pod-tech-3"), retrieved?.podcastIds)
    }

    @Test
    fun autoOrganizeSubscribedShows_adaptiveSizingAssignsSizesBasedOnShowCount() = runTest {
        insertSubscribedPodcast("comedy-1", "Standup Central", genre = "Comedy")

        insertSubscribedPodcast("tech-1", "Tech 1", genre = "Technology")
        insertSubscribedPodcast("tech-2", "Tech 2", genre = "Technology")
        insertSubscribedPodcast("tech-3", "Tech 3", genre = "Technology")
        insertSubscribedPodcast("tech-4", "Tech 4", genre = "Technology")

        insertSubscribedPodcast("news-1", "News 1", genre = "News")
        insertSubscribedPodcast("news-2", "News 2", genre = "News")
        insertSubscribedPodcast("news-3", "News 3", genre = "News")
        insertSubscribedPodcast("news-4", "News 4", genre = "News")
        insertSubscribedPodcast("news-5", "News 5", genre = "News")
        insertSubscribedPodcast("news-6", "News 6", genre = "News")

        repository.autoOrganizeSubscribedShows(defaultDisplaySize = null)

        val folders = repository.getFolders()
        assertEquals(3, folders.size)

        val comedy = folders.first { it.name == "Comedy" }
        assertEquals(FolderDisplaySize.COMPACT, comedy.displaySize)
        assertTrue(comedy.showPodcastGrid)

        val tech = folders.first { it.name == "Tech" }
        assertEquals(FolderDisplaySize.SHELF, tech.displaySize)

        val news = folders.first { it.name == "News" }
        assertEquals(FolderDisplaySize.PANEL, news.displaySize)
    }

    @Test
    fun removePodcastFromAllFoldersRemovesCrossRefsAcrossAllFolders() = runTest {
        insertSubscribedPodcast("pod-1", "Universal Show")
        val f1 = repository.createFolder("Folder 1", podcastIds = listOf("pod-1"))
        val f2 = repository.createFolder("Folder 2", podcastIds = listOf("pod-1"))

        assertEquals(listOf("pod-1"), repository.getFolder(f1.id)?.podcastIds)
        assertEquals(listOf("pod-1"), repository.getFolder(f2.id)?.podcastIds)

        repository.removePodcastFromAllFolders("pod-1")

        assertEquals(emptyList<String>(), repository.getFolder(f1.id)?.podcastIds)
        assertEquals(emptyList<String>(), repository.getFolder(f2.id)?.podcastIds)
    }
}
