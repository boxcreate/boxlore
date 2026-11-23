package cx.aswin.boxlore.feature.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Onboarding search on phones, sideways phones, the Fold 8 cover and tablets: the shows get the
 * room (the topic chips scroll away where the grid would show under one and a half rows), the
 * results scroll clear of the keyboard, the picks wait in one bar that opens a sheet, each show is
 * one checkbox, the grid loads into skeletons, and the list keeps its place through window changes
 * and recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingSearchLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val query = mutableStateOf("")
    private val picked = mutableStateOf(setOf<String>())
    private val loading = mutableStateOf(false)
    private val genre = mutableStateOf<String?>(null)
    private val toggles = mutableListOf<String>()
    private var done = 0
    private val shows = (1..30).map { Podcast(id = "$it", title = "Show $it", artist = "Host $it", imageUrl = "") }

    @Composable
    private fun screen() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                Box(Modifier.requiredSize(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    OnboardingSearchScreen(
                        query = query.value,
                        results = if (query.value.isEmpty()) emptyList() else shows,
                        isSearching = false,
                        subscribedIds = picked.value,
                        selectedPodcasts = shows.associateBy { it.id },
                        onQueryChange = { query.value = it },
                        onSubscribe = { show ->
                            toggles += show.id
                            picked.value = if (show.id in picked.value) picked.value - show.id else picked.value + show.id
                        },
                        onBack = {},
                        onDone = { done++ },
                        popularPodcasts = shows,
                        isPopularLoading = loading.value,
                        selectedSearchGenre = genre.value,
                        onGenreSelect = { genre.value = it },
                    )
                }
            }
        }
    }

    private fun render(insets: SystemInsets? = null) {
        WindowCompat.setDecorFitsSystemWindows(compose.activity.window, false)
        compose.setContent { screen() }
        insets?.let { compose.runOnUiThread { applyFakeSystemInsets(compose.activity, it) } }
        compose.waitForIdle()
    }

    private fun resize(next: BoxloreWindowLayout) {
        compose.runOnIdle { window.value = next }
        compose.waitForIdle()
    }

    private val chipsInGrid get() = compose.onAllNodes(hasTestTag(SEARCH_CATEGORIES_TAG) and hasAnyAncestor(hasTestTag(SEARCH_GRID_TAG))).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the topic chips scroll with the shows only where the grid would get under one and a half rows`() {
        // Pinned on phones and the open foldable; the cover with a pick and sideways phones let the chips scroll away.
        assertFalse(onboardingSearchHeaderCompact(412f, 915f, 2, hasPicks = true))
        assertFalse(onboardingSearchHeaderCompact(360f, 800f, 2, hasPicks = true))
        assertFalse(onboardingSearchHeaderCompact(720f, 650f, 4, hasPicks = true))
        assertTrue(onboardingSearchHeaderCompact(443f, 649f, 2, hasPicks = true))

        picked.value = setOf("1")
        genre.value = "Comedy"
        render()
        assertFalse("the chips scroll away on a phone", chipsInGrid)
        listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.COVER_443, BoxloreTestWindows.PHONE).forEach { next ->
            resize(next)
            val short = next.heightDp < 480f || next == BoxloreTestWindows.COVER_443
            assertEquals("chips in the grid at ${next.widthDp}x${next.heightDp}", short, chipsInGrid)
            // The picked topic and the pick survive each change.
            compose.onNode(hasText("Comedy") and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertExists()
            compose.onNodeWithTag(SEARCH_PICKS_BUTTON_TAG).assertIsDisplayed()
            // At least the start of a second row of shows is on screen above the bar.
            val bar = compose.onNodeWithTag(SEARCH_PICKS_BAR_TAG).fetchSemanticsNode().boundsInRoot
            val secondRow = compose.onNode(hasText("Show 3", substring = false) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
                .fetchSemanticsNode().boundsInRoot
            assertTrue("no second row of shows at ${next.widthDp}x${next.heightDp}", next.widthDp > 600f || secondRow.top < bar.top)
        }
    }

    @Test
    fun `typed results scroll clear of the keyboard, and the picks bar waits under it`() {
        picked.value = setOf("1")
        query.value = "true crime"
        render(SystemInsets(statusTop = 24, navBottom = 24, imeBottom = 300))
        compose.onNodeWithTag(SEARCH_PICKS_BAR_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SEARCH_RESULTS_TAG).performScrollToIndex(shows.lastIndex)
        compose.waitForIdle()
        val last = compose.onNode(hasText("Show 30") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNode().boundsInRoot
        assertTrue("the last result is under the keyboard (${last.bottom})", last.bottom <= window.value.heightDp - 300f + 1f)
        // The keyboard closes: the bar comes back.
        compose.runOnUiThread { applyFakeSystemInsets(compose.activity, SystemInsets(statusTop = 24, navBottom = 24)) }
        compose.waitForIdle()
        compose.onNodeWithTag(SEARCH_PICKS_BAR_TAG).assertIsDisplayed()
    }

    @Test
    fun `the picks open in a sheet where one tap removes a show, and Continue fires once`() {
        picked.value = setOf("1", "2", "3", "4")
        render()
        compose.onNodeWithTag(SEARCH_PICKS_BUTTON_TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Your picks (4)")))
        compose.onNodeWithTag(SEARCH_PICKS_BUTTON_TAG).performClick()
        compose.waitForIdle()
        val rows = compose.onAllNodes(hasAnyAncestor(hasTestTag(SEARCH_PICKS_SHEET_TAG)) and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        assertEquals(4, rows.fetchSemanticsNodes().size)
        // The sheet stays open through a turn to a tablet and back.
        resize(BoxloreTestWindows.TABLET)
        resize(BoxloreTestWindows.PHONE)
        compose.onNodeWithTag(SEARCH_PICKS_SHEET_TAG).assertExists()
        compose.onNode(hasAnyAncestor(hasTestTag(SEARCH_PICKS_SHEET_TAG)) and hasText("Show 2")).performClick()
        compose.waitForIdle()
        assertEquals(listOf("2"), toggles)
        assertEquals(setOf("1", "3", "4"), picked.value)
        compose.onNodeWithText("Your picks (3)").assertExists()
        compose.onNodeWithText("Continue with 3").performClick()
        assertEquals(1, done)
    }

    @Test
    fun `each show is one checkbox that toggles once`() {
        render()
        val poster = compose.onNode(hasText("Show 2") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
        poster.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        poster.performClick()
        compose.waitForIdle()
        assertEquals(listOf("2"), toggles)
        poster.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        // No nested buttons: the badge isn't a second focus stop.
        assertTrue(compose.onAllNodes(hasText("Show 2") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNodes().size == 1)
    }

    @Test
    fun `the trending grid loads into skeletons of its own shape`() {
        loading.value = true
        render()
        assertEquals(6, compose.onAllNodesWithTag(SEARCH_SKELETON_TAG).fetchSemanticsNodes().size)
        val skeleton = compose.onAllNodesWithTag(SEARCH_SKELETON_TAG).fetchSemanticsNodes().first().boundsInRoot.height
        compose.runOnIdle { loading.value = false }
        compose.waitForIdle()
        assertEquals(0, compose.onAllNodesWithTag(SEARCH_SKELETON_TAG).fetchSemanticsNodes().size)
        val poster = compose.onNode(hasText("Show 1") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).fetchSemanticsNode().boundsInRoot.height
        assertEquals(poster, skeleton, 2f)
    }

    @Test
    fun `the results keep their place through recreation`() {
        // Regression: after a fold or display-size change the restored list counted as new results and jumped to the top.
        query.value = "true crime"
        val restore = StateRestorationTester(compose)
        restore.setContent { screen() }
        compose.onNodeWithTag(SEARCH_RESULTS_TAG).performScrollToIndex(20)
        compose.waitForIdle()
        restore.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNode(hasText("Show 21") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertIsDisplayed()
        compose.onNode(hasText("Show 1") and SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)).assertDoesNotExist()
    }
}
