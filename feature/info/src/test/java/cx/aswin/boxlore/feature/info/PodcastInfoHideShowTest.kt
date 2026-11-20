package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.HiddenShow
import cx.aswin.boxlore.core.model.HiddenShows
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Hide show everywhere on the show page: what is stored, the unfollow, Undo and the analytics. */
class PodcastInfoHideShowTest {
    private class Harness(scope: TestScope, podcast: Podcast, followed: Boolean) {
        val hidden = MutableStateFlow<List<HiddenShow>>(emptyList())
        val page = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, emptyList(), isSubscribed = followed))
        val follows = mutableListOf<Boolean>()
        val tracked = mutableListOf<Pair<String, String>>()
        val place = HiddenLibraryPlace(pinnedToHome = true, manualOrderIndex = 3)
        val restored = mutableListOf<Triple<String, HiddenFollowSettings, HiddenLibraryPlace>>()
        val hideShow =
            PodcastInfoHideShow(
                scope = scope,
                hiddenIds = hidden.map(HiddenShows::matchIds),
                hide = { show -> hidden.value = listOf(show) + hidden.value.filterNot { it.id == show.id } },
                unhide = { ids -> hidden.value = hidden.value.filterNot { show -> show.matchIds.any { it in ids } } },
                page = { page.value as? PodcastInfoUiState.Success },
                toggleFollow = { state ->
                    follows += !state.isSubscribed
                    // Unfollowing clears the follow's settings, as SubscriptionRepository does.
                    val podcast =
                        if (state.isSubscribed) {
                            state.podcast.copy(notificationsEnabled = false, autoDownloadEnabled = false, customGenre = null, customGenreIcon = null)
                        } else {
                            state.podcast
                        }
                    page.value = state.copy(podcast = podcast, isSubscribed = !state.isSubscribed)
                },
                track = { action, id -> tracked += action to id },
                libraryPlace = { place },
                restoreFollow = { state, follow, place -> restored += Triple(state.podcast.id, follow, place) },
            )

        fun isHidden() = hideShow.isHidden(page)
    }

    private val show = TestFixtures.podcast(id = "42", title = "Curious world", imageUrl = "https://art/42")

    @Test
    fun `hiding a followed show stores it, unfollows it, and Undo brings both back`() = runTest {
        val h = Harness(this, show, followed = true)

        h.hideShow.hideShow()
        advanceUntilIdle()

        assertEquals(listOf(HiddenShow("42", "Curious world", "https://art/42")), h.hidden.value)
        assertEquals(listOf(false), h.follows)
        assertTrue(h.isHidden().first())
        assertEquals(listOf(PodcastInfoHideShow.ACTION_HIDDEN to "42"), h.tracked)

        h.hideShow.undoHide()
        advanceUntilIdle()

        assertTrue(h.hidden.value.isEmpty())
        assertEquals(listOf(false, true), h.follows)
        assertTrue((h.page.value as PodcastInfoUiState.Success).isSubscribed)
        assertFalse(h.isHidden().first())
        assertEquals(PodcastInfoHideShow.ACTION_UNHIDDEN to "42", h.tracked.last())

        // Undo runs once: a second tap does nothing.
        h.hideShow.undoHide()
        advanceUntilIdle()
        assertEquals(listOf(false, true), h.follows)
    }

    @Test
    fun `Undo puts back the notifications, auto-download, tag, Home pin and library place the follow had`() = runTest {
        // Reviewed: Undo followed the show again with notifications and auto-download off and no tag.
        val followed = show.copy(notificationsEnabled = true, autoDownloadEnabled = true, customGenre = "Science", customGenreIcon = "flask")
        val h = Harness(this, followed, followed = true)

        h.hideShow.hideShow()
        advanceUntilIdle()
        h.hideShow.undoHide()
        advanceUntilIdle()

        assertEquals(
            listOf(Triple("42", HiddenFollowSettings(true, true, "Science", "flask"), HiddenLibraryPlace(pinnedToHome = true, manualOrderIndex = 3))),
            h.restored,
        )
    }

    @Test
    fun `an Undo tapped before the hide is stored still unhides and follows the show again`() = runTest {
        // Reviewed: the pending Undo was set only after the hide's DataStore write, so an early tap did nothing.
        val h = Harness(this, show, followed = true)

        // Both taps land before the launched hide has stored anything.
        h.hideShow.hideShow()
        h.hideShow.undoHide()
        advanceUntilIdle()

        assertTrue(h.hidden.value.isEmpty())
        assertEquals(listOf(false, true), h.follows)
        assertEquals(1, h.restored.size)
    }

    @Test
    fun `Undo restores nothing for a show the listener followed again before tapping it`() = runTest {
        val h = Harness(this, show, followed = true)
        h.hideShow.hideShow()
        advanceUntilIdle()
        h.page.value = (h.page.value as PodcastInfoUiState.Success).copy(isSubscribed = true)

        h.hideShow.undoHide()
        advanceUntilIdle()

        assertEquals(listOf(false), h.follows)
        assertTrue(h.restored.isEmpty())
        assertTrue(h.hidden.value.isEmpty())
    }

    @Test
    fun `hiding an unfollowed show never follows or unfollows, and Undo leaves it unfollowed`() = runTest {
        val h = Harness(this, show, followed = false)

        h.hideShow.hideShow()
        advanceUntilIdle()
        h.hideShow.undoHide()
        advanceUntilIdle()

        assertTrue(h.follows.isEmpty())
        assertTrue(h.restored.isEmpty())
        assertTrue(h.hidden.value.isEmpty())
        assertFalse((h.page.value as PodcastInfoUiState.Success).isSubscribed)
    }

    @Test
    fun `an RSS copy hidden under its Podcast Index show is hidden and unhidden by either ID`() = runTest {
        val rss = show.copy(id = "rss:https://feed.example/a", sourceType = Podcast.SOURCE_RSS, linkedPodcastIndexId = "42")
        val h = Harness(this, rss, followed = false)

        h.hideShow.hideShow()
        advanceUntilIdle()
        assertEquals(setOf("42"), h.hidden.value.single().aliasIds)
        assertTrue(h.isHidden().first())

        // The Podcast Index page of the same show reads as hidden and unhides it.
        h.page.value = PodcastInfoUiState.Success(show, emptyList(), isSubscribed = false)
        assertTrue(h.isHidden().first())
        h.hideShow.unhideShow()
        advanceUntilIdle()
        assertTrue(h.hidden.value.isEmpty())
        assertEquals(PodcastInfoHideShow.ACTION_UNHIDDEN to "42", h.tracked.last())
    }

    @Test
    fun `unhiding clears the pending Undo, so it can't follow the show again`() = runTest {
        val h = Harness(this, show, followed = true)
        h.hideShow.hideShow()
        advanceUntilIdle()

        h.hideShow.unhideShow()
        h.hideShow.undoHide()
        advanceUntilIdle()

        assertEquals(listOf(false), h.follows)
        assertTrue(h.hidden.value.isEmpty())
    }
}
