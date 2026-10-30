package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FolderSummaryLogicTest {

    private fun folder(id: String, size: FolderDisplaySize, genre: String? = null) =
        SubscriptionFolder(id = id, name = id, displaySize = size, linkedGenre = genre)

    @Test
    fun `auto-organize row says off, or how many genre folders it keeps filled`() {
        assertEquals("Off · Sort every show into genre folders", autoOrganizeSummary(enabled = false, genreFolderCount = 3))
        assertEquals("On · 1 genre folder", autoOrganizeSummary(enabled = true, genreFolderCount = 1))
        assertEquals("On · 4 genre folders", autoOrganizeSummary(enabled = true, genreFolderCount = 4))
    }

    @Test
    fun `genre folders are the ones linked to a genre`() {
        val folders = listOf(folder("News", FolderDisplaySize.SHELF, "News"), folder("Mine", FolderDisplaySize.SHELF), folder("Tech", FolderDisplaySize.COMPACT, " "))
        assertEquals(listOf("News"), genreFolders(folders).map { it.id })
    }

    @Test
    fun `section labels show only when pinned folders come first`() {
        val pinnedFirst = listOf(folder("a", FolderDisplaySize.PANEL), folder("b", FolderDisplaySize.SHELF), folder("c", FolderDisplaySize.COMPACT))
        assertEquals(ShowsSectionLabels(yourShowsBeforeFolderIndex = 2), showsSectionLabels(pinnedFirst, isFoldersReordering = false))

        val mixed = listOf(folder("a", FolderDisplaySize.COMPACT), folder("b", FolderDisplaySize.SHELF))
        assertNull(showsSectionLabels(mixed, isFoldersReordering = false))
        assertNull(showsSectionLabels(listOf(folder("a", FolderDisplaySize.COMPACT)), isFoldersReordering = false))
    }

    @Test
    fun `section labels never split folders while they are dragged`() {
        val pinnedFirst = listOf(folder("a", FolderDisplaySize.PANEL), folder("b", FolderDisplaySize.COMPACT))
        assertNull(showsSectionLabels(pinnedFirst, isFoldersReordering = true))
    }

    @Test
    fun `folder page summary counts new episodes`() {
        assertEquals("3 shows", folderPageSummary(3, 0))
        assertEquals("3 shows · 1 new episode", folderPageSummary(3, 1))
        assertEquals("1 show · 2 new episodes", folderPageSummary(1, 2))
    }

    @Test
    fun `look names and descriptions say where it sits and how many covers open directly`() {
        assertEquals(listOf("Tile", "1 row", "2 rows", "3 rows"), FolderLookOptions.map(::folderLookName))
        assertEquals("Among your shows. Tap up to 4 directly.", folderLookDescription(FolderDisplaySize.COMPACT))
        assertEquals("Pinned on top. Tap up to 3 directly.", folderLookDescription(FolderDisplaySize.SHELF))
        assertEquals("Pinned on top. Tap up to 6 directly.", folderLookDescription(FolderDisplaySize.PANEL))
        assertEquals("Pinned on top. Tap up to 9 directly.", folderLookDescription(FolderDisplaySize.SHOWCASE))
    }

    @Test
    fun `apply to all names how many genre folders change`() {
        assertEquals("Apply to 1 genre folder", applyToAllLabel(1))
        assertEquals("Apply to all 5 genre folders", applyToAllLabel(5))
    }

    @Test
    fun `a show count reads naturally`() {
        assertEquals("1 show", showCountLabel(1))
        assertEquals("0 shows", showCountLabel(0))
        assertEquals("12 shows", showCountLabel(12))
    }
}
