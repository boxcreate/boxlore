package cx.aswin.boxlore.core.catalog.backup

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryBackupManualOrderPinsTest {
    private val gson = Gson()

    @Test
    fun `gson round trips manual order and home pins`() {
        val prefs =
            GlobalPreferencesBackup(
                subscriptionSort = "Manual",
                subscriptionManualOrder = listOf("a", "rss:https://feeds.example/x.xml"),
                homePinnedPodcastIds = listOf("p1", "p2"),
            )
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals("Manual", parsed.subscriptionSort)
        assertEquals(listOf("a", "rss:https://feeds.example/x.xml"), parsed.subscriptionManualOrder)
        assertEquals(listOf("p1", "p2"), parsed.homePinnedPodcastIds)
    }

    @Test
    fun `older backup without order lists still parses`() {
        val parsed =
            gson.fromJson(
                """{"subscriptionSort":"SmartRank"}""",
                GlobalPreferencesBackup::class.java,
            )
        assertEquals("SmartRank", parsed.subscriptionSort)
        assertNull(parsed.subscriptionManualOrder)
        assertNull(parsed.homePinnedPodcastIds)
    }
}
