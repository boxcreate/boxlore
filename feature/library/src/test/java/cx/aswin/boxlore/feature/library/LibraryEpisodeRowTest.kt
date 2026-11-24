package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Liked: its count line, Play all (the unfinished liked episodes in order, through the shared
 * multi-show path), and the shared Library episode row; duplicate backend rows don't break the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryEpisodeRowTest {
    @get:Rule val compose = createComposeRule()

    private val fixtures = LibraryCaptureFixtures

    @Test
    fun `Play all plays the unfinished liked episodes in order, once`() {
        val liked = fixtures.liked(4).mapIndexed { index, item -> if (index == 1) item.copy(isCompleted = true) else item }
        // A backend duplicate of the first row.
        val withDuplicate = liked + liked.first()
        val played = mutableListOf<List<Episode>>()
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(412.dp, 915.dp)) {
                        LikedEpisodesScreen(fixtures.viewModel(LibraryUiState.Success(likedEpisodes = withDuplicate)), {}, {}, { _, _ -> }, onPlayEpisodes = { episodes, _ -> played += episodes })
                    }
                }
            }
        }
        compose.onNodeWithText("4 episodes", substring = true).assertExists()
        compose.onNodeWithTag(LIKED_PLAY_ALL_TAG).performClick()
        assertEquals(listOf(listOf("liked-0", "liked-2", "liked-3")), played.map { list -> list.map { it.id } })
    }

    @Test
    fun `time left counts only unfinished episodes with a known length`() {
        val liked = fixtures.liked(3)
        // 3 hours, less the 0, 2 and 4 minutes already played.
        assertEquals(3 * 3_600L - (0 + 120 + 240), likedTimeLeftSeconds(liked))
        assertNull(likedTimeLeftSeconds(liked.map { it.copy(durationMs = 0) }))
        assertEquals(listOf("liked-0", "liked-2"), likedPlayAll(liked.mapIndexed { i, item -> if (i == 1) item.copy(isCompleted = true) else item }).map { it.id })
    }
}
