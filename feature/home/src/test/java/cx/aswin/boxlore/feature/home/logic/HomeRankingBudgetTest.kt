package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeDiscoveryRankingCache
import cx.aswin.boxlore.feature.home.HomeDiscoveryRankingInput
import cx.aswin.boxlore.feature.home.HomeDiscoveryRankingResult
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Home's ranking budget. Scoring shows, building Daily Mix and ranking discovery are the expensive
 * Home steps, and running them on every playback tick made Home stutter. The driver runs the same
 * plan, rank, assemble and commit sequence as the Home pipeline and counts every expensive call
 * through playback, imports and cloud syncs.
 */
class HomeRankingBudgetTest {
    private class HomePipelineDriver {
        val ranker = HomeYourShowsRanker()
        private val discovery = HomeDiscoveryRankingCache()
        var generation = 0L
        var scoreRuns = 0
        var mixBuilds = 0
        var discoveryRuns = 0
        val expensiveRuns get() = scoreRuns + mixBuilds + discoveryRuns

        suspend fun pass(
            subs: List<Podcast>,
            history: List<HomeListeningHistoryItem>,
            staleRestartEnabled: Boolean = true,
            nowMs: Long = 0L,
        ): HomeUiAssemblyResult {
            val plan = ranker.plan(subs.map { it.id }.toSet(), history, staleRestartEnabled, generation)
            discovery.get(HomeDiscoveryRankingInput(emptyList(), emptyList(), plan.discoveryPass)) {
                discoveryRuns++
                HomeDiscoveryRankingResult(it.trending, it.recommendations)
            }
            val scores = if (plan.needsScores) {
                scoreRuns++
                subs.associate { it.id to it.title.length.toDouble() }
            } else {
                emptyMap()
            }
            val result = HomeUiAssemblyLogic.assemble(
                trendingList = emptyList(),
                rankedRecommendations = emptyList(),
                resumeList = emptyList(),
                subs = subs,
                allHistory = history,
                resolvedSerial = emptyMap(),
                completedEpisodeIds = history.filter { it.isCompleted }.map { it.episodeId }.toSet(),
                region = "us",
                editorialRows = emptyList(),
                previousStableOrder = plan.previousOrder,
                podcastScores = scores,
                previousMixtape = plan.previousMixtape,
                buildMixtape = { _, _ ->
                    mixBuilds++
                    val withEpisodes = subs.filter { it.latestEpisode != null }
                    MixtapeEngine.Result(withEpisodes, withEpisodes.mapNotNull { it.latestEpisode }, withEpisodes.size)
                },
                isTrendingLoaded = true,
                hasDismissedImportBanner = true,
                rawBriefing = null,
                rawBriefingChapters = emptyList(),
                briefingDismissedDate = "",
                briefingDismissedForever = false,
                staleRestartEnabled = staleRestartEnabled,
                nowMs = nowMs,
                refreshShowsOrderFromScores = plan.refreshOrder,
            )
            ranker.commit(plan, result.stablePodcastOrder, result.mixtapeCache, nowMs)
            return result
        }

        fun homeStarted(nowMs: Long) {
            if (ranker.shouldRequestRefresh(generation, nowMs)) generation++
        }

        /** What a settled catalog change does in Home: request one re-rank. */
        fun settledCatalogChange() {
            generation++
        }
    }

    private fun show(index: Int, withEpisode: Boolean = true) = TestFixtures.podcast(id = "show-$index", title = "Show ${"x".repeat(index % 7)}$index")
        .copy(latestEpisode = if (withEpisode) TestFixtures.episode(id = "ep-$index", podcastId = "show-$index") else null)

    private fun listened(episode: String, progressMs: Long, playedAt: Long, completed: Boolean = false) =
        HomeListeningHistoryItem(episode, "show-0", "Show", null, progressMs, 3_600_000, completed, false, playedAt)

    @Test
    fun `an hour of playback progress never ranks anything again`() = runTest {
        val home = HomePipelineDriver()
        val subs = List(12) { show(it) }
        home.pass(subs, listOf(listened("ep-0", 0, 0)))
        val firstPass = home.expensiveRuns
        assertEquals(3, firstPass, "first pass scores, builds Daily Mix and ranks discovery once each")

        // A progress save every second for an hour, with Home on screen the whole time.
        repeat(3_600) { second ->
            home.pass(subs, listOf(listened("ep-0", second * 1_000L, second * 1_000L)), nowMs = second * 1_000L)
        }

        assertEquals(firstPass, home.expensiveRuns)
    }

