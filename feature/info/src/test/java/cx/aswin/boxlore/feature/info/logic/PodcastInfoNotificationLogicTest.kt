package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoNotificationLogicTest {
    private val rss = TestFixtures.podcast(id = "rss:saved").copy(sourceType = Podcast.SOURCE_RSS, autoDownloadEnabled = true)

    @Test fun `ViewModel toggle action keeps notifications off when repository refuses consent`() = runTest {
        var requested = false
        val updated = togglePodcastNotifications(rss, false, null) { show, enabled, accepted, url ->
            assertEquals(rss, show)
            assertTrue(enabled)
            assertFalse(accepted)
            assertEquals(null, url)
            requested = true
            false
        }
        assertTrue(requested)
        assertFalse(updated.notificationsEnabled)
        assertTrue(updated.autoDownloadEnabled)
    }

    @Test fun `ViewModel toggle forwards confirmed URL and applies accepted activation and later disable`() = runTest {
        val url = "https://publisher.example/feed"
        val enabled = togglePodcastNotifications(rss, true, url) { _, desired, accepted, disclosed ->
            assertTrue(desired)
            assertTrue(accepted)
            assertEquals(url, disclosed)
            true
        }
        assertTrue(enabled.notificationsEnabled)
        val disabled = togglePodcastNotifications(enabled, false, null) { _, desired, _, _ ->
            assertFalse(desired)
            false
        }
        assertFalse(disabled.notificationsEnabled)
        assertTrue(disabled.autoDownloadEnabled)
    }

    @Test fun `ViewModel enable both action preserves RSS consent refusal while enabling downloads`() = runTest {
        val actions = mutableListOf<String>()
        val updated = enablePodcastNotificationsAndAutoDownload(
            rss.copy(autoDownloadEnabled = false),
            setNotifications = { show, enabled ->
                assertEquals(rss.id, show.id)
                assertTrue(enabled)
                actions += "notifications"
                false
            },
            setAutoDownload = { id, enabled ->
                assertEquals(rss.id, id)
                assertTrue(enabled)
                actions += "download"
            },
        )
        assertEquals(listOf("notifications", "download"), actions)
        assertFalse(updated.notificationsEnabled)
        assertTrue(updated.autoDownloadEnabled)
    }

    @Test fun `disclosure recognizes canonical RSS IDs even with stale source metadata`() {
        assertTrue(requiresRssNotificationDisclosure(rss.copy(sourceType = Podcast.SOURCE_PODCAST_INDEX)))
        assertTrue(requiresRssNotificationDisclosure(rss))
        assertFalse(requiresRssNotificationDisclosure(rss.copy(notificationsEnabled = true)))
        assertFalse(requiresRssNotificationDisclosure(rss.copy(id = "123", sourceType = Podcast.SOURCE_PODCAST_INDEX)))
    }
}
