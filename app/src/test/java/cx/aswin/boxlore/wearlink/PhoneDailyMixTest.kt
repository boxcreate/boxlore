package cx.aswin.boxlore.wearlink

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: the watch's Daily Mix read the newest 200 rows, which one Mark all as played fills,
 * so a started episode dropped out and every show lost its listening.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PhoneDailyMixTest {
    private lateinit var database: BoxLoreDatabase
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aStartedEpisodeStaysAResumePickBehindAShowMarkedPlayedAllAtOnce() = runBlocking {
        val started = row("x", "a", progressMs = 40 * MINUTE, playedAt = now - HOUR)
        val marked = (0 until 250).map { row("bulk-$it", "back", progressMs = 0L, playedAt = now - MINUTE + it, completed = true).copy(isBulkCompletion = true) }
        database.listeningHistoryDao().upsertAll(listOf(started) + marked)
        val shows = listOf(show("a", latest = episode("x", "a")), show("back", latest = episode("bulk-0", "back")))

        val mix = PhoneListRules.dailyMix(shows, database.listeningHistoryDao(), completedIds = marked.mapTo(HashSet()) { it.episodeId })

        assertEquals(listOf("x"), mix.episodes.map { it.id })
        assertEquals(EpisodeStatus.IN_PROGRESS, mix.podcasts.single().episodeStatus)
        assertFalse(mix.episodes.any { it.id.startsWith("bulk") })
        assertTrue(mix.podcasts.single().resumeProgress!! > 0.6f)
    }

    private fun episode(id: String, show: String) = Episode(
        id = id,
        title = id,
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        podcastId = show,
        duration = (HOUR / 1_000L).toInt(),
        publishedDate = (now - DAY) / 1_000L,
    )

    private fun show(id: String, latest: Episode) = Podcast(id = id, title = "Show $id", artist = "", imageUrl = "", latestEpisode = latest)

    private fun row(id: String, show: String, progressMs: Long, playedAt: Long, completed: Boolean = false,) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = id,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Show $show",
        progressMs = progressMs,
        durationMs = HOUR,
        isCompleted = completed,
        lastPlayedAt = playedAt,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
