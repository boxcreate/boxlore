package cx.aswin.boxcast.core.data.content

import cx.aswin.boxcast.core.data.ranking.CandidateSource
import cx.aswin.boxcast.core.data.ranking.RankingObjective
import cx.aswin.boxcast.core.data.ranking.RankingSurface
import cx.aswin.boxcast.core.model.Podcast
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentOrchestratorTest {
    @Test
    fun `invalid catalog resolves to generic anytime fallback`() {
        val context = context()
        val expired = ContentCatalogSnapshot(
            schemaVersion = 1,
            catalogVersion = "expired",
            validUntil = 0,
            intents = emptyList(),
        )

        val (version, intents) = ContentIntentResolver().resolve(expired, context, now = 1)

        assertEquals("embedded-anytime-v1", version)
        assertEquals(listOf("anytime"), intents.map(ContentIntent::id))
    }

    @Test
    fun `provider failures are isolated and session slate is stable`() = runTest {
        var successfulCalls = 0
        val failing = provider(CandidateSource.TRENDING) { error("offline") }
        val successful = provider(CandidateSource.SERVER_RECOMMENDATION) {
            successfulCalls++
            listOf(candidate("episode-a", "show-a", score = 0.8))
        }
        val orchestrator = ContentOrchestrator(
            providers = listOf(failing, successful),
            ranker = ContentCandidateRanker { candidates, _, _ ->
                candidates.sortedByDescending(ContentCandidate::rankingScore)
            },
        )

        val first = orchestrator.compose(context(), catalog())
        val second = orchestrator.compose(context(), catalog())

        assertEquals(listOf("episode-a"), first.sections.single().items.map(ContentCandidate::id))
        assertSame(first, second)
        assertEquals(1, successfulCalls)
    }

    @Test
    fun `slate keeps protected sections and deduplicates across optional sections`() {
        val protectedIntent = intent("protected", protected = true)
        val optionalIntent = intent("optional")
        val duplicate = candidate("duplicate", "show-a", score = 0.9)
        val unique = candidate("unique", "show-b", score = 0.8)

        val slate = SlateComposer().compose(
            context = context(),
            catalogVersion = "test",
            rankedByIntent = listOf(
                protectedIntent to listOf(duplicate),
                optionalIntent to listOf(duplicate, unique),
            ),
            exposureBudget = SharedExposureBudget(),
            now = 1,
        )

        assertTrue(slate.sections.first().intent.protected)
        assertEquals(
            listOf("duplicate", "unique"),
            slate.sections.flatMap(ContentSection::items).map(ContentCandidate::id),
        )
    }

    private fun catalog(): ContentCatalogSnapshot = ContentCatalogSnapshot(
        schemaVersion = 1,
        catalogVersion = "test",
        validUntil = Long.MAX_VALUE,
        intents = listOf(intent("discover")),
    )

    private fun intent(id: String, protected: Boolean = false): ContentIntent = ContentIntent(
        id = id,
        objective = RankingObjective.DISCOVERY,
        eligibleSurfaces = setOf(RankingSurface.HOME),
        title = id,
        layout = ContentLayout.PODCAST_RAIL,
        protected = protected,
    )

    private fun context(): ContentContext = ContentContext(
        surface = RankingSurface.HOME,
        localMinuteOfDay = 600,
        weekday = 3,
        daypart = ContentDaypart.MORNING,
        region = "us",
        isDriving = false,
        isOnline = true,
        availableMinutes = null,
        currentEpisodeId = null,
        currentPodcastId = null,
        historyMaturity = 10,
        subscriptionCount = 2,
        sessionId = "session",
    )

    private fun candidate(id: String, showId: String, score: Double): ContentCandidate {
        return ContentCandidate(
            id = id,
            episode = null,
            podcast = Podcast(showId, showId, "", ""),
            source = CandidateSource.SERVER_RECOMMENDATION,
            intentId = "discover",
            retrievalScore = score,
            rankingScore = score,
        )
    }

    private fun provider(
        source: CandidateSource,
        block: suspend () -> List<ContentCandidate>,
    ): CandidateProvider = object : CandidateProvider {
        override val source: CandidateSource = source

        override suspend fun candidates(
            intent: ContentIntent,
            context: ContentContext,
        ): List<ContentCandidate> = block()
    }
}
