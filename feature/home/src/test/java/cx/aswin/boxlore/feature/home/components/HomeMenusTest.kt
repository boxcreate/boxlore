package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.StableCompletedDownloadList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's two menus use boxlore's menu: the Daily/Offline mix menu marks the current mix as a selected
 * row and picks once, and a cover's Pin menu acts once and stays inside the window on a sideways phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi")
class HomeMenusTest {
    @get:Rule val composeRule = createComposeRule()
    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val shows = List(6) { TestFixtures.podcast(id = "show-$it", title = "Show $it").copy(latestEpisode = TestFixtures.episode(id = "e$it", podcastId = "show-$it")) }

    @Test
    fun `the mix menu marks the current mix and picks another once`() {
        val picks = mutableListOf<HomeMixMode>()
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(412.dp, 600.dp)) {
                        HomeMixModule(
                            dailyPodcasts = shows,
                            subscribedPodcastCount = shows.size,
                            completedDownloads = StableCompletedDownloadList(listOf(CompletedDownloadItem(shows[0].latestEpisode!!, shows[0], 1L))),
                            selectedMode = HomeMixMode.DAILY,
                            episodePlaybackState = StablePlaybackStateMap(emptyMap()),
                            softExpireProgressEpisodeIds = emptySet(),
                            currentPlayingEpisodeId = null,
                            isPlaying = false,
                            downloadedEpisodeIds = emptySet(),
                            onModeChanged = { picks += it },
                            onPlayMix = {},
                            onEpisodeClick = { _, _, _ -> },
                            onPlayEpisode = { _, _, _ -> },
                            onViewDownloads = {},
                        )
                    }
                }
            }
        }
        composeRule.onAllNodesWithTag("home_mix_mode_trigger").onFirst().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        val inMenu = hasAnyAncestor(hasTestTag("home_mix_menu"))
        composeRule.onNode(hasText("Daily Mix") and inMenu, useUnmergedTree = false).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule.onNode(hasText("Offline Mix") and inMenu).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(listOf(HomeMixMode.OFFLINE), picks)
        assertTrue("The menu stayed open after a choice", composeRule.onAllNodesWithTag("home_mix_menu").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a cover's Pin menu acts once and stays inside a sideways phone's window`() {
        var toggles = 0
        window.value = BoxloreTestWindows.PHONE_LAND
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(915.dp, 412.dp)) {
                        Row(Modifier.fillMaxSize()) {
                            shows.forEach { show -> SelectorCover(show, null, isSelected = false, onClick = {}, pin = CoverPin(onToggle = { toggles++ })) }
                        }
                    }
                }
            }
        }
        composeRule.onAllNodesWithTag("home_show_selector_${shows.last().id}").onFirst().performSemanticsAction(SemanticsActions.OnLongClick)
        composeRule.waitForIdle()
        // Screen positions: the menu is a popup window of its own.
        val node = composeRule.onAllNodesWithTag("home_pin_menu").onFirst().fetchSemanticsNode()
        val screen = composeRule.onAllNodesWithTag("home_show_selector_${shows.first().id}").onFirst().fetchSemanticsNode().positionOnScreen
        val left = node.positionOnScreen.x - screen.x
        val top = node.positionOnScreen.y - screen.y
        assertTrue("The Pin menu at $left,$top leaves the window", left >= -0.5f && left + node.size.width <= 915.5f && top + node.size.height <= 412.5f)
        composeRule.onNode(hasText("Pin show") and hasAnyAncestor(hasTestTag("home_pin_menu"))).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(1, toggles)
    }
}
