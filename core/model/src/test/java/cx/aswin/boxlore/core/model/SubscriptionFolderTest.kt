package cx.aswin.boxlore.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionFolderTest {

    @Test
    fun `default values allow iconless folder with compact size`() {
        val folder = SubscriptionFolder(
            id = "folder-1",
            name = "Tech Shows",
        )

        assertEquals("folder-1", folder.id)
        assertEquals("Tech Shows", folder.name)
        assertNull(folder.icon)
        assertFalse(folder.hasIcon)
        assertEquals(FolderDisplaySize.COMPACT, folder.displaySize)
        assertNull(folder.linkedGenre)
        assertFalse(folder.isGenreLinked)
        assertEquals(0, folder.podcastCount)
        assertTrue(folder.podcastIds.isEmpty())
    }

    @Test
    fun `folder with icon and linked genre reflects helper properties`() {
        val folder = SubscriptionFolder(
            id = "folder-2",
            name = "Comedy Highlights",
            icon = "comedy",
            displaySize = FolderDisplaySize.FEATURED,
            linkedGenre = "Comedy",
            podcastCount = 3,
            podcastIds = listOf("pod-1", "pod-2", "pod-3"),
        )

        assertEquals("comedy", folder.icon)
        assertTrue(folder.hasIcon)
        assertEquals(FolderDisplaySize.FEATURED, folder.displaySize)
        assertEquals("Comedy", folder.linkedGenre)
        assertTrue(folder.isGenreLinked)
        assertEquals(3, folder.podcastCount)
        assertEquals(3, folder.podcastIds.size)
    }

    @Test
    fun `blank icon treated as not having icon`() {
        val folder = SubscriptionFolder(
            id = "folder-3",
            name = "News",
            icon = "   ",
        )

        assertFalse(folder.hasIcon)
    }

    @Test
    fun `folder supports wide and large 2x3 max display sizes`() {
        val wideFolder = SubscriptionFolder(
            id = "folder-4",
            name = "Wide Section",
            displaySize = FolderDisplaySize.WIDE,
        )
        assertEquals(FolderDisplaySize.WIDE, wideFolder.displaySize)
        assertEquals(2, wideFolder.displaySize.spanCols)
        assertEquals(1, wideFolder.displaySize.spanRows)
        assertEquals("2×1", wideFolder.displaySize.dimensionsLabel)

        val largeFolder = SubscriptionFolder(
            id = "folder-5",
            name = "Large 2x3 Section",
            displaySize = FolderDisplaySize.LARGE,
        )
        assertEquals(FolderDisplaySize.LARGE, largeFolder.displaySize)
        assertEquals(2, largeFolder.displaySize.spanCols)
        assertEquals(3, largeFolder.displaySize.spanRows)
        assertEquals("2×3", largeFolder.displaySize.dimensionsLabel)
    }
}
