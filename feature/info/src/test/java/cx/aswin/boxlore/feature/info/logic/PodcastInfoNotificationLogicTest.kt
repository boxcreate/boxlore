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

    @Test fun `both notification entry points defer RSS activation and retain the disclosed feed URL`() {
        var confirmation: (() -> Unit)? = null
        var proceeded = false
        val url = "https://publisher.example/original-feed"
        requestPodcastNotificationAction(
            rss.copy(feedUrl = url),
            onDisclosureRequired = { confirmation = it },
            onProceed = { accepted, disclosed ->
                assertTrue(accepted)
                assertEquals(url, disclosed)
                proceeded = true
            },
        )
        assertFalse(proceeded)
        confirmation!!.invoke()
        assertTrue(proceeded)
    }

    @Test fun `catalog activation and disabling existing RSS notifications need no disclosure`() {
        val shows = listOf(
            rss.copy(notificationsEnabled = true),
            rss.copy(id = "123", sourceType = Podcast.SOURCE_PODCAST_INDEX),
        )
        for (show in shows) {
            var proceeded = false
            requestPodcastNotificationAction(
                show,
                onDisclosureRequired = { error("unexpected disclosure") },
                onProceed = { accepted, disclosed ->
                    assertFalse(accepted)
                    assertEquals(null, disclosed)
                    proceeded = true
                },
            )
            assertTrue(proceeded)
        }
    }

    @Test fun `disclosure recognizes canonical RSS IDs even with stale source metadata`() {
        assertTrue(requiresRssNotificationDisclosure(rss.copy(sourceType = Podcast.SOURCE_PODCAST_INDEX)))
        assertTrue(requiresRssNotificationDisclosure(rss))
        assertFalse(requiresRssNotificationDisclosure(rss.copy(notificationsEnabled = true)))
        assertFalse(requiresRssNotificationDisclosure(rss.copy(id = "123", sourceType = Podcast.SOURCE_PODCAST_INDEX)))
    }
}
