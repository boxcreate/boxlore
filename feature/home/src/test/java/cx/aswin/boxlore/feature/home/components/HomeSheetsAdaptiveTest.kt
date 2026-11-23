package cx.aswin.boxlore.feature.home.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The show picker under Because you like: a sheet on phones and a centred panel on tablets, with the
 * current pick marked, every show reachable at large text, and the list's place kept when the window
 * crosses the 840dp panel width in both directions without closing the picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSheetsAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val fontScale = mutableFloatStateOf(1f)
    private val shows = List(14) { TestFixtures.podcast(id = "show-$it", title = "The Rest Is History: The Rise and Fall of Everything $it", artist = "Goalhanger") }
    private val picks = mutableListOf<Podcast?>()
    private var dismissals = 0

    private fun render() {
        compose.setContent {
            val layout = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout, LocalDensity provides Density(1f, fontScale.floatValue)) {
                BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                    Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                        ChangeRecommendationPodcastSheet(
                            candidatePodcasts = shows,
                            selectedPodcastId = shows[2].id,
                            onDismissRequest = { dismissals++ },
                            onPodcastSelect = { picks += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the current pick is marked and Pick for me resets once`() {
        render()
        compose.onNodeWithTag("home_change_show_${shows[2].id}").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithTag("home_change_show_${shows[3].id}").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        compose.onNodeWithTag("home_change_show_auto").performClick()
        assertEquals(listOf<Podcast?>(null), picks)
    }

    @Test
    fun `on the Fold 8 cover at large text the last show is reachable and picks once`() {
        window.value = BoxloreTestWindows.COVER_475
        fontScale.floatValue = 1.3f
        render()
        val last = shows.last().id
        compose.onNodeWithTag("home_change_show_list").performScrollToNode(hasTestTag("home_change_show_$last"))
        compose.onNodeWithTag("home_change_show_$last").assertIsDisplayed().performClick()
        assertEquals(listOf<Podcast?>(shows.last()), picks)
    }

    @Test
    fun `tablets show a centred panel and the list keeps its place across 840dp both ways`() {
        window.value = BoxloreTestWindows.TABLET
        render()
        val panel = compose.onNodeWithTag("expanded_sheet_panel", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The panel is ${panel.width}dp wide", panel.width <= 640f + 0.5f)
        compose.onNodeWithTag("home_change_show_list").performScrollToNode(hasTestTag("home_change_show_${shows[10].id}"))
        compose.waitForIdle()
        for (layout in listOf(BoxloreTestWindows.W841, BoxloreTestWindows.W839, BoxloreTestWindows.W841, BoxloreTestWindows.W839)) {
            window.value = layout
            compose.waitForIdle()
            val label = "${layout.widthDp}x${layout.heightDp}"
            compose.onNodeWithTag("home_change_show_${shows[10].id}").assertIsDisplayed()
            assertEquals("$label: the picker closed", 0, dismissals)
        }
    }
}
