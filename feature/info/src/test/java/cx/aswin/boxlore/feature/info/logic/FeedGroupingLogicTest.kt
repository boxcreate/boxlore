package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedGroupingLogicTest {
    @Test
    fun `groupEpisodes keeps normal episodes in order`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "1").copy(episodeType = "full"),
                TestFixtures.episode(id = "2").copy(episodeType = "full"),
            )

        val grouped = groupEpisodes(episodes)

        assertEquals(2, grouped.size)
        assertTrue(grouped[0] is FeedItem.NormalEpisode)
        assertEquals("1", (grouped[0] as FeedItem.NormalEpisode).episode.id)
        assertEquals(0, (grouped[0] as FeedItem.NormalEpisode).globalIndex)
        assertEquals("2", (grouped[1] as FeedItem.NormalEpisode).episode.id)
    }

    @Test
    fun `groupEpisodes collapses consecutive trailers into a stack`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "t1").copy(episodeType = "trailer"),
                TestFixtures.episode(id = "t2").copy(episodeType = "trailer"),
                TestFixtures.episode(id = "full").copy(episodeType = "full"),
            )

        val grouped = groupEpisodes(episodes)

        assertEquals(2, grouped.size)
        val stack = grouped[0] as FeedItem.TrailerGroup
        assertEquals(listOf("t1", "t2"), stack.trailers.map { it.first.id })
        assertTrue(grouped[1] is FeedItem.NormalEpisode)
    }

    @Test
    fun `groupEpisodes keeps a single trailer as SingleTrailer`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "t1").copy(episodeType = "trailer"),
                TestFixtures.episode(id = "full").copy(episodeType = "full"),
            )

        val grouped = groupEpisodes(episodes)

        assertEquals(2, grouped.size)
        assertTrue(grouped[0] is FeedItem.SingleTrailer)
        assertEquals("t1", (grouped[0] as FeedItem.SingleTrailer).episode.id)
    }

    @Test
    fun `resolveAutoScrollTarget prefers ongoing episode`() {
        val feed =
            groupEpisodes(
                listOf(
                    TestFixtures.episode(id = "a"),
                    TestFixtures.episode(id = "b"),
                    TestFixtures.episode(id = "c"),
                ),
            )

        val target =
            resolveAutoScrollTarget(
                feedItems = feed,
                completedEpisodeIds = emptySet(),
                ongoingEpisodeIds = setOf("b"),
            )

        assertEquals(1, target.jumpIndex)
        assertTrue(target.isOngoing)
        assertEquals("b", target.jumpEpisode?.id)
        assertEquals("c", target.badgeEpisodeId)
    }

    @Test
    fun `resolveAutoScrollTarget jumps after last completed when nothing ongoing`() {
        val feed =
            groupEpisodes(
                listOf(
                    TestFixtures.episode(id = "a"),
                    TestFixtures.episode(id = "b"),
                    TestFixtures.episode(id = "c"),
                ),
            )

        val target =
            resolveAutoScrollTarget(
                feedItems = feed,
                completedEpisodeIds = setOf("a", "b"),
                ongoingEpisodeIds = emptySet(),
            )

        assertEquals(2, target.jumpIndex)
        assertFalse(target.isOngoing)
        assertEquals("c", target.jumpEpisode?.id)
        assertEquals("c", target.badgeEpisodeId)
    }

    @Test
    fun `resolveAutoScrollTarget falls back to first item`() {
        val feed = groupEpisodes(listOf(TestFixtures.episode(id = "only")))

        val target =
            resolveAutoScrollTarget(
                feedItems = feed,
                completedEpisodeIds = emptySet(),
                ongoingEpisodeIds = emptySet(),
            )

        assertEquals(0, target.jumpIndex)
        assertFalse(target.isOngoing)
        assertEquals("only", target.jumpEpisode?.id)
        assertEquals("only", target.badgeEpisodeId)
    }
}
