package cx.aswin.boxlore.core.playback.service.auto

import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseChangeNotifier.Change
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which car screens reload when something changes on the phone. */
class AutoBrowseChangeNotifierTest {
    @Test
    fun `everything Home shows reloads it, and the queue does not because it lives in the player`() {
        Change.entries.filter { it != Change.QUEUE }.forEach { change ->
            assertTrue(AutoBrowseContract.HOME_ID in AutoBrowseChangeNotifier.nodesFor(change), "$change")
        }
        assertEquals(listOf(AutoBrowseContract.HOME_QUEUE_ID), AutoBrowseChangeNotifier.nodesFor(Change.QUEUE))
    }

    @Test
    fun `following a show or a new episode arriving reloads Shows and New`() {
        val nodes = AutoBrowseChangeNotifier.nodesFor(Change.SHOWS)

        assertTrue(AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID in nodes && AutoBrowseContract.HOME_NEW_EPISODES_ID in nodes)
    }

    @Test
    fun `finishing an episode reloads the lists that leave played episodes out`() {
        val nodes = AutoBrowseChangeNotifier.nodesFor(Change.PLAYED)

        assertTrue(AutoBrowseContract.HOME_NEW_EPISODES_ID in nodes && AutoBrowseContract.CONTINUE_ID in nodes)
    }

    @Test
    fun `downloads and likes reload their own lists`() {
        assertTrue(AutoBrowseContract.DOWNLOADS_ID in AutoBrowseChangeNotifier.nodesFor(Change.DOWNLOADS))
        assertTrue(AutoBrowseContract.LIBRARY_LIKED_ID in AutoBrowseChangeNotifier.nodesFor(Change.LIKED))
    }
}