    @Test
    fun `returning to Home re-ranks once after listening changes and never on progress alone`() = runTest {
        val home = HomePipelineDriver()
        val subs = List(8) { show(it) }
        home.pass(subs, listOf(listened("ep-0", 10_000, 1)))
        repeat(50) { home.homeStarted(nowMs = 60_000L + it) }
        home.pass(subs, listOf(listened("ep-0", 90_000, 2)), nowMs = 61_000)
        assertEquals(3, home.expensiveRuns, "progress alone never requests a re-rank")

        val started = listOf(listened("ep-0", 90_000, 2), listened("ep-1", 1_000, 3))
        home.pass(subs, started, nowMs = 62_000)
        assertEquals(3, home.expensiveRuns, "listening changes wait for a return to Home")
        repeat(20) { home.homeStarted(nowMs = 63_000L + it) }
        home.pass(subs, started, nowMs = 64_000)
        assertEquals(6, home.expensiveRuns, "one return re-ranks shows, Daily Mix and discovery once")

        repeat(20) { home.homeStarted(nowMs = 65_000L + it) }
        home.pass(subs, started, nowMs = 66_000)
        assertEquals(6, home.expensiveRuns)
    }

    @Test
    fun `a 300 show import updates the covers at once and ranks once after it settles`() = runTest {
        val home = HomePipelineDriver()
        val subs = mutableListOf(show(0), show(1))
        home.pass(subs, emptyList())
        val firstPass = home.expensiveRuns

        for (index in 2 until 302) {
            subs += show(index)
            val result = home.pass(subs.toList(), emptyList())
            assertEquals("show-$index", result.subscribedPodcasts.first().id, "new shows join the covers at once")
        }
        assertEquals(firstPass, home.expensiveRuns, "an import never ranks per written row")

        home.settledCatalogChange()
        val settled = home.pass(subs.toList(), emptyList())
        assertEquals(firstPass + 3, home.expensiveRuns)
        assertEquals(302, settled.latestEpisodes.size, "the settled pass adds the imported shows to Daily Mix")
    }

    @Test
    fun `a cloud sync restoring history ranks nothing until the listener returns to Home`() = runTest {
        val home = HomePipelineDriver()
        val subs = List(20) { show(it) }
        home.pass(subs, emptyList())
        val firstPass = home.expensiveRuns
        val restored = mutableListOf<HomeListeningHistoryItem>()
        repeat(500) { row ->
            restored += listened("restored-$row", 60_000, row.toLong(), completed = row % 3 == 0)
            home.pass(subs, restored.toList(), nowMs = row.toLong())
        }
        assertEquals(firstPass, home.expensiveRuns)

        home.homeStarted(nowMs = 1_000)
        home.homeStarted(nowMs = 1_001)
        home.pass(subs, restored.toList(), nowMs = 1_002)
        assertEquals(firstPass + 3, home.expensiveRuns)
    }

    @Test
    fun `an unsubscribed show leaves the covers and Daily Mix without a re-rank`() = runTest {
        val home = HomePipelineDriver()
        val subs = List(5) { show(it) }
        home.pass(subs, emptyList())
        val runs = home.expensiveRuns

        val result = home.pass(subs.drop(1), emptyList())

        assertEquals(runs, home.expensiveRuns)
        assertFalse(result.subscribedPodcasts.any { it.id == "show-0" })
        assertFalse(result.latestEpisodes.any { it.id == "show-0" })
        assertEquals(4, result.unplayedEpisodeCount)
    }

    @Test
    fun `episodes arriving after an empty first launch fill Daily Mix on the settled pass`() = runTest {
        val home = HomePipelineDriver()
        val beforeSync = List(6) { show(it, withEpisode = false) }
        assertTrue(home.pass(beforeSync, emptyList()).latestEpisodes.isEmpty())

        val synced = List(6) { show(it) }
        assertTrue(home.pass(synced, emptyList()).latestEpisodes.isEmpty(), "a sync write alone does not rebuild")
        home.settledCatalogChange()

        assertEquals(6, home.pass(synced, emptyList()).latestEpisodes.size)
    }

    @Test
    fun `the 30 minute refresh requests one pass and waits while it is pending`() = runTest {
        val home = HomePipelineDriver()
        val subs = List(4) { show(it) }
        home.pass(subs, emptyList(), nowMs = 0)
        home.homeStarted(nowMs = HomeShowsRefreshPolicy.MIN_SNAPSHOT_AGE_MS - 1)
        assertEquals(0L, home.generation)
        repeat(5) { home.homeStarted(nowMs = HomeShowsRefreshPolicy.MIN_SNAPSHOT_AGE_MS) }
        assertEquals(1L, home.generation, "a pending request is not requested again")
    }

