package cx.aswin.boxlore.feature.home.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The rating prompt and the thank-you sheet after it share one look: a sheet on phones and a centred
 * panel on tablets, with pill buttons that grow with the text. Back closes the thank-you sheet, and
 * crossing the 840dp panel width neither closes it nor counts its page view again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewSheetAdaptiveTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val fontScale = mutableFloatStateOf(1f)
    private val thanks = mutableStateOf(false)
    private var dismissals = 0
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    @Before fun setUp() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @After fun tearDown() = restore()

    private fun render() {
        compose.setContent {
            val layout = window.value
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.floatValue), LocalBoxloreWindowLayout provides layout) {
                run {
                    BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                        Box(Modifier.requiredSize(layout.widthDp.dp, layout.heightDp.dp)) {
                            if (thanks.value) {
                                PostReviewSheet(onDismissRequest = { dismissals++ }, onNavigateToFeedback = {})
                            } else {
                                ReviewPromptSheet(completedCount = 12, onDismissRequest = { dismissals++ }, onNavigateToReview = {}, onNavigateToFeedback = {})
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `both sheets are centred panels on tablets`() {
        window.value = BoxloreTestWindows.TABLET
        render()
        assertTrue("The prompt is a centred panel", panelShown())
        compose.runOnIdle { thanks.value = true }
        compose.waitForIdle()
        assertTrue("The thank-you sheet is a centred panel", panelShown())
    }

    @Test
    fun `buttons grow with very large text and keep their words whole`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        fontScale.floatValue = 2f
        for (showThanks in listOf(false, true)) {
            thanks.value = showThanks
            if (!showThanks) render() else compose.waitForIdle()
            val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.size.width > 200 }
            assertTrue("No buttons in the ${if (showThanks) "thank-you" else "prompt"} sheet", buttons.size >= 2)
            buttons.forEach { button -> assertTrue("A button is ${button.size.height}dp tall", button.size.height >= 48) }
            compose.onAllNodes(hasAnyAncestor(hasClickAction())).fetchSemanticsNodes().forEach { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()?.let { assertTrue("A button label is cut", !it.hasVisualOverflow) }
            }
        }
    }

    @Test
    fun `Back closes the thank-you sheet and 840dp changes neither close it nor count it again`() {
        thanks.value = true
        window.value = BoxloreTestWindows.W839
        render()
        val views = events.count { (_, properties) -> properties.values.any { it == "post_review" } }
        for (layout in listOf(BoxloreTestWindows.W841, BoxloreTestWindows.W839, BoxloreTestWindows.W841)) {
            compose.runOnIdle { window.value = layout }
            compose.waitForIdle()
        }
        assertEquals("The thank-you sheet closed on a resize", 0, dismissals)
        assertEquals("Its page view counted again", views, events.count { (_, properties) -> properties.values.any { it == "post_review" } })
        compose.runOnUiThread { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals("Back closes the thank-you sheet once", 1, dismissals)
    }

    private fun panelShown() = compose.onAllNodesWithTag("expanded_sheet_panel", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
}
