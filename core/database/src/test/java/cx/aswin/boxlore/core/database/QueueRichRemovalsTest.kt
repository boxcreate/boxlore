package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.database.dao.QueueDao
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Rich queue removals kept beside the plain ID list (docs/EPISODE_IDS.md §5, queue removals). */
class QueueRichRemovalsTest {
    private val episodeA = QueueRemovalIdentity("A", RECIPE, SHOW, KEY)
    private val episodeB = QueueRemovalIdentity("B", RECIPE, SHOW, KEY)

    @Test
    fun aRemovalRecordsEveryIdWithItsIdentityAndPlainIdsAlone() {
        val stored = QueueDao.updatedRichRemovals(
            stored = null,
            removedIds = listOf("A", "B", "-7"),
            removedIdentities = listOf(episodeA, episodeB),
            restoredIds = emptyList(),
            restoredIdentities = emptyList(),
        )

        assertEquals(listOf(episodeA, episodeB, QueueRemovalIdentity("-7")), QueueRemovalIdentity.parseList(stored))
        assertEquals(
            """[{"id":"A","recipeId":$RECIPE,"show":"100","catalogKey":"guid-a"},""" +
                """{"id":"B","recipeId":$RECIPE,"show":"100","catalogKey":"guid-a"},{"id":"-7"}]""",
            stored,
        )
    }

    @Test
    fun restoringTheEpisodeUnderAnotherIdDropsItsRichRemoval() {
        val stored = QueueRemovalIdentity.serializeList(listOf(episodeA, QueueRemovalIdentity("-7")))

        val restored = QueueDao.updatedRichRemovals(stored, emptyList(), emptyList(), listOf("B"), listOf(episodeB))

        assertEquals(listOf(QueueRemovalIdentity("-7")), QueueRemovalIdentity.parseList(restored))
        // Restoring by a plain ID drops only that ID.
        assertNull(QueueDao.updatedRichRemovals(restored, emptyList(), emptyList(), listOf("-7"), emptyList()))
    }

    @Test
    fun anEpisodeThatStaysQueuedUnderAnotherIdIsNotRecordedAsRemoved() {
        val stored = QueueDao.updatedRichRemovals(
            stored = null,
            removedIds = listOf("A"),
            removedIdentities = listOf(episodeA),
            restoredIds = listOf("B"),
            restoredIdentities = listOf(episodeB),
        )

        assertNull(stored)
    }

    @Test
    fun theListKeepsTheNewestFiftyAndALaterRemovalOfAnIdMovesItLast() {
        var stored: String? = null
        (1..55).forEach { stored = QueueDao.updatedRichRemovals(stored, listOf("-$it"), emptyList(), emptyList(), emptyList()) }
        stored = QueueDao.updatedRichRemovals(stored, listOf("-10"), emptyList(), emptyList(), emptyList())

        val ids = QueueRemovalIdentity.parseList(stored).map { it.id }
        assertEquals(50, ids.size)
        assertEquals("-6", ids.first())
        assertEquals("-10", ids.last())
        assertEquals(1, ids.count { it == "-10" })
    }

    @Test
    fun aPulledQueueStateKeepsOnlyRichRemovalsThePlainListStillHoldsAndNoQueuedEpisode() {
        val stored = QueueRemovalIdentity.serializeList(listOf(episodeA, QueueRemovalIdentity("-7"), QueueRemovalIdentity("-8")))

        // The pull dropped -8 from the plain list and queued the episode again under B.
        val kept = QueueDao.richRemovalsWithin(stored, plainIds = listOf("A", "-7"), queued = listOf(episodeB))

        assertEquals(listOf(QueueRemovalIdentity("-7")), QueueRemovalIdentity.parseList(kept))
        // Nothing to drop leaves the stored text as it is; an empty result clears it.
        assertEquals(stored, QueueDao.richRemovalsWithin(stored, listOf("A", "-7", "-8"), emptyList()))
        assertNull(QueueDao.richRemovalsWithin(stored, emptyList(), emptyList()))
        assertNull(QueueDao.richRemovalsWithin(null, listOf("A"), emptyList()))
    }

    @Test
    fun unreadableEntriesAndRecipeIdsOutsideTheRangeAreDropped() {
        val raw = """[{"id":"A","recipeId":5,"show":"100"},{"recipeId":$RECIPE},"x",{"id":"","show":"1"},{"id":"C","catalogKey":7}]"""

        assertEquals(
            listOf(QueueRemovalIdentity("A", null, "100", null), QueueRemovalIdentity("C", null, null, "7")),
            QueueRemovalIdentity.parseList(raw),
        )
        assertEquals(emptyList<QueueRemovalIdentity>(), QueueRemovalIdentity.parseList("not json"))
        assertEquals(emptyList<QueueRemovalIdentity>(), QueueRemovalIdentity.parseList(null))
    }

    private companion object {
        const val SHOW = "100"
        const val KEY = "guid-a"
        const val RECIPE = 4_503_599_627_370_500L
    }
}
