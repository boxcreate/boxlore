package cx.aswin.boxlore.feature.briefing

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.BriefingSource
import cx.aswin.boxlore.core.model.Chapter
import java.io.File
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
@Config(sdk = [33], qualifiers = "w400dp-h2200dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BriefingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val playback = mutableListOf<Long?>()
    private val seeks = mutableListOf<Long>()
    private var region = ""
    private var defaultRegion = ""
    private lateinit var view: View
    private var pageBackground = 0
    private val pageVisible = mutableStateOf(true)
    private val briefing = Briefing(
        date = "2026-10-11",
        region = "global",
        title = "Energy markets, digital policy and the week ahead",
        script = "The first story’s reporting in full.\n\nThe second story’s reporting in full.",
        audioUrl = "https://example.invalid/audio",
        coverUrl = "",
        sources = listOf(BriefingSource("Original reporting", "https://example.invalid/story")),
    )

    private fun render(state: BriefingUiState, large: Boolean = false, dark: Boolean = large, phone: Boolean = false) {
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (large) 2f else 1f),
                LocalLayoutDirection provides if (large) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                    pageBackground = MaterialTheme.colorScheme.background.toArgb()
                    Box(Modifier.width(320.dp).then(if (phone) Modifier.height(720.dp) else Modifier).testTag("briefingViewport")) {
                        if (pageVisible.value) {
                            BriefingScreen(
                                uiState = state,
                                onBackClick = {},
                                onRegionSelect = { region = it },
                                onPlayPauseClick = { _, position -> playback.add(position) },
                                onSeekTo = { seeks.add(it) },
                                onEpisodeClick = {},
                                onFeedbackClick = {},
                                defaultEdition = "global",
                                onDefaultEditionSelect = { defaultRegion = it },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `light page cover reaches the status area and navigation leaves the logo clear`() {
        render(sampleEdition(), phone = true)
        assertCoverLayout()
        assertSolidPageBackground()
        assertFalse(statusIconsAreDark())
        writeScreenshot("light")
        scrollArticleToStory()
        assertTrue(statusIconsAreDark())
        writeScreenshot("light-story")
    }

    @Test
    fun `dark page keeps the same cover composition and white status icons`() {
        render(sampleEdition(), dark = true, phone = true)
        assertCoverLayout()
        assertSolidPageBackground()
        assertFalse(statusIconsAreDark())
        writeScreenshot("dark")
        scrollArticleToStory()
        assertFalse(statusIconsAreDark())
        writeScreenshot("dark-story")
    }

    private fun sampleEdition() = BriefingUiState.Success(
        briefing,
        "global",
        duration = 180_000,
        chapters = listOf(Chapter(0.0, "Energy markets"), Chapter(60.0, "Digital policy")),
    )

    @Test
    fun `thin progress track keeps accessible seeking and remaining time on one row`() {
        render(sampleEdition().copy(currentPosition = 60_000, isPlaying = true))
        val seekbar = compose.onNodeWithTag("briefingSeekbar")
        seekbar.assertIsDisplayed().assertTouchHeightIsEqualTo(48.dp)
        val bounds = seekbar.getUnclippedBoundsInRoot()
        val remaining = compose.onNodeWithText("2m left").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(remaining.left >= bounds.right)
        assertEquals((bounds.top.value + bounds.bottom.value) / 2f, (remaining.top.value + remaining.bottom.value) / 2f, 0.5f)
        seekbar.performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(listOf(90_000L), seeks)
        assertTrue(playback.isEmpty())
    }

    @Test
    fun `thin progress track preserves seeking with enlarged RTL controls`() {
        render(sampleEdition(), large = true)
        compose.onNodeWithTag("briefingSeekbar").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        assertEquals(listOf(135_000L), seeks)
    }

    @Test
    fun `unknown duration has no seek control`() {
        render(sampleEdition().copy(duration = 0))
        compose.onNodeWithTag("briefingSeekbar").assertDoesNotExist()
    }

    @Test
    fun `leaving the cover restores the light theme status icons`() {
        render(sampleEdition(), phone = true)
        assertFalse(statusIconsAreDark())
        compose.runOnIdle { pageVisible.value = false }
        assertTrue(statusIconsAreDark())
    }

    private fun assertCoverLayout() {
        val cover = compose.onNodeWithTag("briefingPageCover").getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("briefingViewport").getUnclippedBoundsInRoot()
        val logo = compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val back = compose.onNodeWithTag("briefingBack").getUnclippedBoundsInRoot()
        assertEquals(viewport.top, cover.top)
        assertEquals(16f, logo.left.value - cover.left.value, 0.1f)
        assertTrue("Navigation must not push the masthead down", logo.top.value - cover.top.value < 48f)
        assertTrue("Navigation must stay outside the masthead", back.left >= logo.right)
    }

    private fun statusIconsAreDark(): Boolean = compose.runOnIdle {
        WindowCompat.getInsetsController((view.context as Activity).window, view).isAppearanceLightStatusBars
    }

    private fun assertSolidPageBackground() {
        val cover = compose.onNodeWithTag("briefingPageCover").getUnclippedBoundsInRoot()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals("Artwork must end at the cover rather than wash behind the article", pageBackground, bitmap.getPixel(cover.left.value.toInt() + 4, cover.bottom.value.toInt() + 24))
            bitmap.recycle()
        }
    }

    private fun scrollArticleToStory() {
        // Target the vertical article explicitly; a story also lives inside a horizontal pager.
        compose.onNodeWithTag("briefingArticle").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 450f) }
        compose.onNodeWithContentDescription("Play story: Energy markets").assertIsDisplayed().performClick()
        assertEquals(listOf<Long?>(0L), playback)
    }

    private fun writeScreenshot(name: String) {
        val directory = System.getenv("BOXLORE_UI_TEST_ARTIFACTS") ?: return
        val bounds = compose.onNodeWithTag("briefingViewport").getUnclippedBoundsInRoot()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val crop = Bitmap.createBitmap(bitmap, bounds.left.value.toInt(), bounds.top.value.toInt(), (bounds.right.value - bounds.left.value).toInt(), (bounds.bottom.value - bounds.top.value).toInt())
            File(directory, "boxlore-brief-$name.png").outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
            crop.recycle()
            bitmap.recycle()
        }
    }

    @Test
    fun `native playback story navigation and source controls remain independent`() {
        render(BriefingUiState.Success(briefing, "global", duration = 180_000, chapters = listOf(Chapter(0.0, "Energy markets"), Chapter(60.0, "Digital policy"))))
        compose.onNodeWithText("Listen to brief").performScrollTo().performClick()
        assertEquals(listOf<Long?>(null), playback)

        compose.onNodeWithContentDescription("Next story").performScrollTo().performClick()
        compose.onNodeWithText("Story 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play story: Digital policy").performScrollTo().performClick()
        assertEquals(listOf(null, 60_000L), playback)

        compose.onNodeWithContentDescription("Sources").performScrollTo().performClick()
        compose.onAllNodesWithText("Original reporting").onLast().assertIsDisplayed()
        compose.onNodeWithText("example.invalid").assertIsDisplayed()
    }

    @Test
    fun `successful edition remains usable in dark RTL with enlarged text`() {
        render(
            BriefingUiState.Success(
                briefing,
                "global",
                duration = 180_000,
            chapters = listOf(Chapter(0.0, "Energy markets"))
            ),
                large = true
        )
        compose.onNodeWithContentDescription("The boxlore brief", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Listen to brief").performScrollTo().performClick()
        assertEquals(listOf<Long?>(null), playback)
        compose.onNodeWithContentDescription("Play story: Energy markets").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Brief preferences").assertIsDisplayed()
    }

    @Test
    fun `default edition and retry stay usable in dark RTL with enlarged text`() {
        render(BriefingUiState.Error("Couldn’t load this edition.", "global"), large = true)
        compose.onNodeWithText("Try again").assertIsDisplayed().performClick()
        assertEquals("global", region)
        compose.onNodeWithContentDescription("Brief preferences").performClick()
        compose.onNodeWithText("Default Brief region").assertIsDisplayed()
        compose.onNodeWithText("India").performScrollTo().performClick()
        assertEquals("in", defaultRegion)
        assertEquals("global", region)
        compose.onNodeWithText("Done").performScrollTo().assertIsDisplayed()
    }
}
