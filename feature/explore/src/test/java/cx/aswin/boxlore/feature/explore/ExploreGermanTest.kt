package cx.aswin.boxlore.feature.explore

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows.TABLET
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Explore in German: its rule sets the locale, which a method-level qualifier can't override. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreGermanTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_280, 800, false, locale = "de"))

    @Test
    fun `genre tags follow the app's language`() {
        val trending = List(4) { TestFixtures.podcast(id = "$it", title = "Show $it", imageUrl = PhoneGoldenRule.ARTWORK_URL, genre = "Society & Culture") }
        fixture.prepare()
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides TABLET, LocalBoxlorePaneWidth provides TABLET.widthDp) {
                Box(Modifier.width(TABLET.widthDp.dp).height(TABLET.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        ExploreContent(ExploreUiState.Success(trending = trending, selectedTab = 0), onSearchQueryChanged = {}, onCategorySelected = {}, onPodcastClick = { _, _, _, _ -> }, onEpisodeClick = { _, _ -> }, onTabSelected = {}, onVibeSelected = { _, _ -> }, onClearVibe = {})
                    }
                }
            }
        }
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
        val german = fixture.compose.activity.getString(cx.aswin.boxlore.core.catalog.R.string.auto_genre_society)
        assertEquals("Gesellschaft", german)
        assertTrue(fixture.compose.onAllNodesWithText(german, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("no English genre name", 0, fixture.compose.onAllNodesWithText("Society", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
