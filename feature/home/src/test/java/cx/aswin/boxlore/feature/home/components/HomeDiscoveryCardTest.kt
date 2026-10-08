package cx.aswin.boxlore.feature.home.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCard
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardSkeleton
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePodcastList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDiscoveryCardTest {
    @get:Rule val composeRule = createComposeRule()

    private data class Scenario(
        val width: Dp = 156.dp,
        val fontScale: Float = 1f,
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val title: String = "Short",
        val presentation: FeedMediaCardPresentation = FeedMediaCardPresentation.ExpressivePoster,
        val dark: Boolean = false,
        val surface: String = SurfaceStyles.CLASSIC_DYNAMIC,
        val dynamic: Boolean = false,
    )

    private val scenario = mutableStateOf(Scenario())
    private var density = 1f
    private var containerColor = Color.Unspecified
    private lateinit var view: View

    @Test fun `poster centers shorter titles within three reserved lines and matches skeleton at phone widths and enlarged text`() {
        render()
        for (screenWidth in listOf(320, 360, 412)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                for (direction in LayoutDirection.entries) {
                    scenario.value = Scenario(width = ((screenWidth - 48) / 2f).dp, fontScale = scale, direction = direction)
                    composeRule.waitForIdle()
                    assertMatchingHeights()
                    val card = composeRule.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot
                    val text = composeRule.onNodeWithText("Short", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val artworkBottom = card.top + scenario.value.width.value * density
                    assertEquals((artworkBottom + card.bottom) / 2f, text.center.y, 1f)
                    assertTrue(text.top > artworkBottom + 12f * density)
                    assertEquals(text.top - artworkBottom - 12f * density, card.bottom - text.bottom - 12f * density, 1f)
                    assertFalse(titleLayout().didOverflowHeight)
                }
            }
        }
    }

    @Test fun `long titles ellipsize at line three in LTR and RTL`() {
        render()
        val variants = listOf(FeedMediaCardPresentation.ExpressivePoster, FeedMediaCardPresentation.ExpressiveFeatured)
            .flatMap { presentation -> LayoutDirection.entries.map { Scenario(width = 288.dp, presentation = presentation, direction = it) } }
        for (value in variants) {
            scenario.value = value.copy(title = "A detailed episode title with many words ".repeat(12), fontScale = 2f)
            composeRule.waitForIdle()
            val layout = titleLayout()
            assertEquals(3, layout.lineCount)
            assertTrue(layout.isLineEllipsized(2))
            assertMatchingHeights()
        }
    }

    @Test fun `featured recommendation matches its skeleton and scales without clipping`() {
        render()
        val variants = listOf(288.dp, 328.dp, 380.dp).map { Scenario(width = it, presentation = FeedMediaCardPresentation.ExpressiveFeatured) }
            .flatMap { value -> listOf(1f, 2f).map { value.copy(fontScale = it) } }
            .flatMap { value -> LayoutDirection.entries.map { value.copy(direction = it) } }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            assertMatchingHeights()
            assertFalse(titleLayout().didOverflowHeight)
            assertEquals(16f, titleLayout().layoutInput.style.fontSize.value, 0f)
            val card = composeRule.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot
            if (value.fontScale == 1f) assertEquals("Featured cards stay 152dp tall at normal text size", 152f * density, card.height, 1f)
            val text = composeRule.onNodeWithText("Short", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= card.left && text.right <= card.right)
            if (value.direction == LayoutDirection.Ltr) {
                assertEquals(card.left + 16f * density, text.left, 1f)
                assertTrue(text.right <= card.right - card.width * 0.44f - 16f * density)
            } else {
                assertEquals(card.right - 16f * density, text.right, 1f)
                assertTrue(text.left >= card.left + card.width * 0.44f + 16f * density)
            }
        }
    }

    @Test fun `featured artwork reaches the end edge with a sweeping curved join mirrored in RTL`() {
        composeRule.setContent {
            val value = scenario.value
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalLayoutDirection provides value.direction) {
                BoxLoreTheme(dynamicColor = false) {
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    FeedMediaCardSkeleton(
                        Modifier.width(328.dp).testTag("featured_curve"),
                        presentation = FeedMediaCardPresentation.ExpressiveFeatured,
                    ) { Box(it.background(Color.Magenta)) }
                }
            }
        }
        for (direction in LayoutDirection.entries) {
            scenario.value = Scenario(direction = direction)
            composeRule.waitForIdle()
            val bounds = composeRule.onNodeWithTag("featured_curve").fetchSemanticsNode().boundsInRoot
            val pixels = composeRule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                fun endX(offset: Float) = if (direction == LayoutDirection.Ltr) bounds.right - offset else bounds.left + offset
                val joinX = endX(bounds.width * 0.44f - 4f * density).toInt()
                listOf(
                    bitmap.getPixel(endX(2f * density).toInt(), bounds.center.y.toInt()),
                    bitmap.getPixel(joinX, (bounds.top + 4f * density).toInt()),
                    bitmap.getPixel(joinX, bounds.center.y.toInt()),
                ).also { bitmap.recycle() }
            }
            assertEquals(listOf(Color.Magenta.toArgb(), containerColor.toArgb(), Color.Magenta.toArgb()), pixels)
        }
    }

    @Test fun `default presentation retains the existing fixed title foot`() {
        scenario.value = Scenario(presentation = FeedMediaCardPresentation.Default)
        render()
        val card = composeRule.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot
        assertEquals((156f + 78f) * density, card.height, 1f)
    }

    @Test fun `seed retains original content and softly clipped body shapes with enlarged text and RTL`() {
        val title = "A show with a very long name that needs to be ellipsized across the available title space"
        var upperDecorationColor = Color.Unspecified
        var lowerDecorationColor = Color.Unspecified
        scenario.value = Scenario(width = 288.dp, title = title)
        composeRule.setContent {
            val value = scenario.value
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, value.fontScale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(darkTheme = value.dark, dynamicColor = value.dynamic, surfaceStyle = value.surface) {
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                    upperDecorationColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f).compositeOver(containerColor)
                    lowerDecorationColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f).compositeOver(containerColor)
                    BecauseYouLikeSection(
                        podcast = Podcast(id = "seed", title = value.title, artist = "", imageUrl = ""),
                        recommendations = StableEpisodeList(emptyList()),
                        suggestedPodcasts = StablePodcastList(emptyList()),
                        currentPlayingEpisodeId = null,
                        isPlaying = false,
                        onEpisodeClick = { _, _ -> },
                        onPlayEpisode = { _, _ -> },
                        onPodcastClick = {},
                        onChangePodcastClick = {},
                        modifier = Modifier.width(value.width).testTag("seed"),
                    )
                }
            }
        }
        val themes = listOf(Scenario(), Scenario(dark = true), Scenario(dark = true, surface = SurfaceStyles.AMOLED), Scenario(dynamic = true))
        val variants = themes
            .flatMap { theme -> listOf(288.dp, 328.dp).map { theme.copy(width = it, title = title) } }
            .flatMap { value -> listOf(1f, 2f).map { value.copy(fontScale = it) } }
            .flatMap { value -> LayoutDirection.entries.map { value.copy(direction = it) } }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val card = composeRule.onNodeWithTag("seed").fetchSemanticsNode().boundsInRoot
            val text = composeRule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val action = composeRule.onNodeWithContentDescription(ApplicationProvider.getApplicationContext<Context>().getString(R.string.home_change_recommendation_show))
                .fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= card.left && text.right <= card.right && text.bottom <= card.bottom)
            assertFalse(text.overlaps(action))
            assertEquals("The original swap button stays 40dp wide", 40f * density, action.width, 1f)
            assertEquals("The original swap button stays 40dp tall", 40f * density, action.height, 1f)
            val labelText = ApplicationProvider.getApplicationContext<Context>().getString(R.string.home_because_you_like)
            val label = composeRule.onNodeWithText(labelText, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(label.bottom <= text.top)
            assertEquals(2, titleLayout().lineCount)
            assertTrue(titleLayout().isLineEllipsized(1))
            assertTrue("The original seed reserves its 88dp minimum height", card.height >= 88f * density)
            assertEquals("The original show title uses titleMedium", 16.sp, titleLayout().layoutInput.style.fontSize)
            val pixels = composeRule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                fun bodyX(offset: Float): Int = if (value.direction == LayoutDirection.Ltr) {
                    (card.left + offset).toInt()
                } else {
                    (card.right - offset).toInt()
                }
                listOf(
                    bitmap.getPixel(bodyX(3 * density), card.center.y.toInt()),
                    bitmap.getPixel(bodyX(card.width - 38 * density), (card.top + 4 * density).toInt()),
                    bitmap.getPixel(bodyX(maxOf(156 * density, card.width * 0.52f)), (card.bottom - 4 * density).toInt()),
                ).also { bitmap.recycle() }
            }
            assertEquals("Decorations leave the artwork and emoji lane clear", containerColor.toArgb(), pixels[0])
            assertPixelColor(upperDecorationColor, pixels[1])
            assertPixelColor(lowerDecorationColor, pixels[2])
        }
    }

    @Test fun `filled card follows light dark dynamic and pure black theme surfaces`() {
        render()
        val themes = listOf(Scenario(), Scenario(dark = true), Scenario(dark = true, surface = SurfaceStyles.AMOLED), Scenario(dynamic = true))
        for (value in themes.flatMap { listOf(it, it.copy(presentation = FeedMediaCardPresentation.ExpressiveFeatured)) }) {
            scenario.value = value
            composeRule.waitForIdle()
            val bounds = composeRule.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot
            val pixel = composeRule.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                bitmap.getPixel(bounds.center.x.toInt(), (bounds.bottom - 10 * density).toInt()).also { bitmap.recycle() }
            }
            assertEquals(containerColor.toArgb(), pixel)
        }
    }

    @Test fun `poster artwork retains curved lower corners in the shared loading and loaded layout`() {
        composeRule.setContent {
            view = LocalView.current
            density = LocalDensity.current.density
            BoxLoreTheme(dynamicColor = false) {
                containerColor = MaterialTheme.colorScheme.surfaceContainer
                FeedMediaCardSkeleton(Modifier.width(156.dp).testTag("curve")) { Box(it.background(Color.Magenta)) }
            }
        }
        val bounds = composeRule.onNodeWithTag("curve").fetchSemanticsNode().boundsInRoot
        val pixels = composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val bottom = (bounds.top + 155 * density).toInt()
            listOf(
                bitmap.getPixel((bounds.left + density).toInt(), bottom),
                bitmap.getPixel(bounds.center.x.toInt(), bottom),
                bitmap.getPixel((bounds.right - density).toInt(), bottom),
            ).also { bitmap.recycle() }
        }
        assertEquals(listOf(containerColor.toArgb(), Color.Magenta.toArgb(), containerColor.toArgb()), pixels)
    }

    @Test fun `Because you like rails are smaller than editorial rails and both cap on wide screens`() {
        val viewport = mutableStateOf(328.dp)
        val compact = mutableStateOf(false)
        composeRule.setContent {
            density = LocalDensity.current.density
            BoxLoreTheme(dynamicColor = false) {
                HomeDiscoveryRail(Modifier.width(viewport.value), compact = compact.value) { width ->
                    item {
                        FeedMediaCard("", "Rail title", null, {}, modifier = Modifier.width(width).testTag("rail"), presentation = FeedMediaCardPresentation.ExpressivePoster)
                    }
                }
            }
        }
        assertEquals(171f * density, composeRule.onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width, 1f)
        compact.value = true
        composeRule.waitForIdle()
        assertEquals(138f * density, composeRule.onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width, 1f)
        viewport.value = 640.dp
        composeRule.waitForIdle()
        assertEquals(176f * density, composeRule.onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width, 1f)
        compact.value = false
        composeRule.waitForIdle()
        assertEquals(208f * density, composeRule.onNodeWithTag("rail").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test fun `time rails show a visibly partial second card at phone widths enlarged text and RTL`() {
        composeRule.setContent {
            val value = scenario.value
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, value.fontScale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    HomeDiscoveryRail(Modifier.width(value.width).testTag("time_rail")) { width ->
                        items(3) { index ->
                            FeedMediaCard(
                                "",
                                "Episode $index",
                                null,
                                {},
                                modifier = Modifier.width(width).testTag("time_card_$index"),
                                presentation = FeedMediaCardPresentation.ExpressivePoster,
                            )
                        }
                    }
                }
            }
        }
        val variants = listOf(288.dp, 328.dp, 380.dp).map { Scenario(width = it) }
            .flatMap { value -> listOf(1f, 2f).map { value.copy(fontScale = it) } }
            .flatMap { value -> LayoutDirection.entries.map { value.copy(direction = it) } }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val rail = composeRule.onNodeWithTag("time_rail").fetchSemanticsNode().boundsInRoot
            val first = composeRule.onNodeWithTag("time_card_0").fetchSemanticsNode().boundsInRoot
            val second = composeRule.onNodeWithTag("time_card_1").fetchSemanticsNode().boundsInRoot
            assertTrue("The next card must be visibly incomplete: $value", first.width - second.width >= 24f * density)
            assertTrue("Enough of the next artwork remains visible", second.width > first.width * 0.7f)
            assertEquals(first.height, second.height, 1f)
            if (value.direction == LayoutDirection.Ltr) {
                assertEquals(rail.right, second.right, 1f)
                assertEquals(16f * density, second.left - first.right, 1f)
            } else {
                assertEquals(rail.left, second.left, 1f)
                assertEquals(16f * density, first.left - second.right, 1f)
            }
        }
    }

    private fun render() {
        composeRule.setContent {
            val value = scenario.value
            view = LocalView.current
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, value.fontScale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(darkTheme = value.dark, dynamicColor = value.dynamic, surfaceStyle = value.surface) {
                    containerColor = if (value.presentation == FeedMediaCardPresentation.ExpressiveFeatured) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FeedMediaCard(
                            imageUrl = "",
                            title = value.title,
                            subtitle = null,
                            onClick = {},
                            titleMaxLines = 3,
                            presentation = value.presentation,
                            modifier = Modifier.width(value.width).testTag("real"),
                        )
                        HomeMediaCardSkeleton(
                            presentation = value.presentation,
                            modifier = Modifier.width(value.width).testTag("skeleton"),
                        )
                    }
                }
            }
        }
    }

    private fun titleLayout(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(scenario.value.title, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun assertMatchingHeights() {
        val real = composeRule.onNodeWithTag("real").fetchSemanticsNode().boundsInRoot
        val skeleton = composeRule.onNodeWithTag("skeleton").fetchSemanticsNode().boundsInRoot
        assertEquals(real.height, skeleton.height, 1f)
    }

    private fun assertPixelColor(expected: Color, actual: Int) {
        val expectedArgb = expected.toArgb()
        for (shift in listOf(0, 8, 16, 24)) {
            val expectedChannel = (expectedArgb shr shift) and 255
            val actualChannel = (actual shr shift) and 255
            assertTrue("Expected softly tinted color $expectedArgb but found $actual", kotlin.math.abs(expectedChannel - actualChannel) <= 1)
        }
    }
}
