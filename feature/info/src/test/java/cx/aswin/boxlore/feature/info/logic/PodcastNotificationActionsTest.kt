package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PodcastNotificationActionsTest {
    @Test
    fun `enabling notifications persists consent and tracks it without changing downloads`() = runTest {
        for (autoDownloadEnabled in listOf(false, true)) {
            val podcast = TestFixtures.podcast(id = "show").copy(autoDownloadEnabled = autoDownloadEnabled)
            val state = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast = podcast, episodes = emptyList(), isSubscribed = true))
            val calls = mutableListOf<String>()

            enablePodcastNotifications(
                state,
                { saved, enabled ->
                    assertEquals(podcast, saved)
                    assertTrue(enabled)
                    calls += "persist"
                },
                { id, enabled ->
                    assertEquals("show", id)
                    assertTrue(enabled)
                    calls += "track"
                },
            )

            val updated = state.value as PodcastInfoUiState.Success
            assertEquals(podcast.copy(notificationsEnabled = true), updated.podcast)
            assertEquals(listOf("persist", "track"), calls)
        }
    }

    @Test
    fun `notification action preserves a newer download choice while saving`() = runTest {
        val podcast = TestFixtures.podcast().copy(autoDownloadEnabled = true)
        val initial = PodcastInfoUiState.Success(podcast = podcast, episodes = emptyList(), isSubscribed = true)
        val state = MutableStateFlow<PodcastInfoUiState>(initial)
        val saved = CompletableDeferred<Unit>()
        val action = launch { enablePodcastNotifications(state, { _, _ -> saved.await() }, { _, _ -> }) }
        runCurrent()
        state.value = initial.copy(podcast = podcast.copy(autoDownloadEnabled = false))

        saved.complete(Unit)
        action.join()

        val updated = state.value as PodcastInfoUiState.Success
        assertTrue(updated.podcast.notificationsEnabled)
        assertFalse(updated.podcast.autoDownloadEnabled)
    }

    @Test
    fun `notification save cannot replace a different show that loaded meanwhile`() = runTest {
        val initial = PodcastInfoUiState.Success(podcast = TestFixtures.podcast(id = "first"), episodes = emptyList(), isSubscribed = true)
        val next = PodcastInfoUiState.Success(podcast = TestFixtures.podcast(id = "second"), episodes = emptyList(), isSubscribed = true)
        val state = MutableStateFlow<PodcastInfoUiState>(initial)
        val saved = CompletableDeferred<Unit>()
        val tracked = mutableListOf<String>()
        val action = launch { enablePodcastNotifications(state, { _, _ -> saved.await() }, { id, _ -> tracked += id }) }
        runCurrent()
        state.value = next

        saved.complete(Unit)
        action.join()

        assertEquals(next, state.value)
        assertEquals(listOf("first"), tracked)
    }

    @Test
    fun `loading does not save or track notification consent`() = runTest {
        val state = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Loading)
        enablePodcastNotifications(state, { _, _ -> error("unexpected save") }, { _, _ -> error("unexpected event") })
        assertEquals(PodcastInfoUiState.Loading, state.value)
    }

    @Test
    fun `failed persistence does not update state or report successful consent`() = runTest {
        val initial = PodcastInfoUiState.Success(podcast = TestFixtures.podcast(), episodes = emptyList(), isSubscribed = true)
        val state = MutableStateFlow<PodcastInfoUiState>(initial)
        val result = runCatching {
            enablePodcastNotifications(state, { _, _ -> throw IOException("save failed") }, { _, _ -> error("unexpected event") })
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(initial, state.value)
    }
}
