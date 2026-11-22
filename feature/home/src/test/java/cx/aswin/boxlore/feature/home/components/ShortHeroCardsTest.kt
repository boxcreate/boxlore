package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
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

/**
 * Above a tabletop fold, on sideways phones and in split-screen halves the hero can be as short as
 * 240dp. Duotone and Halo stack "1h / 0m / left"
 * in big type on taller cards; there it squeezed Play into a sliver, so short cards use the one-line
 * context and tighter spacing. Every artwork style must keep a full Play button inside the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
class ShortHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val style = mutableStateOf(HeroCardStyle.COVER)

    private fun item(): SmartHeroItem {
        val episode = TestFixtures.episode(id = "a", title = "Episode a", podcastId = "show-a").copy(duration = 3600)
        val show = TestFixtures.podcast(id = "show-a", title = "Show a", imageUrl = "").copy(latestEpisode = episode)
        return SmartHeroItem(HeroType.RESUME, show, "Resume", description = "Episode a")
    }

    @Test
    fun `short artwork cards keep a full Play button`() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Box(Modifier.size(320.dp, 240.dp).testTag("short_hero")) {
                    when (style.value) {
                        HeroCardStyle.BLOCK -> CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE_LAND) {
                            HeroCard(item(), {}, {}, {})
                        }
                        HeroCardStyle.DUOTONE -> DuotoneHeroCard(item(), {}, {}, null, null, false)
                        HeroCardStyle.HALO -> HaloHeroCard(item(), {}, {}, null, null, false)
                        HeroCardStyle.TIDE -> TideHeroCard(item(), {}, {}, null, null, false)
                        else -> CoverHeroCard(item(), {}, {}, null, null, false)
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        listOf(HeroCardStyle.COVER, HeroCardStyle.TIDE, HeroCardStyle.DUOTONE, HeroCardStyle.HALO, HeroCardStyle.BLOCK).forEach { current ->
            style.value = current
            composeRule.waitForIdle()
            val hero = composeRule.onNodeWithTag("short_hero").fetchSemanticsNode().boundsInRoot
            val play = composeRule.onNodeWithTag("home_hero_play", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            if (play.height < 48f) failures += "$current Play is ${play.height}px tall"
            if (play.bottom > hero.bottom) failures += "$current Play ends at ${play.bottom}, card at ${hero.bottom}"
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    @Test
    fun `the hero fits short compact windows and keeps its full height on portrait phones`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val miniPlayer = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalBoxloreMiniPlayerVisible provides miniPlayer.value) {
                Box(Modifier.height(homeHeroHeight()).width(10.dp).testTag("hero_height"))
            }
        }
        fun height(): Float = composeRule.onNodeWithTag("hero_height").fetchSemanticsNode().boundsInRoot.height
        // Portrait phones keep the full hero in both directions of every change.
        for (layout in listOf(BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE_SMALL, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.PHONE)) {
            window.value = layout
            composeRule.waitForIdle()
            if (layout.heightDp >= 480f) {
                assertEquals("Hero at ${layout.widthDp}x${layout.heightDp}", 416f, height(), 0.5f)
            } else {
                // Short windows: at least 240dp and short enough that the top bar and the bottom bar with the mini player leave it room.
                assertTrue("Hero at ${layout.widthDp}x${layout.heightDp}: ${height()}", height() in 240f..(layout.heightDp - 80f - 12f).coerceAtLeast(240f))
                assertTrue("Hero at ${layout.widthDp}x${layout.heightDp} is shorter than the full hero", height() < 416f)
            }
        }
        // Starting playback never resizes the hero.
        window.value = BoxloreTestWindows.SPLIT_HALF
        composeRule.waitForIdle()
        val before = height()
        miniPlayer.value = true
        composeRule.waitForIdle()
        assertEquals(before, height(), 0.5f)
    }
}
