package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Pure coverage for [buildRecommendationSeeds] in `PodcastRepositoryRecommendations.kt`. */
class PodcastRecommendationSeedsTest {
    @Test
    fun seedIdsReplaceStoredIdsAndPlaceNegativeIds() {
        val recipe = 4_503_599_627_370_497L
        val history =
            listOf(
                TestFixtures.historyItem(episodeId = "7777", isLiked = true),
                TestFixtures.historyItem(episodeId = "-55", isCompleted = true),
                TestFixtures.historyItem(episodeId = "-66"),
                TestFixtures.historyItem(episodeId = "5555"),
            )

        val seeds = buildRecommendationSeeds(history, emptyList(), seedIds = mapOf("7777" to recipe, "-55" to recipe + 1))

        assertEquals(listOf(recipe, recipe + 1, 5555L), seeds.map { it.id })
    }

    @Test
    fun v2HistoryKeepsPlacedIdsAndPodcastIndexIdsOnly() {
        val history =
            listOf(
                TestFixtures.historyItem(episodeId = "7777", podcastId = "920666"),
                TestFixtures.historyItem(episodeId = "-55", podcastId = "920666"),
                TestFixtures.historyItem(episodeId = "-66", podcastId = "920666"),
                TestFixtures.historyItem(episodeId = "-77", podcastId = "rss:abc"),
                TestFixtures.historyItem(episodeId = "-88", podcastId = "rss:abc"),
                TestFixtures.historyItem(episodeId = null, podcastId = "920666"),
            )

        val usable = v2RecommendationHistory(history, recipeIds = mapOf("-55" to 1L, "-77" to 2L))

        assertEquals(listOf("7777", "-55", "-77"), usable.map { it.episodeId })
    }

    @Test
    fun likedEpisodesWeightedHighestAndSortedDescending() {
        val history =
            listOf(
                TestFixtures.historyItem(episodeId = "10", isLiked = false, isCompleted = false),
                TestFixtures.historyItem(episodeId = "11", isLiked = true, isCompleted = false),
            )

        val seeds = buildRecommendationSeeds(history, subscribedPodcastIds = emptyList())

        assertEquals(2, seeds.size)
        assertEquals(11L, seeds.first().id)
        assertEquals(1.0, seeds.first().weight)
        assertTrue(seeds.all { it.kind == "episode" })
    }

    @Test
    fun progressRatioDrivesWeightBuckets() {
        val completed =
            TestFixtures.historyItem(
                episodeId = "1",
                isLiked = false,
                isCompleted = true,
            )
        val halfway =
            TestFixtures.historyItem(
                episodeId = "2",
                isLiked = false,
                isCompleted = false,
                durationMs = 100L,
                progressMs = 60L,
            )
        val barelyStarted =
            TestFixtures.historyItem(
                episodeId = "3",
                isLiked = false,
                isCompleted = false,
                durationMs = 100L,
                progressMs = 5L,
            )

        val seeds =
            buildRecommendationSeeds(
                listOf(completed, halfway, barelyStarted),
                subscribedPodcastIds = emptyList(),
            ).associateBy { it.id }

        assertEquals(0.9, seeds.getValue(1L).weight)
        assertEquals(0.75, seeds.getValue(2L).weight)
        assertEquals(0.35, seeds.getValue(3L).weight)
    }

    @Test
    fun podcastSeedsBackfillWhenEpisodeSeedsBelowMax() {
        val history = listOf(TestFixtures.historyItem(episodeId = "1"))

        val seeds =
            buildRecommendationSeeds(
                history,
                subscribedPodcastIds = listOf("100", "200", "invalid", "-1"),
            )

        val podcastSeeds = seeds.filter { it.kind == "podcast" }
        assertEquals(listOf(100L, 200L), podcastSeeds.map { it.id })
        assertTrue(podcastSeeds.all { it.weight == 0.35 })
    }

    @Test
    fun episodeSeedsAreBoundedByMaximum() {
        val history = (1..20).map { TestFixtures.historyItem(episodeId = it.toString(), isLiked = true) }

        val seeds = buildRecommendationSeeds(history, subscribedPodcastIds = listOf("999"), maximumSeeds = 5)

        assertEquals(5, seeds.size)
        assertTrue(seeds.all { it.kind == "episode" })
    }

