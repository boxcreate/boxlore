package cx.aswin.boxlore.core.playback.service

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which covers each Home block's collage is drawn from. */
class AutoCollagePrewarmerTest {
    private val noMix = MixtapeEngine.Result(podcasts = emptyList(), episodes = emptyList(), unplayedCount = 0)

    private fun history(id: String) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "100",
        episodeTitle = "Episode $id",
        episodeImageUrl = "https://example.com/$id.jpg",
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show",
        progressMs = 10L,
        durationMs = 1_000L,
        isCompleted = false,
        lastPlayedAt = 1L,
    )

    private fun queued(id: String) = Episode(id, "Episode $id", "", "https://example.com/$id.mp3", imageUrl = "https://example.com/$id.jpg")

    private fun inputs(
        history: List<ListeningHistoryEntity> = emptyList(),
        resume: List<ListeningHistoryEntity> = emptyList(),
        liked: List<ListeningHistoryEntity> = emptyList(),
        queue: List<Episode> = emptyList(),
    ) = AutoCollagePrewarmer.PrewarmSnapshot(
        history = history,
        resumeItems = resume,
        liked = liked,
        subscriptions = emptyList(),
        downloads = emptyList(),
        queue = queue,
    ).buildFolderInputs(noMix).mapValues { (_, pairs) -> pairs.map { it.contentKey } }

    @Test
    fun `the Liked and History blocks draw from liked and recently played episodes`() {
        val folders = inputs(history = listOf(history("h1"), history("h2")), liked = listOf(history("l1")))

        assertEquals(listOf("l1"), folders[AutoBrowseContract.LIBRARY_LIKED_ID])
        assertEquals(listOf("h1", "h2"), folders[AutoBrowseContract.LIBRARY_HISTORY_ID])
    }

    @Test
    fun `an episode both in progress and queued appears once in the queue collage`() {
        val folders = inputs(resume = listOf(history("a")), queue = listOf(queued("a"), queued("b")))

        assertEquals(listOf("a", "b"), folders[AutoBrowseContract.HOME_QUEUE_ID])
    }

    @Test
    fun `a collage uses at most four covers`() {
        val folders = inputs(history = (1..6).map { history("h$it") })

        assertEquals(4, folders[AutoBrowseContract.LIBRARY_HISTORY_ID]?.size)
    }

    @Test
    fun `the Drive picks block draws on the queue`() {
        // Why a queue change has to rebuild Home's artwork.
        val folders = inputs(queue = listOf(queued("q1"), queued("q2")))

        assertEquals(listOf("q1", "q2"), folders[AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID])
    }

    @Test
    fun `Home is told about artwork only when a block's artwork changed`() {
        val announced = CollageAnnouncements<String>()

        assertEquals(true, announced.isNews(mapOf("drive" to "drive.png?v=1", "liked" to "liked.png?v=1")))
        // A rebuild that came out the same: reloading Home would only send it back to the top.
        assertEquals(false, announced.isNews(mapOf("drive" to "drive.png?v=1", "liked" to "liked.png?v=1")))
        assertEquals(true, announced.isNews(mapOf("drive" to "drive.png?v=2", "liked" to "liked.png?v=1")))
        assertEquals(true, announced.isNews(mapOf("liked" to "liked.png?v=1")))
        assertEquals(false, announced.isNews(mapOf("liked" to "liked.png?v=1")))
    }
}
