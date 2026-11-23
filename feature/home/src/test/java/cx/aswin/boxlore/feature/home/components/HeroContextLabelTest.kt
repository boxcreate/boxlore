package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hero's state label ("Resume", "New episode") used to shrink to 8sp with long words or large text. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
class HeroContextLabelTest {
    @get:Rule val composeRule = createComposeRule()
    private val width = mutableIntStateOf(320)
    private val scale = mutableFloatStateOf(1f)

    @Test
    fun `the state label never shrinks below 11sp and stays on one line`() {
        val show = TestFixtures.podcast(id = "show", title = "Show").copy(latestEpisode = TestFixtures.episode(id = "e", title = "Episode", podcastId = "show").copy(duration = 3000))
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.floatValue)) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(width.intValue.dp).height(homeHeroHeight())) {
                        HeroCard(SmartHeroItem(HeroType.SPOTLIGHT, show, "RETOMA DONDE LO DEJASTE AHORA", "Episode"), {}, {}, {})
                    }
                }
            }
        }
        for (cardWidth in listOf(288, 320, 380)) {
            for (textScale in listOf(1f, 1.3f)) {
                width.intValue = cardWidth
                scale.floatValue = textScale
                composeRule.waitForIdle()
                val results = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithTag("home_hero_context", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val label = results.single()
                assertEquals("${cardWidth}dp at ${textScale}x", 1, label.lineCount)
                assertTrue("${cardWidth}dp at ${textScale}x: ${label.layoutInput.style.fontSize}", label.layoutInput.style.fontSize.value >= 11f - 0.01f)
            }
        }
    }
}
