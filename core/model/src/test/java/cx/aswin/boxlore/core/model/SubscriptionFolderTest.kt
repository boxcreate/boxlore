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
}
