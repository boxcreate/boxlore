package cx.aswin.boxlore.feature.library.subscriptions

import android.app.Application
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import cx.aswin.boxlore.feature.library.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Runs on Robolectric so the English text comes from the module's resources. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FolderSummaryLogicTest {

    private fun UiText.en(): String = resolve(RuntimeEnvironment.getApplication())

    private fun text(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun folder(id: String, size: FolderDisplaySize, genre: String? = null) =
        SubscriptionFolder(id = id, name = id, displaySize = size, linkedGenre = genre)

    @Test
    fun `auto-organize row says off, or how many genre folders it keeps filled`() {
        assertEquals("Off · Sort every show into genre folders", autoOrganizeSummary(enabled = false, genreFolderCount = 3).en())
        assertEquals("On · 1 genre folder", autoOrganizeSummary(enabled = true, genreFolderCount = 1).en())
        assertEquals("On · 4 genre folders", autoOrganizeSummary(enabled = true, genreFolderCount = 4).en())
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
        assertEquals("3 shows", folderPageSummary(3, 0).en())
        assertEquals("3 shows · 1 new episode", folderPageSummary(3, 1).en())
        assertEquals("1 show · 2 new episodes", folderPageSummary(1, 2).en())
    }

    @Test
    fun `look names and descriptions say where it sits and how many covers open directly`() {
        assertEquals(listOf("Tile", "1 row", "2 rows", "3 rows"), FolderLookOptions.map { folderLookName(it).en() })
        assertEquals("Among your shows. Tap up to 4 directly.", folderLookDescription(FolderDisplaySize.COMPACT).en())
        assertEquals("Pinned on top. Tap up to 3 directly.", folderLookDescription(FolderDisplaySize.SHELF).en())
        assertEquals("Pinned on top. Tap up to 6 directly.", folderLookDescription(FolderDisplaySize.PANEL).en())
        assertEquals("Pinned on top. Tap up to 9 directly.", folderLookDescription(FolderDisplaySize.SHOWCASE).en())
    }

    @Test
    fun `apply to all names how many genre folders change`() {
        assertEquals("Apply to 1 genre folder", applyToAllLabel(1).en())
        assertEquals("Apply to all 5 genre folders", applyToAllLabel(5).en())
    }

    @Test
    fun `a show count reads naturally`() {
        assertEquals("1 show", showCountLabel(1).en())
        assertEquals("0 shows", showCountLabel(0).en())
        assertEquals("12 shows", showCountLabel(12).en())
    }

    @Test
    fun `genre and auto say that shows followed later are added`() {
        assertEquals("Shows you follow later in this genre are added automatically.", genreAutoAddNote(null).en())
        assertEquals("Shows you follow later in News are added automatically.", genreAutoAddNote(" News ").en())
        assertEquals(true, text(AUTO_ORGANIZE_NOTE).contains("shows you follow later are added automatically"))
        // Auto-organize fills a folder the listener already has for a genre, so the note says so.
        assertEquals(true, text(AUTO_ORGANIZE_NOTE).contains("A folder you already have for a genre is filled too"))
    }

    @Test
    fun `a catalog genre in a note uses its shown name`() {
        assertEquals("Shows you follow later in Tech are added automatically.", genreAutoAddNote("Tech").en())
        assertEquals(UiText.res(R.string.library_genre_auto_add_note, UiText.raw("Coding")), genreAutoAddNote("Coding"))
    }
}