    @Test
    fun `playback ticks never change the catalog or request a re-rank`() = runTest {
        val subs = List(10) { show(it) }
        val base = HomeYourShowsSignals.catalog(subs, emptyMap())
        val ticking = subs.map { it.copy(resumeProgress = 0.7f, episodeStatus = EpisodeStatus.IN_PROGRESS) }
        assertEquals(base, HomeYourShowsSignals.catalog(ticking, emptyMap()))

        val requests = mutableListOf<Long>()
        flow {
            emit(base)
            repeat(3_600) {
                delay(1_000)
                emit(HomeYourShowsSignals.catalog(ticking, emptyMap()))
            }
        }.settledCatalogChanges().collect { requests += testScheduler.currentTime }

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an import burst requests one re-rank after it settles`() = runTest {
        val requests = mutableListOf<Long>()
        flow {
            emit(HomeYourShowsSignals.catalog(listOf(show(0)), emptyMap()))
            for (count in 2..300) {
                delay(50)
                emit(HomeYourShowsSignals.catalog(List(count) { show(it) }, emptyMap()))
            }
            delay(60_000)
        }.settledCatalogChanges().collect { requests += testScheduler.currentTime }

        val lastWrite = 299 * 50L
        assertEquals(listOf(lastWrite + HomeShowsRefreshPolicy.CATALOG_SETTLE_MS), requests)
    }

    @Test
    fun `a trickling sync is capped and its last change still gets a re-rank`() = runTest {
        val requests = mutableListOf<Long>()
        val writes = 30
        flow {
            emit(HomeYourShowsSignals.catalog(listOf(show(0)), emptyMap()))
            for (write in 1..writes) {
                delay(2_000)
                emit(HomeYourShowsSignals.catalog(List(write + 1) { show(it) }, emptyMap()))
            }
            delay(60_000)
        }.settledCatalogChanges().collect { requests += testScheduler.currentTime }

        val lastWrite = writes * 2_000L
        val cap = (lastWrite + HomeShowsRefreshPolicy.CATALOG_SETTLE_MS) / HomeShowsRefreshPolicy.CATALOG_MIN_GAP_MS + 1
        assertTrue(requests.size <= cap, "${requests.size} re-ranks for one sync; cap is $cap")
        requests.zipWithNext { a, b -> assertTrue(b - a >= HomeShowsRefreshPolicy.CATALOG_MIN_GAP_MS) }
        assertTrue(requests.last() >= lastWrite + HomeShowsRefreshPolicy.CATALOG_SETTLE_MS)
    }

    @Test
    fun `a late serial resolution or new episode requests exactly one re-rank`() = runTest {
        val subs = List(5) { show(it) }
        val serial = TestFixtures.episode(id = "serial-next", podcastId = "show-2")
        val newEpisode = subs.mapIndexed { index, show ->
            if (index == 4) show.copy(latestEpisode = TestFixtures.episode(id = "fresh", podcastId = "show-4")) else show
        }
        val requests = mutableListOf<Long>()
        flow {
            emit(HomeYourShowsSignals.catalog(subs, emptyMap()))
            delay(10_000)
            emit(HomeYourShowsSignals.catalog(subs, mapOf("show-2" to serial)))
            delay(10_000)
            emit(HomeYourShowsSignals.catalog(newEpisode, mapOf("show-2" to serial)))
            delay(10_000)
        }.settledCatalogChanges().collect { requests += testScheduler.currentTime }

        assertEquals(2, requests.size)
    }

    @Test
    fun `listening signature ignores progress and play times but sees starts and completions`() {
        val base = listOf(listened("a", 1_000, 1), listened("b", 0, 2))
        val ticked = listOf(listened("a", 900_000, 99), listened("b", 5_000, 100))
        assertEquals(HomeYourShowsSignals.listening(base), HomeYourShowsSignals.listening(ticked))
        assertTrue(HomeYourShowsSignals.listening(base) != HomeYourShowsSignals.listening(base + listened("c", 1, 3)))
        assertTrue(
            HomeYourShowsSignals.listening(base) !=
                HomeYourShowsSignals.listening(listOf(listened("a", 3_600_000, 4, completed = true), listened("b", 0, 2))),
        )
    }
}
