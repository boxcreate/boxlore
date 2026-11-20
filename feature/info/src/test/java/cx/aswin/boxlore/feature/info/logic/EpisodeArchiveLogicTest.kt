package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeArchiveLogicTest {
    private val episodes = List(5) { TestFixtures.episode(id = "e$it", title = "Episode $it") }
    private fun ids(list: List<cx.aswin.boxlore.core.model.Episode>) = list.map { it.id }

    @Test
    fun `archived episodes leave the list unless Show archived is on, and a search shows them`() {
        val archived = setOf("e1")
        val completed = setOf("e2")

        assertEquals(listOf("e0", "e2", "e3", "e4"), ids(EpisodeArchiveLogic.listEpisodes(episodes, false, false, completed, false, archived)))
        assertEquals(listOf("e0", "e3", "e4"), ids(EpisodeArchiveLogic.listEpisodes(episodes, false, true, completed, false, archived)))
        assertEquals(listOf("e0", "e1", "e3", "e4"), ids(EpisodeArchiveLogic.listEpisodes(episodes, false, true, completed, true, archived)))
        assertEquals(ids(episodes), ids(EpisodeArchiveLogic.listEpisodes(episodes, true, true, completed, false, archived)))
        assertTrue(EpisodeArchiveLogic.isHidden("e1", false, completed, false, archived))
        assertFalse(EpisodeArchiveLogic.isHidden("e1", false, completed, true, archived))
    }

    @Test
    fun `the toolbar offers Unarchive only when every selected episode is archived`() {
        assertTrue(EpisodeArchiveLogic.shouldUnarchive(episodes.take(2), setOf("e0", "e1")))
        assertFalse(EpisodeArchiveLogic.shouldUnarchive(episodes.take(2), setOf("e0")))
        assertFalse(EpisodeArchiveLogic.shouldUnarchive(emptyList(), setOf("e0")))
    }

    @Test
    fun `an action changes only the episodes not already as asked, once each, and counts loaded archived episodes`() {
        val selection = episodes.take(3) + episodes.first()
        assertEquals(listOf("e0", "e2"), ids(EpisodeArchiveLogic.changedBy(selection, archive = true, archivedIds = setOf("e1"))))
        assertEquals(listOf("e1"), ids(EpisodeArchiveLogic.changedBy(selection, archive = false, archivedIds = setOf("e1", "e9"))))
        assertEquals(1, EpisodeArchiveLogic.archivedCount(episodes, setOf("e1", "e9")))
    }
}
