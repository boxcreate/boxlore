package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hub's cards say what's inside, mark empty sections, open with one tap and keep phone size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryHubCardsTest {
    @get:Rule val compose = createComposeRule()

    private val fixtures = LibraryCaptureFixtures
    private val window = mutableStateOf(BoxloreTestWindows.PHONE)

    private fun showHub(state: LibraryUiState.Success, playlists: LibraryPlaylistsCard? = LibraryPlaylistsCard(onClick = {}, count = 0)) = compose.setContent {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
            BoxLoreTheme(dynamicColor = false) {
                Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    LibraryContent(state, {}, {}, {}, {}, playlists = playlists)
                }
            }
        }
    }

    @Test
    fun `cards say what's inside, and empty sections show a medallion instead of the corner icon`() {
        showHub(LibraryUiState.Success(subscribedPodcasts = fixtures.podcasts.take(3)))
        val statuses = compose.onAllNodesWithTag(HUB_CARD_STATUS_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        val texts = statuses.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        assertTrue(texts.toString(), texts.first().startsWith("3 shows"))
        assertTrue(texts.containsAll(listOf("No likes yet", "Nothing downloaded yet", "Nothing played yet", "No playlists yet")))
        // Liked, Downloads, History and Playlists are empty: four medallions.
        assertEquals(4, compose.onAllNodesWithTag(HUB_CARD_MEDALLION_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a card opens with one tap and keeps today's phone size`() {
        showHub(fixtures.loaded())
        val card = compose.onNode(hasText("Subscriptions", substring = true) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNode()
        assertTrue(card.config.contains(SemanticsActions.OnClick))
        assertEquals(120f, card.boundsInRoot.height)
        assertEquals(412f - 32f, card.boundsInRoot.width)
    }
}
