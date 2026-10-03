package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoQueueContextTest {
    @Test fun restoredQueueCarriesContextThroughAutoMediaFactory() {
        val episode = PlaybackQueueContext.episodes(listOf(Episode("rss:1", "Episode", "", "https://example.com/1.mp3"))).single()
        val item = AutoMediaItemFactory.fromEpisode(episode, AutoBrowseContract.SOURCE_QUEUE, null, mediaIdPrefix = AutoBrowseContract.QUEUE_PREFIX)
        assertEquals("queue:rss:1", item.mediaId)
        assertTrue(PlaybackQueueContext.isContextItem(item))
    }
}
