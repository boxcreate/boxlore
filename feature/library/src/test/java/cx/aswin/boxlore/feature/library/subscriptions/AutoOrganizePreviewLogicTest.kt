package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutoOrganizePreviewLogicTest {

    @Test
    fun `preview groups by custom genre, else the first genre, and leaves placeholder shows out`() {
        val podcasts = listOf(
            TestFixtures.podcast(id = "a", genre = "News, Politics"),
            TestFixtures.podcast(id = "b", genre = "News"),
            TestFixtures.podcast(id = "c", genre = "Comedy").copy(customGenre = "News"),
            TestFixtures.podcast(id = "d", genre = "Comedy"),
            TestFixtures.podcast(id = "e", genre = "Podcast"),
            TestFixtures.podcast(id = "f", genre = " "),
        )
        val preview = autoOrganizePreview(podcasts, folders = emptyList())
        assertEquals(listOf("News" to 3, "Comedy" to 1), preview.rows.map { it.genre to it.showCount })
        assertEquals(2, preview.showsWithoutGenre)
        assertEquals(2, preview.newFolders.size)
    }

    @Test
    fun `a genre with a folder already fills that folder`() {
        val podcasts = listOf(
            TestFixtures.podcast(id = "a", genre = "Technology"),
            TestFixtures.podcast(id = "b", genre = "Comedy"),
            TestFixtures.podcast(id = "c", genre = "History"),
        )
        val folders = listOf(
            SubscriptionFolder(id = "t", name = "Technology"),
            SubscriptionFolder(id = "l", name = "Laughs", linkedGenre = "Comedy"),
        )
        val preview = autoOrganizePreview(podcasts, folders)
        val byGenre = preview.rows.associateBy { it.genre }
        assertEquals("Technology", byGenre.getValue("Tech").existingFolderName)
        assertEquals("Laughs", byGenre.getValue("Comedy").existingFolderName)
        assertEquals(listOf("History"), preview.newFolders.map { it.genre })
        assertEquals(2, preview.existingFolders.size)
    }

    @Test
    fun `boxlore picks the look by size, as the repository does`() {
        assertEquals(FolderDisplaySize.COMPACT, autoOrganizeSizeFor(1))
        assertEquals(FolderDisplaySize.COMPACT, autoOrganizeSizeFor(2))
        assertEquals(FolderDisplaySize.SHELF, autoOrganizeSizeFor(3))
        assertEquals(FolderDisplaySize.SHELF, autoOrganizeSizeFor(5))
        assertEquals(FolderDisplaySize.PANEL, autoOrganizeSizeFor(6))
    }

    @Test
    fun `apply to all keeps covers on chosen tiles and follows a fixed look`() {
        assertEquals(FolderDisplaySize.COMPACT to true, autoOrganizeLookFor(2, AutoOrganizeLook()))
        assertEquals(FolderDisplaySize.PANEL to false, autoOrganizeLookFor(8, AutoOrganizeLook()))
        assertEquals(
            FolderDisplaySize.COMPACT to false,
            autoOrganizeLookFor(8, AutoOrganizeLook(fixedSize = FolderDisplaySize.COMPACT, tileShowsCovers = false)),
        )
        assertEquals(
            FolderDisplaySize.SHELF to false,
            autoOrganizeLookFor(1, AutoOrganizeLook(fixedSize = FolderDisplaySize.SHELF, tileShowsCovers = true)),
        )
    }

    @Test
    fun `an unknown saved look size lets boxlore choose`() {
        assertEquals(AutoOrganizeLook(fixedSize = null, tileShowsCovers = true), autoOrganizeLookFromSaved(null, true))
        assertEquals(AutoOrganizeLook(fixedSize = null, tileShowsCovers = false), autoOrganizeLookFromSaved("GONE", false))
        assertEquals(AutoOrganizeLook(fixedSize = FolderDisplaySize.SHOWCASE), autoOrganizeLookFromSaved("SHOWCASE", true))
    }
}
