package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SerialEpisodeLogicTest {
    @Test
    fun `resolve next serial episode advances from ongoing episode`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "ep-1"),
                TestFixtures.episode(id = "ep-2"),
                TestFixtures.episode(id = "ep-3"),
            )

        val next =
            resolveNextSerialEpisode(
                allEpisodes = episodes,
                ongoingId = "ep-1",
                lastCompletedId = null,
                completedEpIdsForResolve = emptySet(),
                inProgressEpIdsForResolve = emptySet(),
            )

        assertEquals("ep-2", next?.id)
    }

    @Test
    fun `resolve next serial episode advances from last completed when no ongoing`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "ep-1"),
                TestFixtures.episode(id = "ep-2"),
            )

        val next =
            resolveNextSerialEpisode(
                allEpisodes = episodes,
                ongoingId = null,
                lastCompletedId = "ep-1",
                completedEpIdsForResolve = setOf("ep-1"),
                inProgressEpIdsForResolve = emptySet(),
            )

        assertEquals("ep-2", next?.id)
    }

    @Test
    fun `resolve next serial episode skips completed and in progress when at end of chain`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "ep-1"),
                TestFixtures.episode(id = "ep-2"),
                TestFixtures.episode(id = "ep-3"),
            )

        val next =
            resolveNextSerialEpisode(
                allEpisodes = episodes,
                ongoingId = "ep-3",
                lastCompletedId = null,
                completedEpIdsForResolve = setOf("ep-1"),
                inProgressEpIdsForResolve = setOf("ep-3"),
            )

        assertEquals("ep-2", next?.id)
    }

    @Test
    fun `resolve next serial episode returns null when all episodes are consumed`() {
        val episodes =
            listOf(
                TestFixtures.episode(id = "ep-1"),
                TestFixtures.episode(id = "ep-2"),
            )

        val next =
            resolveNextSerialEpisode(
                allEpisodes = episodes,
                ongoingId = "ep-2",
                lastCompletedId = null,
                completedEpIdsForResolve = setOf("ep-1", "ep-2"),
                inProgressEpIdsForResolve = setOf("ep-2"),
            )

        assertNull(next)
    }
}
