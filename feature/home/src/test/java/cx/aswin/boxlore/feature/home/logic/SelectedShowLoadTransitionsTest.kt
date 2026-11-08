package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectedShowLoadTransitionsTest {
    private val pool = ShowEpisodesPool("show-a", inOrder = false, episodes = listOf(TestFixtures.episode(id = "ep-1")))
    private val other = ShowEpisodesPool("show-b", inOrder = false, episodes = listOf(TestFixtures.episode(id = "ep-2")))

    @Test
    fun `a failed first load shows the failure instead of an empty show`() {
        val loading = SelectedShowLoadTransitions.started(SelectedShowLoadTransitions.cleared, switchingPodcast = true)
        assertTrue(loading.loading)
        assertFalse(loading.failed)

        val failed = SelectedShowLoadTransitions.failed(loading)

        assertNull(failed.pool)
        assertFalse(failed.loading)
        assertTrue(failed.failed)
    }

    @Test
    fun `a failed refresh keeps the list on screen and doesn't say it failed`() {
        val shown = SelectedShowLoadTransitions.succeeded(pool)

        val refreshing = SelectedShowLoadTransitions.started(shown, switchingPodcast = false)
        // Same show with a list: refreshed in place, no skeleton.
        assertEquals(pool, refreshing.pool)
        assertFalse(refreshing.loading)

        val failed = SelectedShowLoadTransitions.failed(refreshing)
        assertEquals(pool, failed.pool)
        assertFalse(failed.failed)
    }

    @Test
    fun `try again after a failure shows the skeleton, then the episodes`() {
        val failed = SelectedShowLoadTransitions.failed(SelectedShowLoadTransitions.started(SelectedShowLoadTransitions.cleared, switchingPodcast = true))

        val retrying = SelectedShowLoadTransitions.started(failed, switchingPodcast = false)
        assertTrue(retrying.loading)
        assertFalse(retrying.failed)

        val recovered = SelectedShowLoadTransitions.succeeded(pool)
        assertEquals(pool, recovered.pool)
        assertFalse(recovered.loading)
        assertFalse(recovered.failed)
    }

    @Test
    fun `a retry that fails again says so again`() {
        val failed = SelectedShowLoadTransitions.failed(SelectedShowLoadTransitions.started(SelectedShowLoadTransitions.cleared, switchingPodcast = true))

        val failedAgain = SelectedShowLoadTransitions.failed(SelectedShowLoadTransitions.started(failed, switchingPodcast = false))

        assertTrue(failedAgain.failed)
    }

    @Test
    fun `switching shows drops the old show's episodes and its failure`() {
        val shown = SelectedShowLoadTransitions.succeeded(pool)

        val switching = SelectedShowLoadTransitions.started(shown, switchingPodcast = true)
        assertNull(switching.pool)
        assertTrue(switching.loading)

        assertEquals(other, SelectedShowLoadTransitions.succeeded(other).pool)
        val afterFailure = SelectedShowLoadTransitions.started(SelectedShowLoadTransitions.failed(switching), switchingPodcast = true)
        assertFalse(afterFailure.failed)
    }
}