    @Test
    fun invalidEpisodeIdsAreSkipped() {
        val history =
            listOf(
                TestFixtures.historyItem(episodeId = null),
                TestFixtures.historyItem(episodeId = "0"),
                TestFixtures.historyItem(episodeId = "abc"),
            )

        val seeds = buildRecommendationSeeds(history, subscribedPodcastIds = emptyList())

        assertTrue(seeds.isEmpty())
    }

    @Test
    fun withoutHistoryFollowSeedsComeFromTheNewestFollowsNotTheFirstByTitle() {
        // 30 follows passed in title order (as Room lists them); the later ones were followed on later days.
        val follows = (1..30).map { (100 + it).toString() }
        val followedAt = follows.withIndex().associate { (index, id) -> id to index * DAY + 1_000L }

        val order = recommendationFollowOrder(follows, emptyList(), followedAt, nowMillis = 40 * DAY)
        val seeds = buildRecommendationSeeds(emptyList(), order)

        assertEquals((130L downTo 119L).toList(), seeds.map { it.id })
    }

    @Test
    fun followsPlayedLatelyComeFirstByTheirLatestPlay() {
        val follows = listOf("1", "2", "3", "4")
        val history =
            listOf(
                TestFixtures.historyItem(podcastId = "3", episodeId = "30"),
                TestFixtures.historyItem(podcastId = "9", episodeId = "90"),
                TestFixtures.historyItem(podcastId = "1", episodeId = "10"),
                TestFixtures.historyItem(podcastId = "3", episodeId = "31"),
            )

        val order = recommendationFollowOrder(follows + "3", history, followedAt = mapOf("2" to 5L, "4" to DAY + 9L), nowMillis = 2 * DAY)

        assertEquals(listOf("3", "1", "4", "2"), order)
    }

    @Test
    fun followsFollowedTogetherTakeTurnsDayByDay() {
        // An OPML import: every show followed at the same moment.
        val follows = (1..30).map { "%03d".format(it) }
        val followedAt = follows.associateWith { 500L }

        val today = recommendationFollowOrder(follows, emptyList(), followedAt, nowMillis = 10 * DAY)
        val laterToday = recommendationFollowOrder(follows, emptyList(), followedAt, nowMillis = 10 * DAY + DAY - 1)
        val days = (10L..16L).map { recommendationFollowOrder(follows, emptyList(), followedAt, nowMillis = it * DAY).take(12).toSet() }

        assertEquals(today, laterToday, "the same order all day")
        assertEquals(follows.toSet(), today.toSet())
        assertTrue(today.take(12) != follows.take(12), "not the first 12 by title")
        assertTrue(days.toSet().size > 1, "another day, other shows")
        assertEquals(follows.toSet(), days.flatten().toSet(), "over a week every show seeds")
    }

    @Test
    fun anImportFollowedMomentsApartStillTakesTurnsDayByDay() {
        // A 300-show OPML import in title order: each show followed a few hundred ms after the last.
        val follows = (1..300).map { "%03d".format(it) }
        val importStart = 5 * DAY + 1_000L
        val followedAt = follows.withIndex().associate { (index, id) -> id to importStart + index * 350L }
        val laterFollow = "later"
        val earlierFollow = "earlier"
        val all = follows + laterFollow + earlierFollow
        val stamps = followedAt + (laterFollow to 6 * DAY + 5L) + (earlierFollow to 4 * DAY)

        val orders = (10L..16L).map { recommendationFollowOrder(all, emptyList(), stamps, nowMillis = it * DAY) }

        orders.forEach { order ->
            assertEquals(laterFollow, order.first(), "a show followed on a later day comes before the import")
            assertEquals(earlierFollow, order.last(), "a show followed on an earlier day comes after it")
        }
        val seeds = orders.map { it.drop(1).take(12) }
        val cut = orders.map { it.drop(MAX_RECOMMENDATION_IDS).toSet() }
        assertTrue(seeds.toSet().size > 1, "the seeds change from day to day")
        assertTrue(seeds.first() != follows.takeLast(12).reversed(), "not the last 12 by title")
        assertTrue(cut.toSet().size > 1, "other shows are left out of the request each day")
    }

    @Test
    fun requiresPositiveMaximum() {
        assertThrows(IllegalArgumentException::class.java) {
            buildRecommendationSeeds(emptyList(), emptyList(), maximumSeeds = 0)
        }
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}
