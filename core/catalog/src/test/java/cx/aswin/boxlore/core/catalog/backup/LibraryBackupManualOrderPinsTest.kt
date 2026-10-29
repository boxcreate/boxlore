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
        assertNull(parsed.sameShowQueueOnly)
        assertNull(parsed.homeShortcutsInLibrary)
        assertNull(parsed.widgetAppearance)
        assertNull(parsed.exploreDefaultTab)
        assertNull(parsed.subscriptionsDefaultTab)
    }

    @Test
    fun `gson round trips English off with its languages, and older backups leave it unset`() {
        val prefs = GlobalPreferencesBackup(contentLanguages = listOf("de", "fr", "es", "nl"), contentEnglishOff = true)
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals(true, parsed.contentEnglishOff)
        assertEquals(listOf("de", "fr", "es", "nl"), parsed.contentLanguages)
        assertNull(gson.fromJson("""{"contentLanguages":["en","de"]}""", GlobalPreferencesBackup::class.java).contentEnglishOff)
    }

    @Test
    fun `gson round trips same-show queue only`() {
        val prefs = GlobalPreferencesBackup(sameShowQueueOnly = true)
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals(true, parsed.sameShowQueueOnly)
    }

    @Test
    fun `gson round trips home shortcuts in library`() {
        val prefs = GlobalPreferencesBackup(homeShortcutsInLibrary = true)
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals(true, parsed.homeShortcutsInLibrary)
    }

    @Test
    fun `gson round trips widget appearance`() {
        val prefs = GlobalPreferencesBackup(widgetAppearance = "system")
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals("system", parsed.widgetAppearance)
    }

    @Test
    fun `gson round trips default landing tabs`() {
        val prefs =
            GlobalPreferencesBackup(
                exploreDefaultTab = "top",
                subscriptionsDefaultTab = "new_episodes",
                subscriptionsTabStyle = "floating",
                navigationStyle = "classic",
                fontRoundness = "soft",
            )
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals("top", parsed.exploreDefaultTab)
        assertEquals("new_episodes", parsed.subscriptionsDefaultTab)
        assertEquals("floating", parsed.subscriptionsTabStyle)
        assertEquals("classic", parsed.navigationStyle)
        assertEquals("soft", parsed.fontRoundness)
    }

    @Test
    fun `gson round trips folder sorts and manual order`() {
        val prefs =
            GlobalPreferencesBackup(
                subscriptionFolderSort = "Manual",
                subscriptionIntraFolderSort = "Alphabetical",
                subscriptionFolderManualOrder = listOf("folder-1", "folder-2"),
                autoOrganizeFolders = true,
            )
        val parsed = gson.fromJson(gson.toJson(prefs), GlobalPreferencesBackup::class.java)
        assertEquals("Manual", parsed.subscriptionFolderSort)
        assertEquals("Alphabetical", parsed.subscriptionIntraFolderSort)
        assertEquals(listOf("folder-1", "folder-2"), parsed.subscriptionFolderManualOrder)
        assertEquals(true, parsed.autoOrganizeFolders)
    }

    @Test
    fun `gson round trips BoxLoreBackup with folders`() {
        val backup =
            BoxLoreBackup(
                version = 6,
                subscriptions = emptyList(),
                history = emptyList(),
                folders = listOf(
                    SubscriptionFolderBackup(
                        id = "folder-tech",
                        name = "Tech",
                        icon = "tech",
                        displaySize = "SHELF",
                        linkedGenre = "Tech",
                        showPodcastGrid = true,
                        podcastIds = listOf("pod-1", "pod-2"),
                    ),
                ),
            )
        val parsed = gson.fromJson(gson.toJson(backup), BoxLoreBackup::class.java)
        assertEquals(6, parsed.version)
        org.junit.jupiter.api.Assertions.assertNotNull(parsed.folders)
        assertEquals(1, parsed.folders?.size)
        val folder = parsed.folders?.first()
        assertEquals("folder-tech", folder?.id)
        assertEquals("Tech", folder?.name)
        assertEquals(listOf("pod-1", "pod-2"), folder?.podcastIds)
    }
}
