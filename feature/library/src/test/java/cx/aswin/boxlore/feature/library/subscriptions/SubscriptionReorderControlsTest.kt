package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.library.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionReorderControlsTest {

    private val podcastA = TestFixtures.podcast(id = "pod-a", title = "Alpha")
    private val podcastB = TestFixtures.podcast(id = "pod-b", title = "Bravo")
    private val podcastC = TestFixtures.podcast(id = "pod-c", title = "Charlie")
    private val unfiled = listOf(podcastA, podcastB, podcastC)

    @Test
    fun `resolveOrderedPodcasts returns unfiled unchanged when not manual and not reordering`() {
        val result = resolveOrderedPodcasts(
            unfiledPodcasts = unfiled,
            orderedKeys = listOf("pod-c", "pod-a", "pod-b"),
            isManualSort = false,
            isRootReordering = false,
        )
        assertEquals(listOf("pod-a", "pod-b", "pod-c"), result.map { it.id })
    }

    @Test
    fun `resolveOrderedPodcasts never repeats a show the grid keys by id`() {
        // Regression: a repeated id in the saved order or the subscription rows crashed the keyed grid.
        val manual = resolveOrderedPodcasts(
            unfiledPodcasts = unfiled + podcastA,
            orderedKeys = listOf("pod-b", "pod-b", "pod-a"),
            isManualSort = true,
        )
        assertEquals(listOf("pod-b", "pod-a", "pod-c"), manual.map { it.id })
        val automatic = resolveOrderedPodcasts(
            unfiledPodcasts = unfiled + podcastA,
            orderedKeys = emptyList(),
            isManualSort = false,
        )
        assertEquals(listOf("pod-a", "pod-b", "pod-c"), automatic.map { it.id })
    }

    @Test
    fun `resolveOrderedPodcasts respects orderedKeys when isManualSort is true`() {
        val result = resolveOrderedPodcasts(
            unfiledPodcasts = unfiled,
            orderedKeys = listOf("pod-c", "pod-a", "pod-b"),
            isManualSort = true,
            isRootReordering = false,
        )
        assertEquals(listOf("pod-c", "pod-a", "pod-b"), result.map { it.id })
    }

    @Test
    fun `resolveOrderedPodcasts respects orderedKeys during isRootReordering even if not manual sort`() {
        // Regression test for Issue 1: dragged show jumping around because isManualSort is false during drag
        val result = resolveOrderedPodcasts(
            unfiledPodcasts = unfiled,
            orderedKeys = listOf("pod-b", "pod-c", "pod-a"),
            isManualSort = false,
            isRootReordering = true,
        )
        assertEquals(listOf("pod-b", "pod-c", "pod-a"), result.map { it.id })
    }

    @Test
    fun `resolveOrderedPodcasts appends missing unfiled shows at the end`() {
        val result = resolveOrderedPodcasts(
            unfiledPodcasts = unfiled,
            orderedKeys = listOf("pod-b"),
            isManualSort = true,
            isRootReordering = false,
        )
        assertEquals(listOf("pod-b", "pod-a", "pod-c"), result.map { it.id })
    }

    @Test
    fun `isFolderKey detects all folder prefixes`() {
        assertTrue(isFolderKey("folder_f0"))
        assertTrue(isFolderKey("compact_folder_f1"))
        assertTrue(isFolderKey("pinned_folder_f2"))
        assertTrue(isFolderKey("list_folder_f3"))
        assertFalse(isFolderKey("pod-a"))
        assertFalse(isFolderKey(ShowsGenreHeaderKey))
    }

    @Test
    fun `extractFolderId extracts id correctly from key`() {
        assertEquals("f0", extractFolderId("folder_f0"))
        assertEquals("f1", extractFolderId("compact_folder_f1"))
        assertEquals("f2", extractFolderId("pinned_folder_f2"))
        assertEquals("f3", extractFolderId("list_folder_f3"))
        assertEquals(null, extractFolderId("pod-a"))
    }

    @Test
    fun `isReorderablePodcastKey filters out folders and genre header`() {
        assertTrue(isReorderablePodcastKey("pod-123"))
        assertFalse(isReorderablePodcastKey("folder_f0"))
        assertFalse(isReorderablePodcastKey("compact_folder_f1"))
        assertFalse(isReorderablePodcastKey("pinned_folder_f2"))
        assertFalse(isReorderablePodcastKey("list_folder_f3"))
        assertFalse(isReorderablePodcastKey(ShowsGenreHeaderKey))
    }

    @Test
    fun `resolveReorderBarContent produces expressive title with folder name`() {
        val folderContent = resolveReorderBarContent(ReorderMode.Folders)
        assertEquals(UiText.res(R.string.library_reorder_title_folders), folderContent.title)
        assertEquals(UiText.res(R.string.library_reorder_drag_to_arrange), folderContent.subtitle)
        assertEquals(UiText.res(R.string.library_reorder_switches_to_manual), folderContent.note)

        val namedFolderShows = resolveReorderBarContent(ReorderMode.FolderShows("f1", "Tech News"))
        assertEquals(UiText.res(R.string.library_reorder_title_named, UiText.raw("Tech News")), namedFolderShows.title)
        assertEquals(UiText.res(R.string.library_reorder_drag_to_arrange), namedFolderShows.subtitle)
        assertEquals(UiText.res(R.string.library_reorder_switches_to_manual), namedFolderShows.note)

        val unnamedFolderShows = resolveReorderBarContent(ReorderMode.FolderShows("f1", ""))
        assertEquals(UiText.res(R.string.library_reorder_title_named, UiText.res(R.string.library_folder_fallback_name)), unnamedFolderShows.title)
        assertEquals(UiText.res(R.string.library_reorder_drag_to_arrange), unnamedFolderShows.subtitle)
        assertEquals(UiText.res(R.string.library_reorder_switches_to_manual), unnamedFolderShows.note)

        val rootShowsContent = resolveReorderBarContent(ReorderMode.RootShows)
        assertEquals(UiText.res(R.string.library_reorder_title_shows), rootShowsContent.title)
        assertEquals(UiText.res(R.string.library_reorder_drag_to_arrange), rootShowsContent.subtitle)
        assertEquals(UiText.res(R.string.library_reorder_switches_to_manual), rootShowsContent.note)
    }
}
