package cx.aswin.boxlore.widgets

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.widgets.WidgetShowRow
import cx.aswin.boxlore.feature.widgets.logic.LibraryWidgetLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetLibrarySourceAdapterTest {
    @Test
    fun enrichMarksCompletedFromHistory() {
        val podcast =
            Podcast(
                id = "p1",
                title = "Show",
                artist = "Host",
                imageUrl = "https://example.com/a.jpg",
                latestEpisode =
                Episode(
                    id = "e1",
                    title = "Ep",
                    description = "",
                    audioUrl = "https://example.com/a.mp3",
                    publishedDate = 1_700_000_000L,
                ),
            )
        val history =
            listOf(
                ListeningHistoryEntity(
                    episodeId = "e1",
                    podcastId = "p1",
                    episodeTitle = "Ep",
                    episodeImageUrl = null,
                    podcastImageUrl = null,
                    episodeAudioUrl = null,
                    podcastName = "Show",
                    progressMs = 10L,
                    durationMs = 10L,
                    isCompleted = true,
                    lastPlayedAt = 1L,
                ),
            )
        val enriched = WidgetLibrarySourceAdapter.enrichWithHistory(listOf(podcast), history)
        assertEquals(EpisodeStatus.COMPLETED, enriched.single().episodeStatus)

        // Regression: finished outside recent history (after a Mark all of 300+ episodes), it read unplayed.
        val outsideRecent = WidgetLibrarySourceAdapter.enrichWithHistory(listOf(podcast), emptyList(), completedIds = setOf("e1"))
        assertEquals(EpisodeStatus.COMPLETED, outsideRecent.single().episodeStatus)
        assertTrue(WidgetLibrarySourceAdapter.latestEpisodePool(outsideRecent, hideCompleted = true).isEmpty())

        // Regression: started long ago, behind many rows marked played, it read unplayed.
        val started = history.single().copy(isCompleted = false, progressMs = 5L)
        val startedOutsideRecent = WidgetLibrarySourceAdapter.enrichWithHistory(listOf(podcast), emptyList(), inProgress = listOf(started)).single()
        assertEquals(EpisodeStatus.IN_PROGRESS, startedOutsideRecent.episodeStatus)
        assertEquals(0.5f, startedOutsideRecent.resumeProgress)
    }

    @Test
    fun startedEpisodesRedrawOnlyWhenAnEpisodeStartsOrStops() = runTest {
        fun row(id: String, progress: Long) = ListeningHistoryEntity(
            episodeId = id,
            podcastId = "p1",
            episodeTitle = id,
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = null,
            podcastName = "Show",
            progressMs = progress,
            durationMs = 100L,
            isCompleted = false,
            lastPlayedAt = 1L,
        )
        val saves = flowOf(listOf(row("a", 1L)), listOf(row("a", 2L)), listOf(row("a", 3L), row("b", 1L)))

        val emitted = WidgetLibrarySourceAdapter.startedEpisodes(saves).toList()

        assertEquals(listOf(listOf("a"), listOf("a", "b")), emitted.map { rows -> rows.map { it.episodeId } })
    }

    @Test
    fun deepLinksUseBoxloreScheme() {
        assertEquals(
            "boxlore://podcast/p%201",
            WidgetLibrarySourceAdapter.podcastDeepLink("p 1"),
        )
        assertEquals(
            "boxlore://library/subscriptions?tab=1",
            WidgetLibrarySourceAdapter.subscriptionsDeepLink(1),
        )
        val episode =
            WidgetLibrarySourceAdapter.episodeDeepLink(
                episodeId = "e1",
                podcastId = "p1",
                podcastTitle = "Cold War",
            )
        assertTrue(episode.startsWith("boxlore://episode/e1?"))
        assertTrue(episode.contains("autoplay=false"))
        assertTrue(episode.contains("podcastId=p1"))
    }

    @Test
    fun countsCoverTheWholeLibraryWhileTheRowsStayCapped() {
        val total = LibraryWidgetLogic.MAX_ROWS + 12
        val finished = 5
        val library =
            List(total) { index ->
                Podcast(
                    id = "p$index",
                    title = "Show $index",
                    artist = "Host",
                    imageUrl = "https://example.com/$index.jpg",
                    latestEpisode = Episode(id = "e$index", title = "Ep $index", description = "", audioUrl = "https://example.com/$index.mp3", publishedDate = 1_700_000_000L + index),
                    episodeStatus = if (index < finished) EpisodeStatus.COMPLETED else EpisodeStatus.UNPLAYED,
                )
            }

        val everyEpisode = WidgetLibrarySourceAdapter.latestEpisodePool(library, hideCompleted = false)
        val counts = WidgetLibrarySourceAdapter.libraryCounts(library, everyEpisode)
        assertEquals(total, counts.subscriptions)
        assertEquals(total, counts.newEpisodes)
        // The widget still scrolls a capped list.
        assertEquals(LibraryWidgetLogic.MAX_ROWS, LibraryWidgetLogic.truncateShows(library.map { WidgetShowRow(podcastId = it.id, title = it.title, deepLinkUri = "") }).size)

        // Hiding finished episodes shrinks the New Episodes count, not the show count.
        val unfinished = WidgetLibrarySourceAdapter.latestEpisodePool(library, hideCompleted = true)
        val hidden = WidgetLibrarySourceAdapter.libraryCounts(library, unfinished)
        assertEquals(total, hidden.subscriptions)
        assertEquals(total - finished, hidden.newEpisodes)

        // A show without a latest episode is a show, but not a New Episodes row.
        val withoutEpisode = library + Podcast(id = "bare", title = "Bare", artist = "Host", imageUrl = "")
        assertEquals(total, WidgetLibrarySourceAdapter.latestEpisodePool(withoutEpisode, hideCompleted = false).size)
    }

    @Test
    fun anArchivedLatestEpisodeLeavesNewEpisodesWhateverTheHideFinishedSetting() = runTest {
        val shows = listOf(show("p1", "e1"), show("p2", "e2"))
        assertEquals(listOf("p2"), WidgetLibrarySourceAdapter.latestEpisodePool(shows, hideCompleted = false, dismissedIds = setOf("e1")).map { it.id })
        assertEquals(listOf("p2"), WidgetLibrarySourceAdapter.latestEpisodePool(shows, hideCompleted = true, dismissedIds = setOf("e1")).map { it.id })

        val dismissed = MutableStateFlow(emptySet<String>())
        val adapterScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val adapter =
            WidgetLibrarySourceAdapter(
                podcasts = flowOf(shows),
                history = flowOf(emptyList()),
                sortName = flowOf("SmartRank"),
                dismissedEpisodeIds = dismissed,
                hideCompleted = flowOf(false),
                useSmart = flowOf(false),
                lastSeen = flowOf(emptyMap()),
                scoreShows = { scorable, _ -> scorable.associate { it.id to 1.0 } },
                scoreLatest = { inputs, _ -> inputs.associate { it.episode.id to 1.0 } },
                scope = adapterScope,
            )
        advanceUntilIdle()
        assertEquals(setOf("e1", "e2"), adapter.newEpisodes.first().map { it.episodeId }.toSet())

        dismissed.value = setOf("e2")
        advanceUntilIdle()
        assertEquals(listOf("e1"), adapter.newEpisodes.first().map { it.episodeId })
        // The show stays in the library; only its archived episode leaves New Episodes.
        assertEquals(2, adapter.counts.first().subscriptions)
        assertEquals(1, adapter.counts.first().newEpisodes)
        adapterScope.cancel()
    }

    @Test
    fun progressSavesDuringPlaybackNeverRankTheLibraryAgain() = runTest {
        val shows = listOf(show("p1", "e1"), show("p2", "e2"))
        val history = MutableStateFlow(listOf(played("e1", "p1", progressMs = 10_000L, lastPlayedAt = HOUR_START)))
        var showRuns = 0
        var latestRuns = 0
        // The adapter's long-lived scope, on the test clock: advanceUntilIdle skips backgroundScope.
        val adapterScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val adapter =
            WidgetLibrarySourceAdapter(
                podcasts = flowOf(shows),
                history = history,
                sortName = flowOf("SmartRank"),
                hideCompleted = flowOf(true),
                useSmart = flowOf(true),
                lastSeen = flowOf(emptyMap()),
                scoreShows = { scorable, _ ->
                    showRuns++
                    scorable.associate { it.id to if (it.id == "p1") 2.0 else 1.0 }
                },
                scoreLatest = { inputs, _ ->
                    latestRuns++
                    inputs.associate { it.episode.id to 1.0 }
                },
                scope = adapterScope,
            )
        advanceUntilIdle()
        assertEquals(1, showRuns)
        assertEquals(1, latestRuns)

        // Playback saves progress every 10 s: the rows the widget ranks from do not change.
        for (tick in 1..30) {
            history.value = listOf(played("e1", "p1", progressMs = 10_000L + tick * 10_000L, lastPlayedAt = HOUR_START + tick * 10_000L))
            advanceUntilIdle()
        }
        assertEquals(1, showRuns)
        assertEquals(1, latestRuns)
        assertEquals(listOf("e1", "e2"), adapter.newEpisodes.first().map { it.episodeId })

        // Finishing the episode is a real history change: it ranks again and hides the finished episode.
        history.value = listOf(played("e1", "p1", progressMs = 300_000L, lastPlayedAt = HOUR_START + 300_000L, isCompleted = true))
        advanceUntilIdle()
        assertEquals(2, showRuns)
        assertEquals(2, latestRuns)
        assertEquals(listOf("p1", "p2"), adapter.subscriptions.first().map { it.podcastId })
        assertEquals(listOf("e2"), adapter.newEpisodes.first().map { it.episodeId })
        adapterScope.cancel()
    }

    private fun show(id: String, episodeId: String) = Podcast(
        id = id,
        title = "Show $id",
        artist = "Host",
        imageUrl = "https://example.com/$id.jpg",
        latestEpisode = Episode(id = episodeId, title = "Ep $episodeId", description = "", audioUrl = "https://example.com/$episodeId.mp3", publishedDate = 1_700_000_000L),
    )

    private fun played(episodeId: String, podcastId: String, progressMs: Long, lastPlayedAt: Long, isCompleted: Boolean = false,) = ListeningHistoryEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        episodeTitle = "Ep",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "Show",
        progressMs = progressMs,
        durationMs = 3_000_000L,
        isCompleted = isCompleted,
        lastPlayedAt = lastPlayedAt,
    )

    private companion object {
        // On the hour, so the 30 ticks above stay inside one hour.
        const val HOUR_START = 1_700_002_800_000L
    }
}
