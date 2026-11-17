package cx.aswin.boxlore.core.playback

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The player shows a downloaded episode's saved chapters and transcript even when the network stalls. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackChaptersTranscriptControllerTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastRepository: PodcastRepository
    private val state = MutableStateFlow(PlayerState())
    private var remoteReads = 0
    private val scheduler = TestCoroutineScheduler()

    /** The controller's own long-lived scope, on the test's clock and cancelled after each test. */
    private val controllerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))

    private val savedChapters = listOf(Chapter(0.0, "Saved chapter"))
    private val savedTranscript = listOf(TranscriptSegment(0L, 1_000L, "Saved line"))
    private val remoteChapters = listOf(Chapter(0.0, "Remote chapter"))
    private val remoteTranscript = listOf(TranscriptSegment(0L, 1_000L, "Remote line"))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcastRepository =
            PodcastRepository(
                baseUrl = "https://api.boxlore.test",
                publicKey = "test-key",
                context = context,
                rssRepository = RssPodcastRepository.createForTests(context = context, database = database),
                boxLoreApi = NetworkModule.createBoxLoreApi("https://api.boxlore.test", NetworkModule.createCachedClient(context)),
            )
    }

    @After
    fun tearDown() {
        controllerScope.cancel()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun aDownloadedEpisodeShowsItsSavedCopyWhileTheNetworkStalls() = runTest(StandardTestDispatcher(scheduler)) {
        controller(
            scope = controllerScope,
            remoteChapters = {
                remoteReads++
                awaitCancellation()
            },
            remoteTranscript = {
                remoteReads++
                awaitCancellation()
            },
            saved = true,
        ).monitorChaptersAndTranscripts()
        advanceUntilIdle()

        state.value = state.value.copy(currentEpisode = episode())
        advanceUntilIdle()

        assertEquals(savedChapters, state.value.currentChapters)
        assertFalse(state.value.isChaptersLoading)
        assertEquals(savedTranscript, state.value.currentTranscript)
        assertEquals(0, remoteReads)
    }

    @Test
    fun withNothingSavedTheEpisodeReadsItsLinks() = runTest(StandardTestDispatcher(scheduler)) {
        controller(
            scope = controllerScope,
            remoteChapters = {
                remoteReads++
                remoteChapters
            },
            remoteTranscript = {
                remoteReads++
                remoteTranscript
            },
            saved = false,
        ).monitorChaptersAndTranscripts()
        advanceUntilIdle()

        state.value = state.value.copy(currentEpisode = episode())
        advanceUntilIdle()

        assertEquals(remoteChapters, state.value.currentChapters)
        assertFalse(state.value.isChaptersLoading)
        assertEquals(remoteTranscript, state.value.currentTranscript)
        assertEquals(2, remoteReads)
    }

    private fun controller(
        scope: CoroutineScope,
        remoteChapters: suspend (String) -> List<Chapter>,
        remoteTranscript: suspend (String) -> List<TranscriptSegment>,
        saved: Boolean,
    ) = PlaybackChaptersTranscriptController(
        context = context,
        scope = scope,
        playerState = state,
        playerStateFlow = state,
        podcastRepository = podcastRepository,
        sources = ChaptersTranscriptSources(
            chapters = remoteChapters,
            offlineChapters = { _, _ -> if (saved) savedChapters else emptyList() },
            transcript = remoteTranscript,
            offlineTranscript = { _, _ -> if (saved) savedTranscript else emptyList() },
        ),
    )

    private fun episode() = Episode(
        id = "e1",
        title = "Downloaded episode",
        description = "",
        audioUrl = "https://cdn.example/e1.mp3",
        chaptersUrl = "https://cdn.example/e1/chapters.json",
        transcriptUrl = "https://cdn.example/e1/transcript.srt",
    )
}
