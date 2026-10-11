package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w420dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomePureMixSurfaceTest {
    @get:Rule val composeRule = createComposeRule()
    private val dark = mutableStateOf(false)
    private val playing = mutableStateOf(false)
    private val empty = mutableStateOf(false)
    private val surfaceStyle = mutableStateOf(SurfaceStyles.DYNAMIC_OLED_WHITE)
    private lateinit var view: View
    private lateinit var colors: ColorScheme

    @Test
    fun `Pure panel and resting cards have separate solid fills without an edge stroke`() {
        render()
        for (isDark in listOf(false, true)) {
            composeRule.runOnIdle { dark.value = isDark }
            composeRule.waitForIdle()
            val panel = "home_your_shows_surface"
            assertNotEquals(colors.background.toArgb(), colors.surfaceContainer.toArgb())
            assertEquals(colors.surfaceContainer.toArgb(), pixel(panel, 0.002f, 0.35f))
            assertEquals(colors.surfaceContainerHigh.toArgb(), pixel("home_mix_episode_episode-0", 0.015f, 0.5f))
            assertNotEquals(pixel(panel, 0.002f, 0.35f), pixel("home_mix_episode_episode-0", 0.015f, 0.5f))
            composeRule.runOnIdle { playing.value = true }
            assertEquals(colors.primaryContainer.toArgb(), pixel("home_mix_episode_episode-0", 0.015f, 0.5f))
            composeRule.runOnIdle { playing.value = false }
        }
    }

    @Test
    fun `caught up state retains the Pure panel without a border`() {
        empty.value = true
        render()
        for (isDark in listOf(false, true)) {
            composeRule.runOnIdle { dark.value = isDark }
            composeRule.waitForIdle()
            assertEquals(colors.surfaceContainer.toArgb(), pixel("home_your_shows_surface", 0.002f, 0.5f))
            composeRule.onNodeWithTag("home_mix_episode_episode-0").assertDoesNotExist()
        }
    }

    @Test
    fun `Classic and Material cards stay solid and separate from the module in both modes`() {
        render()
        for (style in listOf(SurfaceStyles.CLASSIC_DYNAMIC, SurfaceStyles.STANDARD)) {
            for (isDark in listOf(false, true)) {
                composeRule.runOnIdle {
                    surfaceStyle.value = style
                    dark.value = isDark
                }
                composeRule.waitForIdle()
                val cardColor = pixel("home_mix_episode_episode-0", 0.015f, 0.5f)
                assertEquals(colors.surfaceContainerHigh.toArgb(), cardColor)
                assertNotEquals(pixel("home_your_shows_surface", 0.002f, 0.35f), cardColor)
                composeRule.runOnIdle { playing.value = true }
                assertEquals(colors.primaryContainer.toArgb(), pixel("home_mix_episode_episode-0", 0.015f, 0.5f))
                composeRule.runOnIdle { playing.value = false }
            }
        }
    }

    private fun render() {
        val shows = List(2) { index ->
            TestFixtures.podcast(id = "show-$index", imageUrl = "").copy(
                latestEpisode = TestFixtures.episode(id = "episode-$index", title = "Episode"),
            )
        }
        composeRule.setContent {
            view = LocalView.current
            BoxLoreTheme(darkTheme = dark.value, dynamicColor = false, surfaceStyle = surfaceStyle.value) {
                colors = MaterialTheme.colorScheme
                Box(Modifier.width(380.dp).height(800.dp).background(colors.background)) {
                    YourShowsSection(
                        subscribedPodcasts = StablePodcastList(shows),
                        latestEpisodes = StablePodcastList(if (empty.value) emptyList() else shows),
                        selectedPodcastId = null,
                        selectedPodcastEpisodes = StableEpisodeList(emptyList()),
                        isSelectedPodcastLoading = false,
                        isSelectedRssRefreshing = false,
                        episodePlaybackState = StablePlaybackStateMap(emptyMap()),
                        currentPlayingEpisodeId = "episode-0",
                        isPlaying = playing.value,
                        onPodcastSelected = {},
                        onPodcastClick = {},
                        onEpisodeClick = { _, _, _ -> },
                        onPlayMix = {},
                        onMixModeChanged = {},
                        selectedMixMode = HomeMixMode.DAILY,
                        onPlayEpisode = { _, _, _ -> },
                        onViewLibrary = {},
                        onViewDownloads = {},
                    )
                }
            }
        }
    }

    private fun pixel(tag: String, x: Float, y: Float): Int {
        val bounds = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            bitmap.getPixel((bounds.left + bounds.width * x).toInt(), (bounds.top + bounds.height * y).toInt())
                .also { bitmap.recycle() }
        }
    }
}
