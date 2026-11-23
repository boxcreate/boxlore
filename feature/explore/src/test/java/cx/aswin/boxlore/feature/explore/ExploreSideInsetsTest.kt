package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.COVER_LAND
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.PHONE_LAND
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a sideways phone with the camera cutout at the start (32dp) and three-button navigation at
 * the end (48dp), Explore's and Lore's controls stay between them; portrait phones are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreSideInsetsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_280, false))

    private val window = mutableStateOf(PHONE_LAND)
    private val shows = List(15) { TestFixtures.podcast(id = "$it", title = "Curious world $it", imageUrl = PhoneGoldenRule.ARTWORK_URL) }

    @Test
    fun `Explore's search box, switcher and poster columns stay inside the safe area`() {
        renderExplore()
        for (size in listOf(PHONE_LAND, COVER_LAND)) {
            move(size)
            val root = bounds(fixture.compose.onNodeWithTag(ROOT).fetchSemanticsNode().boundsInRoot)
            fun inside(label: String, rect: Rect) {
                assertTrue("$size $label $rect starts after the cutout (root $root)", rect.left - root.left >= 32f - 0.5f)
                assertTrue("$size $label $rect ends before the buttons (root $root)", root.right - rect.right >= 48f - 0.5f)
            }
            inside("search", bounds(fixture.compose.onNodeWithTag("explore_header_container").fetchSemanticsNode().boundsInRoot))
            val posters = (1..6).flatMap { fixture.compose.onAllNodesWithText("Curious world $it").fetchSemanticsNodes() }.map { bounds(it.boundsInRoot) }
            posters.forEach { inside("poster", it) }
            inside("switcher", bounds(fixture.compose.onAllNodesWithText(fixture.compose.activity.getString(R.string.explore_for_you)).fetchSemanticsNodes().first().boundsInRoot))
        }
        // Portrait phones report no side insets, so the grid keeps its 16dp edge.
        move(PHONE)
        val root = bounds(fixture.compose.onNodeWithTag(ROOT).fetchSemanticsNode().boundsInRoot)
        val first = bounds(fixture.compose.onAllNodesWithText("Curious world 1").fetchSemanticsNodes().first().boundsInRoot)
        assertTrue("phone poster starts 16dp in, not ${first.left - root.left}", first.left - root.left < 32f)
    }

    @Test
    fun `Lore's Play, Add to queue and History stay inside the safe area`() {
        val host = LoreDeckHost(fixture)
        host.render()
        for (size in listOf(PHONE_LAND, COVER_LAND)) {
            host.moveTo(size)
            sendInsets()
            host.settle()
            val root = host.rootBounds()
            listOf(R.string.lore_play_episode, R.string.lore_action_add_to_queue, R.string.lore_action_skip, R.string.lore_history).forEach { id ->
                val label = hasContentDescription(host.string(id))
                val node = if (id == R.string.lore_history) fixture.compose.onNode(label) else host.onActiveCard(label)
                val rect = host.bounds(node)
                assertTrue("$size ${host.string(id)} after the cutout", (rect.left - root.left).value >= 31.5f)
                assertTrue("$size ${host.string(id)} before the buttons", (root.right - rect.right).value >= 47.5f)
            }
        }
        host.moveTo(PHONE)
        assertEquals("the active card is still the first", true, fixture.compose.onAllNodesWithText("Why do familiar places feel different when we return? (0)", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun renderExplore() {
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(size.widthDp.dp, size.heightDp.dp).testTag(ROOT)) {
                        ExploreContent(
                            ExploreUiState.Success(trending = shows, selectedTab = 0),
                            onSearchQueryChanged = {},
                            onCategorySelected = {},
                            onPodcastClick = { _, _, _, _ -> },
                            onEpisodeClick = { _, _ -> },
                            onTabSelected = {},
                            onVibeSelected = { _, _ -> },
                            onClearVibe = {},
                        )
                    }
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    private fun move(size: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = size }
        sendInsets(if (size == PHONE) SystemInsets(statusTop = 24) else SystemInsets.SIDEWAYS_3_BUTTON)
        fixture.compose.waitForIdle()
    }

    private fun sendInsets(insets: SystemInsets = SystemInsets.SIDEWAYS_3_BUTTON) {
        fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, insets) }
        fixture.compose.waitForIdle()
    }

    private fun bounds(rect: Rect): Rect = with(fixture.compose.density) {
        Rect(rect.left.toDp().value, rect.top.toDp().value, rect.right.toDp().value, rect.bottom.toDp().value)
    }

    private companion object {
        const val ROOT = "explore_insets_root"
    }
}
