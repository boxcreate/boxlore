package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The queue holds an episode stored under several IDs once (docs/EPISODE_IDS.md §5). */
class QueueAliasPolicyTest {
    private val mediaIds = listOf("1", "${QueueMath.LEARN_PREFIX}B", "2", "A", "3")

    @Test
    fun anyIdOfTheEpisodeFindsItsQueuedItem() {
        assertEquals(1, QueueAliasPolicy.indexOfAny(mediaIds, setOf("A", "B")))
        assertEquals(-1, QueueAliasPolicy.indexOfAny(mediaIds, setOf("Z")))
    }

    @Test
    fun removingOneIdAlsoRemovesTheOthersButNeverThePlayingItem() {
        assertEquals(listOf(3, 1), QueueAliasPolicy.otherAliasIndices(mediaIds, setOf("A", "B", "C"), removedId = "C", currentIndex = 0))
        assertEquals(listOf(3), QueueAliasPolicy.otherAliasIndices(mediaIds, setOf("A", "B"), removedId = "C", currentIndex = 1))
        assertEquals(emptyList<Int>(), QueueAliasPolicy.otherAliasIndices(mediaIds, setOf("2"), removedId = "2", currentIndex = 0))
    }

    @Test
    fun theInMemoryQueueDropsEveryIdOfTheEpisodeExceptThePlayingOne() {
        val queue = listOf("A", "1", "B", "2")

        assertEquals(listOf("1", "2"), QueueAliasPolicy.withoutEpisode(queue, "B", setOf("A", "B"), playingId = "9") { it })
        assertEquals(listOf("A", "1", "2"), QueueAliasPolicy.withoutEpisode(queue, "B", setOf("A", "B"), playingId = "A") { it })
        assertEquals(listOf("A", "B", "2"), QueueAliasPolicy.withoutEpisode(queue, "1", setOf("1"), playingId = null) { it })
    }
}
