package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.components.EpisodeLinkStatusTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives one live episode page opened from a shared link through its "finding this episode",
 * "not found" and loaded states across the shared window gates (600dp width, 480dp height, 840dp
 * landscape split, portrait/landscape and a fold posture change) in both directions, then recreates
 * it with saved state. The state lives in the page's ViewModel, so no window change or recreation
 * starts the feed read again.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodeInfoAdaptiveTransitionTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false))

    private val window = mutableStateOf(COMPACT)
    private val uiState = MutableStateFlow<EpisodeInfoUiState>(EpisodeInfoUiState.Loading)
    private val model = mock(EpisodeInfoViewModel::class.java)
    private lateinit var restoration: StateRestorationTester
    private val openedShows = mutableListOf<String>()
    private var backs = 0

    @Test
    fun `finding the episode survives window changes and recreation without a second load`() {
        render()
        uiState.value = EpisodeInfoUiState.ReadingFeed
        fixture.compose.waitForIdle()

        acrossWindowChanges { readingFeedShown() }
        recreate(FOLD_TABLETOP)
        readingFeedShown()
        acrossWindowChanges { readingFeedShown() }

        // One load for the first composition and one for the recreated page; window changes never
        // reload, and the ViewModel skips a repeat of a running load (EpisodeLoadRequestsTest).
        val loads = mockingDetails(model).invocations.count { it.method.name == "loadEpisode" && it.arguments.first() == LINK_ID }
        assertEquals(2, loads)
    }

    @Test
    fun `not found keeps its actions across window changes and calls them once`() {
        render()
        uiState.value = EpisodeInfoUiState.ReadingFeed
        fixture.compose.waitForIdle()
        window.value = TABLET
        uiState.value = EpisodeInfoUiState.NotFound("920666")
        fixture.compose.waitForIdle()

        acrossWindowChanges { notFoundShown(withShow = true) }
        fixture.compose.onNodeWithText("View show").performClick()
        fixture.compose.waitForIdle()
        assertEquals(listOf("920666"), openedShows)

        recreate(COMPACT)
        notFoundShown(withShow = true)
        fixture.compose.onNodeWithText("Back").performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, backs)
        assertEquals(listOf("920666"), openedShows)
    }

    @Test
    fun `not found without a show offers only Back, and a later answer shows the episode`() {
        render()
        uiState.value = EpisodeInfoUiState.NotFound("")
        fixture.compose.waitForIdle()
        acrossWindowChanges { notFoundShown(withShow = false) }

        // Recovery: the page loads the episode (a retry with the show found it in the feed).
        val episode = TestFixtures.episode(id = LINK_ID, title = "Found in the feed", podcastId = "920666", podcastTitle = "Curious world")
        uiState.value = EpisodeInfoUiState.Success(episode, "920666", "Curious world", relatedEpisodesLoading = false, similarEpisodesLoading = false)
        fixture.compose.waitForIdle()
        acrossWindowChanges {
            fixture.compose.onNodeWithTag(EpisodeLinkStatusTags.NOT_FOUND).assertDoesNotExist()
            val titles = fixture.compose.onAllNodesWithText("Found in the feed", useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("the loaded episode is shown", titles.isNotEmpty())
        }
        assertEquals(0, backs)
    }

    private fun readingFeedShown() {
        fixture.compose.onNodeWithTag(EpisodeLinkStatusTags.READING_FEED).assertIsDisplayed()
        fixture.compose.onNodeWithText("Finding this episode").assertIsDisplayed()
        fixture.compose.onNodeWithText("Checking the show’s feed. This can take a moment.").assertIsDisplayed()
        fixture.compose.onNodeWithText("Back").assertDoesNotExist()
    }

    private fun notFoundShown(withShow: Boolean) {
        fixture.compose.onNodeWithTag(EpisodeLinkStatusTags.NOT_FOUND).assertIsDisplayed()
        fixture.compose.onNodeWithText("Episode not found").assertIsDisplayed()
        fixture.compose.onNodeWithText("It may have been removed from the show.").assertIsDisplayed()
        fixture.compose.onNodeWithText("Back").assertIsDisplayed()
        if (withShow) {
            fixture.compose.onNodeWithText("View show").assertIsDisplayed()
        } else {
            fixture.compose.onNodeWithText("View show").assertDoesNotExist()
        }
    }

    /** Moves the same live page across every gate and back, asserting after each step. */
    private fun acrossWindowChanges(assertion: () -> Unit) {
        val start = window.value
        (TRANSITIONS + start).forEachIndexed { step, size ->
            fixture.compose.runOnIdle { window.value = size }
            fixture.compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            fixture.compose.waitForIdle()
            try {
                assertion()
            } catch (failure: AssertionError) {
                throw AssertionError("After window step $step ($size): ${failure.message}", failure)
            }
        }
    }

    private fun recreate(to: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = to }
        restoration.emulateSavedInstanceStateRestore()
        fixture.compose.waitForIdle()
    }

    private fun render() {
        `when`(model.uiState).thenReturn(uiState)
        `when`(model.moreLikeThisOn).thenReturn(MutableStateFlow(false))
        `when`(model.likedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.completedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.isDownloaded(LINK_ID)).thenReturn(flowOf(false))
        `when`(model.isDownloading(LINK_ID)).thenReturn(flowOf(false))
        `when`(model.showRemoveDownloadDialog).thenReturn(MutableStateFlow(false))
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(fixture.compose)
        restoration.setContent {
            val size = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides size.widthDp,
            ) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface {
                            EpisodeInfoScreen(
                                episodeId = LINK_ID,
                                episodeTitle = "",
                                episodeDescription = "",
                                episodeImageUrl = "",
                                episodeAudioUrl = "",
                                episodeDuration = 0,
                                podcastId = "",
                                podcastTitle = "Podcast",
                                viewModel = model,
                                onBack = { backs++ },
                                onPodcastClick = { openedShows += it },
                                onEpisodeClick = {},
                                onPlay = {},
                            )
                        }
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private companion object {
        const val LINK_ID = "4503599627370500"
        val COMPACT = BoxloreWindowLayout(599f, 800f)
        val TABLET = BoxloreWindowLayout(1_280f, 800f)
        val FOLD_FLAT = BoxloreWindowLayout(673f, 841f)
        val FOLD_TABLETOP = FOLD_FLAT.copy(
            folds = listOf(
                BoxloreFold(
                    BoxloreFoldState.HalfOpened,
                    BoxloreFoldOrientation.Horizontal,
                    BoxloreLayoutBounds(0f, 410f, 673f, 431f),
                    true,
                    true,
                ),
            ),
        )

        /** Each gate is crossed in both directions; the walk ends where it started. */
        val TRANSITIONS = listOf(
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(600f, 800f),
            BoxloreWindowLayout(599f, 800f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(840f, 479f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(840f, 600f),
            BoxloreWindowLayout(839f, 600f),
            BoxloreWindowLayout(600f, 960f),
            BoxloreWindowLayout(960f, 600f),
            BoxloreWindowLayout(600f, 960f),
            FOLD_FLAT,
            FOLD_TABLETOP,
            FOLD_FLAT,
        )
    }
}
