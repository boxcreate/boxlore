package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
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
import org.robolectric.annotation.GraphicsMode

/** Each hero style loads on its own skeleton, laid out where its card's buttons land. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeroSkeletonCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val style = mutableStateOf(HeroCardStyle.BLOCK)

    private val item = SmartHeroItem(
        HeroType.RESUME,
        TestFixtures.podcast(id = "show-a", title = "Show a", imageUrl = "").copy(
            latestEpisode = TestFixtures.episode(id = "a", title = "Episode a", podcastId = "show-a"),
            episodeStatus = EpisodeStatus.IN_PROGRESS,
            resumeProgress = 0.4f,
        ),
        "Resume",
        description = "Episode a",
    )

    @Test fun `every hero style has a skeleton of its own`() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHeroCardStyle provides style.value) {
                    HeroSkeleton(Modifier.width(360.dp))
                }
            }
        }
        HeroCardStyle.entries.forEach { entry ->
            style.value = entry
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(skeletonTag(entry))
                .assertExists("$entry loads on another style's skeleton; give it its own in HeroSkeletonCard")
        }
    }

    @Test fun `artwork style skeletons put the play pair where the cards do`() {
        style.value = HeroCardStyle.COVER
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Column {
                    Box(Modifier.size(320.dp, homeHeroHeight()).testTag("card")) {
                        when (style.value) {
                            HeroCardStyle.COVER -> CoverHeroCard(item, {}, {}, null, null, isPlaying = false)
                            HeroCardStyle.HALO -> HaloHeroCard(item, {}, {}, null, null, isPlaying = false)
                            else -> DuotoneHeroCard(item, {}, {}, null, null, isPlaying = false)
                        }
                    }
                    Box(Modifier.size(320.dp, homeHeroHeight()).testTag("skeleton")) { HeroSkeletonCard(style.value) }
                }
            }
        }
        listOf(HeroCardStyle.COVER, HeroCardStyle.DUOTONE, HeroCardStyle.HALO).forEach { entry ->
            style.value = entry
            composeRule.waitForIdle()
            val card = bounds("card")
            val play = bounds("home_hero_play")
            val skeleton = bounds("skeleton")
            val actions = bounds("home_hero_skeleton_actions")
            assertEquals("$entry play top", play.top - card.top, actions.top - skeleton.top, 1f)
            assertEquals("$entry play bottom", play.bottom - card.top, actions.bottom - skeleton.top, 1f)
            assertEquals("$entry play start", play.left - card.left, actions.left - skeleton.left, 1f)
        }
    }

    @Test fun `Block's skeleton follows its card into the short body on short windows and back`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    Column {
                        Box(Modifier.size(320.dp, homeHeroHeight()).testTag("card")) { HeroCard(item, {}, {}, {}) }
                        Box(Modifier.size(320.dp, homeHeroHeight()).testTag("skeleton")) { HeroSkeletonCard(HeroCardStyle.BLOCK) }
                    }
                }
            }
        }
        for (layout in listOf(BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.PHONE)) {
            window.value = layout
            composeRule.waitForIdle()
            val label = "${layout.widthDp}x${layout.heightDp}"
            val short = layout.heightDp < 480f
            val skeletonArtwork = composeRule.onAllNodesWithTag("home_hero_skeleton_artwork", useUnmergedTree = true).fetchSemanticsNodes()
            assertEquals("$label: the skeleton's short body", short, skeletonArtwork.isNotEmpty())
            val actions = bounds("home_hero_skeleton_actions")
            assertTrue("$label: skeleton actions are ${actions.height}dp tall", actions.height >= 48f - 0.5f)
            if (short) {
                val card = bounds("card")
                val skeleton = bounds("skeleton")
                val artwork = bounds("home_hero_artwork")
                val placeholder = skeletonArtwork.first().boundsInRoot
                assertEquals("$label artwork start", artwork.left - card.left, placeholder.left - skeleton.left, 1f)
                assertEquals("$label artwork top", artwork.top - card.top, placeholder.top - skeleton.top, 1f)
                assertEquals("$label artwork size", artwork.width, placeholder.width, 1f)
                assertEquals("$label play top", bounds("home_hero_play").top - card.top, actions.top - skeleton.top, 1f)
            }
        }
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}
