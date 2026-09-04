package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeMixModeLogicTest {
    @Test
    fun `persisted mode values restore safely`() {
        assertEquals(HomeMixMode.OFFLINE, HomeMixMode.fromPersistedValue("offline"))
        assertEquals(HomeMixMode.DAILY, HomeMixMode.fromPersistedValue("unknown"))
    }

    @Test
    fun `offline requires two subscriptions and one completed download`() {
        assertFalse(HomeMixModeLogic.canOfferOffline(subscriptionCount = 1, completedDownloadCount = 1))
        assertFalse(HomeMixModeLogic.canOfferOffline(subscriptionCount = 2, completedDownloadCount = 0))
        assertTrue(HomeMixModeLogic.canOfferOffline(subscriptionCount = 2, completedDownloadCount = 1))
    }

    @Test
    fun `offline falls back to daily when eligibility disappears`() {
        assertEquals(
            HomeMixMode.DAILY,
            HomeMixModeLogic.resolveMode(
                requested = HomeMixMode.OFFLINE,
                canOfferOffline = false,
            ),
        )
        assertEquals(
            HomeMixMode.OFFLINE,
            HomeMixModeLogic.resolveMode(
                requested = HomeMixMode.OFFLINE,
                canOfferOffline = true,
            ),
        )
    }

    @Test
    fun `offline excludes completed downloaded episodes`() {
        val downloads = listOf(download(1), download(2), download(3))

        val eligible =
            HomeMixModeLogic.eligibleOfflineItems(
                items = downloads,
                completedEpisodeIds = setOf("offline-2"),
            )

        assertEquals(listOf("offline-1", "offline-3"), eligible.map { it.episode.id })
    }

    @Test
    fun `home offline rail and queue share the same fifteen item cap`() {
        val downloads = (1..20).map(::download)

        val visible = HomeMixModeLogic.visibleOfflineItems(downloads)
        val queued =
            HomeMixModeLogic.queueEpisodes(
                mode = HomeMixMode.OFFLINE,
                dailyEpisodes = emptyList(),
                completedDownloads = downloads,
            )

        assertEquals(15, visible.size)
        assertEquals(visible.map { it.episode.id }, queued.map(Episode::id))
    }

    @Test
    fun `daily queue remains the ranked daily order`() {
        val daily = listOf(episode("daily-2"), episode("daily-1"))

        val queued =
            HomeMixModeLogic.queueEpisodes(
                mode = HomeMixMode.DAILY,
                dailyEpisodes = daily,
                completedDownloads = listOf(download(1)),
            )

        assertEquals(listOf("daily-2", "daily-1"), queued.map(Episode::id))
    }

    private fun download(index: Int): CompletedDownloadItem {
        val episode = episode("offline-$index")
        val podcast =
            Podcast(
                id = "pod-$index",
                title = "Podcast $index",
                artist = "",
                imageUrl = "",
            )
        return CompletedDownloadItem(
            episode = episode,
            podcast = podcast,
            downloadedAt = index.toLong(),
        )
    }

    private fun episode(id: String): Episode = Episode(
        id = id,
        title = id,
        description = "",
        audioUrl = "/downloads/$id.mp3",
    )
}
