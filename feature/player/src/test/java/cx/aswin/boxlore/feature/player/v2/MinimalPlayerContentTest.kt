package cx.aswin.boxlore.feature.player.v2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.text.Layout
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.buildBoxLoreTypography
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlayerState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
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
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MinimalPlayerContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private lateinit var sharpTitleFamily: FontFamily
    private lateinit var appTitleFamily: FontFamily
    private var visibleHeight = 0.dp
    private val calls = mutableListOf<String>()
    private var sought = -1L
    private val position = MutableStateFlow(120_000L)
    private val title = "Freedom from the Known"
    private val state = PlayerState(
        currentEpisode = Episode("ep", title, "", "https://example.invalid/audio"),
        currentPodcast = Podcast("show", "Krishnamurti", "", ""),
        duration = 4_560_000L,
        seekBackwardMs = 15_000L,
        seekForwardMs = 45_000L,
    )

    private fun render(model: PlayerState = state, dark: Boolean = false, large: Boolean = false, oversizedSheet: Boolean = false) {
        compose.setContent {
            view = LocalView.current
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (large) 2f else 1f),
                LocalLayoutDirection provides if (large) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                BoxLoreTheme(darkTheme = dark) {
                    sharpTitleFamily = buildBoxLoreTypography(LocalContext.current, FontRoundness.AXIS_CRISP).headlineLarge.fontFamily!!
                    appTitleFamily = MaterialTheme.typography.headlineLarge.fontFamily!!
                    visibleHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val sheetModifier = if (oversizedSheet) {
                            Modifier.wrapContentSize(Alignment.TopCenter, unbounded = true).requiredSize(maxWidth, visibleHeight + 80.dp)
                        } else {
                            Modifier.fillMaxSize()
                        }
                        Box(sheetModifier) {
                            MinimalPlayerContent(
                                model,
                                FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true),
                                PlayerProgressFlows(position, MutableStateFlow(0L)),
                                MinimalPlayerActions(
                                    { calls.add("play") }, { calls.add("back") }, { calls.add("forward") },
                                    { calls.add("sleep") }, { calls.add("speed") }, { calls.add("queue") },
                                    { sought = it }, { calls.add("collapse") }, { calls.add("podcast") },
                                ),
                                Modifier,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `oversized animation canvas keeps the complete transport and queue inside the window`() {
        render(oversizedSheet = true)
        assertTrue(visibleHeight > 0.dp)
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Up next").assertIsDisplayed()
        val queue = compose.onNodeWithContentDescription("Up next").getUnclippedBoundsInRoot()
        assertTrue("Up next must remain above the window bottom", queue.bottom <= visibleHeight)
        writeImage("oversized-host")
    }

    @Test
    fun `show and episode heading is centered in the space above transport`() {
        render()
        val handle = compose.onNodeWithContentDescription("Minimize player").getUnclippedBoundsInRoot()
        val show = compose.onNodeWithText("KRISHNAMURTI").getUnclippedBoundsInRoot()
        val episode = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
        val time = compose.onNodeWithText("−1h 14m").getUnclippedBoundsInRoot()
        assertEquals((handle.bottom.value + time.top.value) / 2f, (show.top.value + episode.bottom.value) / 2f, 1f)
    }

    @Test
    fun `minimal typography uses crisp lettering without changing the app typography`() {
        render()
        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        val actual = episodeTitleView().typeface
        assertEquals(resolver.resolve(sharpTitleFamily, GoogleSansWeight.medium).value, actual)
        assertNotEquals(resolver.resolve(appTitleFamily, GoogleSansWeight.medium).value, actual)
    }

    @Test
    fun `short names grow and remaining time uses the larger single line size`() {
        render(state.copy(currentEpisode = state.currentEpisode?.copy(title = "Go"), currentPodcast = state.currentPodcast?.copy(title = "99pi")))
        val episode = episodeTitleView()
        val show = textLayout("99PI")
        val time = textLayout("−1h 14m")
        // Auto-size preserves the input style in semantics; verify the rendered glyph metrics.
        assertEquals(40f, episode.textSize, 1f)
        assertTrue(lineHeight(show) in 19f..23f)
        assertTrue(lineHeight(time) in 75f..83f)
        assertEquals(1, time.lineCount)
        compose.onNodeWithContentDescription("Up next").assertIsDisplayed()
        writeImage("short-names")
    }

    @Test
    fun `long names shrink within four episode and two show lines without covering controls`() {
        val episodeName = "Understanding the history of science and the extraordinary people who changed the way we see the universe and our place in it"
        val showName = "The surprisingly detailed weekly conversations about history science and the world around us"
        render(state.copy(currentEpisode = state.currentEpisode?.copy(title = episodeName), currentPodcast = state.currentPodcast?.copy(title = showName)))
        val episode = episodeTitleView()
        val show = textLayout(showName.uppercase())
        assertTrue(episode.textSize in 28f..39f)
        assertTrue(episode.lineCount <= 4)
        assertTrue(show.lineCount <= 2)
        assertTrue(lineHeight(show) / 1.25f in 11f..17f)
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Up next").assertIsDisplayed()
        compose.onNodeWithText(episodeName).performClick()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        writeImage("long-names")
    }

    private fun textLayout(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun episodeTitleView(): TextView = compose.runOnIdle { view.findViewWithTag(MINIMAL_EPISODE_TITLE_TAG) }

    @Test
    fun `hyphenation is limited to the episode heading and preserves its original title`() {
        val episodeName = "Understanding extraordinary internationalization"
        render(state.copy(currentEpisode = state.currentEpisode?.copy(title = episodeName)))
        val episode = episodeTitleView()
        assertEquals(Layout.HYPHENATION_FREQUENCY_FULL_FAST, episode.hyphenationFrequency)
        assertEquals(Layout.BREAK_STRATEGY_HIGH_QUALITY, episode.breakStrategy)
        assertEquals(episodeName, episode.text.toString())
        assertTrue(episode.lineCount <= 4)
        assertNotEquals(Hyphens.Auto, textLayout("KRISHNAMURTI").layoutInput.style.hyphens)
        assertNotEquals(Hyphens.Auto, textLayout("−1h 14m").layoutInput.style.hyphens)
        compose.onNodeWithText(episodeName).performClick()
        val expanded = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(episodeName) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(expanded) }
        assertNotEquals(Hyphens.Auto, expanded.single().layoutInput.style.hyphens)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Up next").assertIsDisplayed()
        writeImage("hyphenation")
    }

    @Test
    @Config(sdk = [31])
    fun `older supported Android versions retain full title hyphenation`() {
        render()
        assertEquals(Layout.HYPHENATION_FREQUENCY_FULL, episodeTitleView().hyphenationFrequency)
        compose.onNodeWithText(title).assertIsDisplayed().performClick()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    private fun lineHeight(layout: TextLayoutResult): Float = layout.getLineBottom(0) - layout.getLineTop(0)

    @Test
    fun `minimal transport uses global seeks and preserves distinct control and queue actions`() {
        render()
        compose.onNodeWithText("−1h 14m").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").performClick()
        compose.onNodeWithContentDescription("Seek back 15 seconds", useUnmergedTree = false).performClick()
        compose.onNodeWithContentDescription("Seek forward 45 seconds", useUnmergedTree = false).performClick()
        compose.onNodeWithContentDescription("Sleep timer").performClick()
        compose.onNodeWithContentDescription("Playback speed: 1×").performClick()
        compose.onNodeWithContentDescription("Up next").performClick()
        assertEquals(listOf("play", "back", "forward", "sleep", "speed", "queue"), calls)
        writeImage("light")
    }

    @Test
    fun `accessible seeking changes position while title expands without opening other controls`() {
        render()
        compose.onNodeWithContentDescription("Playback position").performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        assertEquals(state.duration / 2L, sought)
        compose.onNodeWithText(title).performClick()
        compose.onNodeWithText("Done").assertIsDisplayed().performClick()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `large RTL dark layout keeps all five controls visible and buffering disables play`() {
        render(state.copy(isLoading = true, playbackSpeed = 1.5f, sleepAtEndOfEpisode = true), dark = true, large = true)
        compose.onNodeWithContentDescription("Buffering").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Seek back 15 seconds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seek forward 45 seconds").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Playback speed: 1.5×").assertIsDisplayed()
        compose.onNodeWithContentDescription("Up next").assertIsDisplayed()
        writeImage("large-rtl-dark")
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun `short landscape viewport scrolls instead of covering controls with long titles`() {
        render(state.copy(currentEpisode = state.currentEpisode?.copy(title = "A very long episode title about science and curiosity ".repeat(8))))
        compose.onNodeWithContentDescription("Up next").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf("queue"), calls)
    }

    @Test
    fun `an upward drag can open the existing queue`() {
        render()
        compose.onRoot().performTouchInput {
            swipe(Offset(centerX, height * 0.5f), Offset(centerX, height * 0.2f))
        }
        compose.waitForIdle()
        assertEquals(listOf("queue"), calls)
    }

    @Test
    fun `unknown duration stays visible without dispatching a bogus seek`() {
        render(state.copy(duration = 0L))
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithContentDescription("Playback position").performSemanticsAction(SemanticsActions.SetProgress) {
            assertEquals(false, it(0.5f))
        }
        compose.onNodeWithContentDescription("Playback position").performTouchInput { click(center) }
        assertEquals(-1L, sought)
    }

    private fun writeImage(name: String) {
        val directory = System.getenv("BOXLORE_UI_TEST_ARTIFACTS") ?: return
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "bunpod-player-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
