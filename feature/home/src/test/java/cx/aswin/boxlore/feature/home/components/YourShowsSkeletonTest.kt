package cx.aswin.boxlore.feature.home.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePlaybackStateMap
import cx.aswin.boxlore.feature.home.StablePodcastList
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w420dp-h1000dp-mdpi")
class YourShowsSkeletonTest {
    @get:Rule val composeRule = createComposeRule()

    /** A selected show used to load as a spinner in a 200dp box, then jump to the height of its rows. */
    @Test fun `the selected show's loading rows are as tall as its loaded rows and See all`() {
        val scale = mutableStateOf(1f)
        val width = mutableStateOf(380)
        val episodes = List(4) { TestFixtures.episode(id = "e$it", title = "Episode $it with a title long enough for two lines") }
        val podcast = TestFixtures.podcast()
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.value)) {
                MaterialTheme {
                    ShimmerScope(active = false) {
                        Column(Modifier.width(width.value.dp)) {
                            DenseEpisodeRowSkeleton(rows = 4, modifier = Modifier.testTag("skeleton"))
                            Column(Modifier.testTag("loaded"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                episodes.forEach { DenseEpisodeRow(it, podcast, DenseEpisodeRowActions({}, {})) }
                                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp))
                            }
                        }
                    }
                }
            }
        }
        // Phones and the Fold 8 cover's card widths, at normal and 130% text.
        for ((cardWidth, textScale) in listOf(380 to 1f, 380 to 1.3f, 443 to 1f, 443 to 1.3f)) {
            width.value = cardWidth
            scale.value = textScale
            composeRule.waitForIdle()
            val skeleton = composeRule.onNodeWithTag("skeleton").fetchSemanticsNode().boundsInRoot.height
            val loaded = composeRule.onNodeWithTag("loaded").fetchSemanticsNode().boundsInRoot.height
            assertEquals("${cardWidth}dp at ${textScale}x", loaded, skeleton, 1f)
        }
    }

    @Test fun `unknown count reserves both rows with visible cover placeholders`() {
        verifyPlaceholderRows(subscribedCount = 0, secondCount = 5)
    }

    @Test fun `known count matches artwork-only selectors in two rows`() {
        verifyPlaceholderRows(subscribedCount = 8, secondCount = 4)
    }

    @Test fun `landscape skeleton grows with text and leaves the next card visible`() {
        val scale = mutableStateOf(1f)
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, scale.value)) {
                MaterialTheme {
                    ShimmerScope(active = false) { YourShowsSkeleton(3) }
                }
            }
        }
        for (value in listOf(1f, 1.6f, 2f)) {
            scale.value = value
            composeRule.waitForIdle()
            val first = bounds("home_mix_skeleton_0")
            val second = bounds("home_mix_skeleton_1")
            assertEquals(HomeMixLayout.CardHeight.value * value * density, first.height, 1f)
            assertTrue(second.left > first.right && second.width > 0f)
        }
    }

    /**
     * The loading Your shows used a plain bar and a small dot for its heading, which jumped when the real
     * heading arrived, and on tablets without the side picker it drew more covers a row than the loaded
     * picker's five. Both now take the loaded shape at every size and text scale.
     */
    @Test
    @Config(qualifiers = "w1700dp-h2400dp-mdpi")
    fun `loading Your shows has the loaded heading and covers a row on phones and tablets`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val scale = mutableFloatStateOf(1f)
        val shows = List(8) { TestFixtures.podcast(id = "show_$it", title = "Show $it") }
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalDensity provides Density(1f, scale.floatValue)) {
                BoxLoreTheme(dynamicColor = false) {
                    ShimmerScope(active = false) {
                        val width = (window.value.widthDp - 32f).dp
                        Column {
                            Box(Modifier.requiredWidth(width).testTag("skeleton_box")) { YourShowsSkeleton(shows.size) }
                            Box(Modifier.requiredWidth(width).testTag("loaded_box")) { LoadedYourShows(shows) }
                        }
                    }
                }
            }
        }
        val windows = listOf(BoxloreTestWindows.PHONE_SMALL, BoxloreTestWindows.PHONE, BoxloreWindowLayout(600f, 960f), BoxloreTestWindows.PIXEL_FOLD, BoxloreTestWindows.TABLET)
        for (size in windows) {
            for (textScale in listOf(1f, 1.3f)) {
                window.value = size
                scale.floatValue = textScale
                composeRule.waitForIdle()
                val label = "${size.widthDp}x${size.heightDp} at ${textScale}x"
                val skeletonBox = bounds("skeleton_box")
                val loadedBox = bounds("loaded_box")
                val skeletonHeader = bounds("home_your_shows_header_skeleton").relativeTo(skeletonBox)
                val loadedHeader = bounds("home_your_shows_header").relativeTo(loadedBox)
                assertEquals("$label heading height", loadedHeader.height, skeletonHeader.height, 1f)
                assertEquals("$label heading width", loadedHeader.width, skeletonHeader.width, 1f)
                val skeletonAction = bounds("home_your_shows_header_skeleton_action").relativeTo(skeletonBox)
                val loadedAction = composeRule.onNodeWithContentDescription("View library", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.relativeTo(loadedBox)
                assertEquals("$label browse button x", loadedAction.center.x, skeletonAction.center.x, 1f)
                assertEquals("$label browse button y", loadedAction.center.y, skeletonAction.center.y, 1f)
                val skeletonRow = firstRow("home_show_skeleton")
                val loadedRow = firstRow("home_show_selector_")
                assertEquals("$label covers a row", loadedRow.size, skeletonRow.size)
                assertEquals("$label cover width", loadedRow.first().width, skeletonRow.first().width, 1f)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LoadedYourShows(shows: List<cx.aswin.boxlore.core.model.Podcast>) {
        YourShowsSection(
            subscribedPodcasts = StablePodcastList(shows),
            latestEpisodes = StablePodcastList(shows),
            selectedPodcastId = null,
            selectedPodcastEpisodes = StableEpisodeList(emptyList()),
            isSelectedPodcastLoading = false,
            isSelectedRssRefreshing = false,
            episodePlaybackState = StablePlaybackStateMap(emptyMap()),
            onPodcastSelected = {},
            onPodcastClick = {},
            onEpisodeClick = { _, _, _ -> },
            onPlayMix = {},
            onMixModeChanged = {},
            selectedMixMode = HomeMixMode.DAILY,
            onPlayEpisode = { _, _, _ -> },
            onViewLibrary = {},
            onViewDownloads = {},
            wideWithoutBrief = true,
        )
    }

    /** Bounds of the first row of nodes whose test tag starts with [prefix]. */
    private fun firstRow(prefix: String): List<Rect> {
        val tiles = composeRule.onAllNodes(SemanticsMatcher("tag starts with $prefix") { node -> node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true }, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        val top = tiles.minOf { it.top }
        return tiles.filter { kotlin.math.abs(it.top - top) < 1f }
    }

    private fun Rect.relativeTo(container: Rect): Rect = translate(-container.left, -container.top)

    private fun verifyPlaceholderRows(subscribedCount: Int, secondCount: Int) {
        lateinit var view: View
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme(colorScheme = darkColorScheme(onSurface = Color.White)) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    ShimmerScope(active = false) { YourShowsSkeleton(subscribedCount, Modifier.testTag("skeleton")) }
                }
            }
        }
        val covers = (0..1).flatMap { row ->
            (0 until if (row == 0) 5 else secondCount).map { column -> bounds("home_show_skeleton_${row}_$column") }
        }
        val section = bounds("skeleton")
        assertTrue(covers.all { it.left >= section.left && it.right <= section.right })
        assertTrue(covers[5].top > covers[0].bottom)
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                covers.forEach { cover ->
                    assertNotEquals("Visible cover backing", Color.Black.toArgb(), bitmap.getPixel(cover.center.x.toInt(), (cover.top + cover.width / 2).toInt()))
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
}
