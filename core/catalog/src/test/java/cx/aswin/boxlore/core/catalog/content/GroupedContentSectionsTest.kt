package cx.aswin.boxlore.core.catalog.content

import cx.aswin.boxlore.core.catalog.toContentCatalogSnapshot
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.ContentCatalogResponse
import cx.aswin.boxlore.core.network.model.ContentDiversityDto
import cx.aswin.boxlore.core.network.model.ContentDurationRangeDto
import cx.aswin.boxlore.core.network.model.ContentIntentDto
import cx.aswin.boxlore.core.network.model.ContentQualityDto
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class GroupedContentSectionsTest {
    @Test
    fun `catalog mapping retains v3 selection constraints`() {
        val snapshot =
            ContentCatalogResponse(
                schemaVersion = 1,
                catalogVersion = 3,
                validForSeconds = 86_400,
                intents =
                listOf(
                    ContentIntentDto(
                        id = "focused",
                        titleKey = "focused_title",
                        titleFallback = "Focused",
                        subtitleKey = "focused_subtitle",
                        subtitleFallback = "Learn something",
                        icon = "neurology",
                        surfaces = listOf("home"),
                        dayparts = listOf("afternoon"),
                        providerQueryRef = "creative_focus",
                        layout = "episode_rail",
                        minCandidates = 4,
                        maxCandidates = 12,
                        freshnessDays = 30,
                        durationMinutes = ContentDurationRangeDto(12, 60),
                        diversity = ContentDiversityDto(2, 4),
                        quality = ContentQualityDto(0.23, 0.4),
                    ),
                ),
            ).toContentCatalogSnapshot(fetchedAt = 1_000L)

        val intent = snapshot.intents.single()
        assertEquals(30, intent.freshnessDays)
        assertEquals(ContentDurationRange(12, 60), intent.durationRange)
        assertEquals(ContentDiversityConstraints(2, 4), intent.diversity)
        assertEquals(ContentQualityConstraints(0.23, 0.4), intent.quality)
        assertEquals(listOf("afternoon"), intent.daypartIds)
    }

    @Test
    fun `grouped provider is preferred and keeps fallback providers idle`() = runTest {
        var groupedCalls = 0
        var fallbackCalls = 0
        val grouped =
            GroupedContentSections(
                contractVersion = 1,
                catalogVersion = "3",
                resolvedDaypart = "commute",
                algorithmVersion = "test",
                isFallback = false,
                generatedAt = null,
                sections =
                listOf(
                    GroupedContentSection(
                        intent = intent("first"),
                        items = listOf(candidate("1", "show-1")),
                    ),
                ),
            )
        val orchestrator =
            ContentOrchestrator(
                providers =
                listOf(
                    object : CandidateProvider {
                        override val source = CandidateSource.CURATED_INTENT

                        override suspend fun candidates(intent: ContentIntent, context: ContentContext,): List<ContentCandidate> {
                            fallbackCalls++
                            return listOf(candidate("fallback", "fallback-show"))
                        }
                    },
                ),
                groupedProviders =
                listOf(
                    ServerGroupedSectionProvider {
                        groupedCalls++
                        grouped
                    },
                ),
                ranker = ContentCandidateRanker { candidates, _, _ -> candidates },
            )

        val slate =
            orchestrator.compose(
                context = context(),
                catalog = catalog(),
                now = 1_700_000_000_000L,
            )

        assertEquals(listOf("first"), slate.sections.map { it.intent.id })
        assertEquals(
            listOf("1"),
            slate.sections
                .single()
                .items
                .map(ContentCandidate::id),
        )
        assertEquals(1, groupedCalls)
        assertEquals(0, fallbackCalls)
    }

    @Test
    fun `composer enforces catalog quality freshness duration diversity and unseen reserve`() {
        val now = 1_800_000_000_000L
        val constrainedIntent =
            intent("constrained").copy(
                minimumItems = 2,
                maximumItems = 3,
                freshnessDays = 10,
                durationRange = ContentDurationRange(10, 30),
                diversity =
                ContentDiversityConstraints(
                    maximumItemsPerShow = 1,
                    minimumDistinctShows = 2,
                ),
                quality =
                ContentQualityConstraints(
                    minimumSemanticScore = 0.5,
                    unseenShowReserve = 0.34,
                ),
            )
        val currentSeconds = now / 1_000L
        val seen = episodeCandidate("seen", "show-a", currentSeconds, semantic = 0.9)
        val sameShow = episodeCandidate("same-show", "show-a", currentSeconds, semantic = 0.9)
        val unseen =
            episodeCandidate(
                "unseen",
                "show-b",
                currentSeconds,
                semantic = 0.8,
                novel = true,
            )
        val stale =
            episodeCandidate(
                "stale",
                "show-c",
                currentSeconds - 11L * 86_400L,
                semantic = 0.9,
            )
        val lowQuality = episodeCandidate("low", "show-d", currentSeconds, semantic = 0.4)
        val tooShort =
            episodeCandidate(
                "short",
                "show-e",
                currentSeconds,
                semantic = 0.9,
                durationSeconds = 300,
            )

        val slate =
            SlateComposer().compose(
                context = context(),
                catalogVersion = "3",
                rankedByIntent =
                listOf(
                    constrainedIntent to listOf(seen, sameShow, unseen, stale, lowQuality, tooShort),
                ),
                exposureBudget = SharedExposureBudget(),
                now = now,
            )

        assertEquals(
            listOf("seen", "unseen"),
            slate.sections
                .single()
                .items
                .map(ContentCandidate::id),
        )
        assertFalse(
            slate.sections
                .single()
                .items
                .any { it.id in setOf("stale", "low", "short") },
        )
        assertEquals(
            2,
            slate.sections
                .single()
                .items
                .map { it.podcast.id }
                .distinct()
                .size,
        )
    }

    private fun catalog(): ContentCatalogSnapshot = ContentCatalogSnapshot(
        schemaVersion = 1,
        catalogVersion = "3",
        validUntil = Long.MAX_VALUE,
        intents =
        listOf(
            intent("first").copy(
                freshnessDays = 14,
                diversity = ContentDiversityConstraints(1, 1),
                quality = ContentQualityConstraints(0.6, 0.25),
            ),
            intent("second"),
        ),
    )

    private fun intent(id: String): ContentIntent = ContentIntent(
        id = id,
        objective = RankingObjective.DISCOVERY,
        eligibleSurfaces = setOf(RankingSurface.HOME),
        eligibleDayparts = setOf(ContentDaypart.MORNING),
        title = "Catalog $id",
        layout = ContentLayout.EPISODE_RAIL,
        minimumItems = 1,
        maximumItems = 10,
    )

    private fun context(): ContentContext = ContentContext(
        surface = RankingSurface.HOME,
        localMinuteOfDay = 500,
        weekday = 5,
        daypart = ContentDaypart.MORNING,
        region = "us",
        isDriving = false,
        isOnline = true,
        availableMinutes = null,
        currentEpisodeId = null,
        currentPodcastId = null,
        historyMaturity = 4,
        subscriptionCount = 1,
        sessionId = "grouped-test",
    )

    private fun candidate(id: String, showId: String,): ContentCandidate = ContentCandidate(
        id = id,
        episode = null,
        podcast = Podcast(showId, showId, "", ""),
        source = CandidateSource.CURATED_INTENT,
        intentId = "first",
        retrievalScore = 1.0,
    )

    private fun episodeCandidate(
        id: String,
        showId: String,
        publishedAtSeconds: Long,
        semantic: Double,
        novel: Boolean = false,
        durationSeconds: Int = 1_200,
    ): ContentCandidate {
        val episode =
            Episode(
                id = id,
                title = id,
                description = "",
                audioUrl = "https://example.com/$id.mp3",
                podcastTitle = showId,
                podcastId = showId,
                duration = durationSeconds,
                publishedDate = publishedAtSeconds,
                retrievalScore = 0.8,
                semanticScore = semantic,
            )
        return ContentCandidate(
            id = id,
            episode = episode,
            podcast = Podcast(showId, showId, "", "", latestEpisode = episode),
            source = CandidateSource.SERVER_RECOMMENDATION,
            intentId = "constrained",
            retrievalScore = 0.8,
            isNovel = novel,
            semanticScore = semantic,
        )
    }
}
