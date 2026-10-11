package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackSession
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeHeroLogicTest {
    @Test
    fun `session to podcast uses episode art and resume ratio`() {
        val session =
            PlaybackSession(
                podcastId = "pod-1",
                episodeId = "ep-1",
                positionMs = 60_000L,
                durationMs = 120_000L,
                timestamp = 1L,
                episodeTitle = "Ep",
                podcastTitle = "Show",
                imageUrl = "https://example.com/ep.jpg",
                podcastImageUrl = "https://example.com/pod.jpg",
                audioUrl = "https://example.com/a.mp3",
            )

        val podcast = HomeHeroLogic.sessionToPodcast(session, subs = emptyList())

        assertEquals("pod-1", podcast.id)
        assertEquals("Show", podcast.title)
        assertEquals("https://example.com/ep.jpg", podcast.imageUrl)
        assertEquals(0.5f, podcast.resumeProgress)
        assertEquals("ep-1", podcast.latestEpisode?.id)
    }

    @Test
    fun `unplayed hero label prefers NEXT for oldest sort`() {
        assertEquals("NEXT", HomeHeroLogic.unplayedHeroLabel("oldest", unplayedCount = 3, isFirst = true))
        assertEquals("NEW EPISODE", HomeHeroLogic.unplayedHeroLabel("newest", unplayedCount = 1, isFirst = true))
        assertEquals("FRESH DROP", HomeHeroLogic.unplayedHeroLabel("newest", unplayedCount = 3, isFirst = true))
    }

    @Test
    fun `build hero items adds resume then unplayed then spotlight`() {
        val sub = TestFixtures.podcast(id = "sub-1", title = "Sub")
        val resume =
            listOf(
                PlaybackSession(
                    podcastId = "sub-1",
                    episodeId = "ep-r",
                    positionMs = 30_000L,
                    durationMs = 120_000L,
                    timestamp = 1L,
                    episodeTitle = "Resume Ep",
                    podcastTitle = "Sub",
                    imageUrl = null,
                    podcastImageUrl = "https://example.com/pod.jpg",
                    audioUrl = null,
                ),
            )
        val unplayed = listOf(sub.copy(latestEpisode = TestFixtures.episode(id = "ep-new")))
        val trending =
            listOf(
                TestFixtures.podcast(id = "trend-1", title = "Trend"),
                TestFixtures.podcast(id = "trend-2", title = "Trend2"),
            )

        val hero =
            HomeHeroLogic.buildHeroItems(
                resumeList = resume,
                unplayedBucket = unplayed,
                trendingList = trending,
                subs = listOf(sub),
                region = "us",
            )

        assertEquals(HeroType.RESUME, hero.first().type)
        assertTrue(hero.any { it.type == HeroType.JUMP_BACK_IN })
        assertTrue(hero.any { it.type == HeroType.SPOTLIGHT })
        assertEquals("#1 IN US", hero.first { it.type == HeroType.SPOTLIGHT }.label)
    }

    @Test
    fun `newest episode becomes solo hero before grid limit is applied`() {
        val bucket =
            listOf(
                unplayedShow("a", "Alpha", 100L),
                unplayedShow("b", "Bravo", 400L),
                unplayedShow("c", "Charlie", 200L),
                unplayedShow("d", "Delta", 500L),
                unplayedShow("e", "Echo", 300L),
                unplayedShow("z", "Zulu", 600L),
            )
        val hero = mutableListOf<SmartHeroItem>()
        val used = mutableSetOf<String>()

        HomeHeroLogic.appendUnplayedHeroItems(hero, used, bucket)

        assertEquals(2, hero.size)
        assertEquals(HeroType.JUMP_BACK_IN, hero.first().type)
        assertEquals("z", hero.first().podcast.id)
        assertEquals("FRESH DROP", hero.first().label)
        assertEquals(HeroType.NEW_EPISODES_GRID, hero[1].type)
        assertEquals(listOf("d", "b", "e", "c"), hero[1].gridItems.map { it.id })
        assertEquals(setOf("z", "d", "b", "e", "c"), used)
        assertEquals(listOf("a", "b", "c", "d", "e", "z"), bucket.map { it.id })
    }

    @Test
    fun `two unplayed episodes remain solo cards in newest first order`() {
        val hero = heroes(listOf(unplayedShow("a", "Alpha", 100L), unplayedShow("z", "Zulu", 200L)))

        assertEquals(listOf("z", "a"), displayedIds(hero))
        assertTrue(hero.all { it.type == HeroType.JUMP_BACK_IN && it.gridItems.isEmpty() })
        assertEquals(listOf("FRESH DROP", "NEW EPISODE"), hero.map { it.label })
    }

    @Test
    fun `equal publication dates use case insensitive show names then stable ids`() {
        val bucket =
            listOf(
                unplayedShow("z", "Zulu", 100L),
                unplayedShow("b", "ALPHA", 100L),
                unplayedShow("c", "Bravo", 100L),
                unplayedShow("a", "alpha", 100L),
            )

        assertEquals(listOf("a", "b", "c", "z"), displayedIds(heroes(bucket)))
        assertEquals(listOf("a", "b", "c", "z"), displayedIds(heroes(bucket.reversed())))
    }

    @Test
    fun `missing and invalid dates follow dated episodes`() {
        val bucket =
            listOf(
                unplayedShow("zero", "Alpha", 0L),
                unplayedShow("negative", "Bravo", -1L),
                TestFixtures.podcast(id = "missing", title = "Charlie"),
                unplayedShow("older", "Zulu", 100L),
                unplayedShow("newer", "Yankee", 200L),
            )

        assertEquals(listOf("newer", "older", "zero", "negative", "missing"), displayedIds(heroes(bucket)))
    }

    @Test
    fun `serial show keeps its next episode and sorts by that episode date`() {
        val serial = unplayedShow("serial", "Alpha", 300L).copy(preferredSort = "oldest")
        val regular = unplayedShow("regular", "Zulu", 200L)
        val nextEpisode = TestFixtures.episode(id = "serial-next", podcastId = serial.id, publishedDate = 100L)
        val bucket =
            HomeCatchUpLogic.buildCatchUpBuckets(
                subs = listOf(serial, regular),
                allHistory = emptyList(),
                resolvedSerial = mapOf(serial.id to nextEpisode),
            )

        val hero = heroes(bucket.unplayed)

        assertEquals(listOf("regular", "serial"), displayedIds(hero))
        assertEquals("serial-next", hero[1].podcast.latestEpisode?.id)
        assertEquals(100L, hero[1].podcast.latestEpisode?.publishedDate)
        assertEquals("NEXT", hero[1].label)
    }

    @Test
    fun `empty and single candidates retain their existing layouts`() {
        assertTrue(heroes(emptyList()).isEmpty())
        val single = heroes(listOf(unplayedShow("only", "Only Show", 100L)))
        assertEquals(1, single.size)
        assertEquals("NEW EPISODE", single.single().label)
        assertEquals(HeroType.JUMP_BACK_IN, single.single().type)
    }

    @Test
    fun `spotlight label uses region and genre`() {
        assertEquals("#1 IN INDIA", HomeHeroLogic.spotlightLabel(0, "in", ""))
        assertEquals("TRENDING IN NEWS", HomeHeroLogic.spotlightLabel(1, "us", "News"))
        assertEquals("TRENDING", HomeHeroLogic.spotlightLabel(1, "us", "Podcast"))
    }

    private fun unplayedShow(id: String, title: String, publishedDate: Long): Podcast = TestFixtures.podcast(id = id, title = title).copy(
        latestEpisode = TestFixtures.episode(id = "ep-$id", podcastId = id, publishedDate = publishedDate),
    )

    private fun heroes(bucket: List<Podcast>): List<SmartHeroItem> = HomeHeroLogic.buildHeroItems(
        resumeList = emptyList(),
        unplayedBucket = bucket,
        trendingList = emptyList(),
        subs = bucket,
        region = "us",
    )

    private fun displayedIds(hero: List<SmartHeroItem>): List<String> = hero.flatMap { it.gridItems.ifEmpty { listOf(it.podcast) } }.map { it.id }
}
