package cx.aswin.boxlore.feature.home.components

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHeroCardTest {
    @get:Rule val composeRule = createComposeRule()

    private data class Scenario(
        val width: Int = 320,
        val scale: Float = 1f,
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val type: HeroType = HeroType.RESUME,
        val count: Int = 4,
        val title: String = "The stories behind the headlines",
        val showName: String = "Show 0",
        val artist: String = "Creator",
        val label: String = "NEW EPISODE",
        val genre: String = "Podcast",
        val duration: Int = 3000,
        val progress: Float? = 0.5f,
        val playing: Boolean = false,
        val playingEpisode: String? = null,
        val dark: Boolean = false,
        val hasEpisode: Boolean = true,
    )

    private val scenario = mutableStateOf(Scenario())
    private var playClicks = 0
    private var detailsClicks = 0
    private val collectionClicks = mutableListOf<String>()
    private val collectionPlayClicks = mutableListOf<String>()
    private lateinit var view: View

    @Test fun `single heroes keep artwork titles and actions within the card at phone widths large text and RTL`() {
        render()
        val variants = listOf(288, 320, 380).flatMap { width ->
            listOf(1f, 1.3f, 2f).flatMap { scale -> LayoutDirection.entries.map { Scenario(width = width, scale = scale, direction = it) } }
        }
        for (value in variants) {
            scenario.value = value.copy(
                title = "A very long title with details about this episode ".repeat(8),
                showName = "A very long podcast name about fascinating discoveries ".repeat(8),
                duration = 10560,
            )
            composeRule.waitForIdle()
            val card = bounds("hero")
            val art = bounds("home_hero_artwork")
            val title = bounds("home_hero_title")
            val play = bounds("home_hero_play")
            for (child in listOf(art, title, play, bounds("home_hero_details"), bounds("home_hero_progress"), bounds("home_hero_supporting_name"), bounds("home_hero_duration"), bounds("home_hero_context"))) {
                assertTrue("Child inside $value", child.left >= card.left && child.right <= card.right && child.top >= card.top && child.bottom <= card.bottom)
            }
            assertEquals(art.width, art.height, 1f)
            assertTrue(art.bottom < title.top)
            assertTrue(title.bottom < bounds("home_hero_supporting_name").top)
            assertTrue(bounds("home_hero_supporting_name").bottom < bounds("home_hero_progress").top)
            assertTrue("Progress ${bounds("home_hero_progress")}; play $play; copy ${bounds("home_hero_copy")}; scenario $value", bounds("home_hero_progress").bottom < play.top)
            val time = bounds("home_hero_duration")
            val badge = bounds("home_hero_context")
            assertTrue(badge.bottom <= time.top || badge.right <= time.left || badge.left >= time.right)
            assertTrue("Time $time; left ${bounds("home_hero_time_left")}; scenario $value", time.bottom <= bounds("home_hero_time_left").top)
            assertTrue(time.bottom <= art.bottom)
            assertTrue(time.right <= art.left || time.left >= art.right || time.bottom <= art.top)
            if (value.scale <= 1.3f) {
                assertEquals(bounds("home_hero_lead").top, art.top, 1f)
                assertEquals(if (scenario.value.direction == LayoutDirection.Ltr) card.right else card.left, if (scenario.value.direction == LayoutDirection.Ltr) art.right else art.left, 1f)
                assertTrue(kotlin.math.abs((time.top + bounds("home_hero_time_left").bottom) / 2 - art.center.y) < 45f)
            }
            assertEquals(play.height, bounds("home_hero_details").height, 1f)
            val layout = textLayout("home_hero_title")
            assertEquals(3, layout.lineCount)
            assertTrue(layout.isLineEllipsized(2))
            assertTrue(layout.getLineBottom(2) <= title.height + 1f)
            val supporting = textLayout("home_hero_supporting_name")
            assertEquals(1, supporting.lineCount)
            assertTrue(supporting.isLineEllipsized(0))
            assertTrue(supporting.getLineBottom(0) <= bounds("home_hero_supporting_name").height + 1f)
            assertTrue(textLayout("home_hero_time_left").getLineBottom(0) <= bounds("home_hero_time_left").height + 1f)
            assertTrue(!textLayout("home_hero_duration").hasVisualOverflow)
            composeRule.onNodeWithText(if (value.scale > 1.3f) "1h 28m" else "1h\n28m", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test fun `short solo titles let attached square artwork use 65 percent of the card width`() {
        render()
        for (width in listOf(288, 320, 380)) {
            for (direction in LayoutDirection.entries) {
                for (type in listOf(HeroType.RESUME, HeroType.JUMP_BACK_IN, HeroType.SPOTLIGHT)) {
                    scenario.value = Scenario(width = width, direction = direction, type = type, title = "One idea", showName = "Radiolab", artist = "WNYC")
                    composeRule.waitForIdle()
                    val card = bounds("hero")
                    val art = bounds("home_hero_artwork")
                    assertEquals(card.width * 0.65f, art.width, 1f)
                    assertEquals(art.width, art.height, 1f)
                    assertTrue(art.bottom < bounds("home_hero_title").top)
                    assertEquals(card.top, art.top, 1f)
                    assertEquals(if (direction == LayoutDirection.Ltr) card.right else card.left, if (direction == LayoutDirection.Ltr) art.right else art.left, 1f)
                }
            }
        }
    }

    @Test fun `adaptive titles grow short names and keep long names within three lines without crowding controls`() {
        render()
        val variants = listOf(288, 320, 380).flatMap { width ->
            listOf(1f, 1.3f, 2f).flatMap { scale ->
                LayoutDirection.entries.map { Scenario(width = width, scale = scale, direction = it) }
            }
        }.flatMap { value ->
            listOf(HeroType.RESUME, HeroType.SPOTLIGHT).map { value.copy(type = it, title = "One idea", showName = "Radiolab", artist = "WNYC") }
        }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val short = textLayout("home_hero_title")
            assertTrue("Short title at $value: lines=${short.lineCount}, overflow=${short.hasVisualOverflow}", short.lineCount <= 2 && !short.hasVisualOverflow)
            val shortSize = short.layoutInput.style.fontSize.value
            if (value.scale == 1f) assertEquals(32f, shortSize, 0f)
            assertTrue(short.getLineBottom(short.lineCount - 1) <= bounds("home_hero_title").height + 1f)
            val longName = "A very long name about fascinating stories and discoveries ".repeat(8)
            scenario.value = value.copy(title = longName, showName = longName)
            composeRule.waitForIdle()
            val long = textLayout("home_hero_title")
            assertEquals(24f, long.layoutInput.style.fontSize.value, 0f)
            assertTrue(shortSize > long.layoutInput.style.fontSize.value)
            assertEquals(3, long.lineCount)
            assertTrue(long.isLineEllipsized(2))
            assertTrue("Title at $value: bottom=${long.getLineBottom(2)}, height=${bounds("home_hero_title").height}", long.getLineBottom(2) <= bounds("home_hero_title").height + 1f)
            assertTrue(bounds("home_hero_artwork").bottom < bounds("home_hero_title").top)
            assertTrue(bounds("home_hero_title").bottom < bounds("home_hero_supporting_name").top)
            assertTrue("Supporting ${bounds("home_hero_supporting_name")}; play ${bounds("home_hero_play")}; copy ${bounds("home_hero_copy")}; scenario $value", bounds("home_hero_supporting_name").bottom < bounds("home_hero_play").top)
            assertEquals(416f + 200f * (value.scale - 1f), bounds("hero").height, 1f)
        }
    }

    @Test fun `larger solo artwork and names fit with short long and missing time in episode and spotlight heroes`() {
        render()
        val variants = listOf(HeroType.RESUME, HeroType.JUMP_BACK_IN, HeroType.SPOTLIGHT).flatMap { type ->
            listOf(null, 0f, 0.5f).flatMap { progress ->
                listOf(1f, 2f).map { scale ->
                    Scenario(
                        width = 288,
                        type = type,
                        scale = scale,
                        progress = progress,
                        title = "A detailed episode title with more than enough text to fill all three lines ".repeat(6),
                        showName = "A detailed show name with a long subtitle ".repeat(8),
                        artist = "Several creators with very long names ".repeat(8),
                        duration = 100 * 3600 + 30 * 60,
                    )
                }
            }
        }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val art = bounds("home_hero_artwork")
            val title = bounds("home_hero_title")
            val supporting = bounds("home_hero_supporting_name")
            val play = bounds("home_hero_play")
            assertTrue(art.width >= 145f)
            assertTrue(art.bottom < title.top && title.bottom < supporting.top && supporting.bottom < play.top)
            assertEquals(3, textLayout("home_hero_title").lineCount)
            assertEquals(1, textLayout("home_hero_supporting_name").lineCount)
            assertEquals(if (value.type == HeroType.SPOTLIGHT) value.artist else value.showName, textLayout("home_hero_supporting_name").layoutInput.text.text)
            assertEquals(416f + 200f * (value.scale - 1f), bounds("hero").height, 1f)
            if (value.type != HeroType.SPOTLIGHT && value.progress != null) {
                assertTrue(!textLayout("home_hero_duration").hasVisualOverflow)
            } else {
                composeRule.onNodeWithTag("home_hero_duration", useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }

    @Test fun `duration units stay intact and captions fit while genres can occupy two lines`() {
        render()
        val variants = listOf(288, 320, 380).flatMap { width ->
            listOf(1f, 1.3f, 2f).flatMap { scale -> LayoutDirection.entries.map { Scenario(width = width, scale = scale, direction = it, duration = 7680) } }
        }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val time = textLayout("home_hero_duration")
            val lines = (0 until time.lineCount).map { time.layoutInput.text.text.substring(time.getLineStart(it), time.getLineEnd(it)).trim() }
            assertEquals(if (value.scale > 1.3f) listOf("1h 4m") else listOf("1h", "4m"), lines)
            assertTrue(!time.hasVisualOverflow)
            assertEquals(1, textLayout("home_hero_context").lineCount)
            assertEquals(1, textLayout("home_hero_time_left").lineCount)
            for (label in listOf("#1 IN NETHERLANDS", "TRENDING IN EDUCATION", "TRENDING IN BUSINESS", "TRENDING IN TECHNOLOGY")) {
                scenario.value = value.copy(type = HeroType.SPOTLIGHT, label = label)
                composeRule.waitForIdle()
                assertEquals(1, textLayout("home_hero_context").lineCount)
                if (label.startsWith("TRENDING")) {
                    assertEquals(1, textLayout("home_hero_genre").lineCount)
                    assertTrue("$label at $value: ${textLayout("home_hero_genre").layoutInput.style}", !textLayout("home_hero_genre").hasVisualOverflow)
                    assertEquals(48f, bounds("home_hero_genre_icon").width, 1f)
                } else {
                    assertEquals(1, textLayout("home_hero_rank").lineCount)
                }
            }
        }
    }

    @Test fun `hero captions and actions are larger and fresh drop listen follows a known duration`() {
        render()
        assertEquals(16f, textLayout("home_hero_time_left").layoutInput.style.fontSize.value, 0.1f)
        assertEquals(15f, textLayout("home_hero_play_label").layoutInput.style.fontSize.value, 0.1f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Normal, textLayout("home_hero_play_label").layoutInput.style.fontWeight)
        assertEquals(22f, bounds("home_hero_play_icon").width, 0.1f)
        assertTrue(bounds("home_hero_play_icon").top >= bounds("home_hero_play").top)
        assertTrue(bounds("home_hero_play_icon").bottom <= bounds("home_hero_play").bottom)
        for (width in listOf(288, 320, 380)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                for (direction in LayoutDirection.entries) {
                    scenario.value = Scenario(type = HeroType.JUMP_BACK_IN, label = "FRESH DROP", width = width, scale = scale, direction = direction, duration = 7680)
                    composeRule.waitForIdle()
                    val listen = textLayout("home_hero_listen")
                    assertEquals("Listen", listen.layoutInput.text.text)
                    assertEquals(1, listen.lineCount)
                    assertTrue(!listen.hasVisualOverflow)
                    assertTrue(bounds("home_hero_duration").bottom <= bounds("home_hero_listen").top)
                    val lead = bounds("home_hero_lead")
                    val caption = bounds("home_hero_listen")
                    assertTrue(caption.top >= lead.top && caption.bottom <= lead.bottom)
                    assertTrue(!textLayout("home_hero_play_label").hasVisualOverflow)
                }
            }
        }
        scenario.value = Scenario(type = HeroType.JUMP_BACK_IN, label = "FRESH DROP", duration = 0)
        composeRule.onNodeWithTag("home_hero_duration", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_listen", useUnmergedTree = true).assertDoesNotExist()
        scenario.value = Scenario(type = HeroType.JUMP_BACK_IN, label = "NEW EPISODE")
        composeRule.onNodeWithTag("home_hero_listen", useUnmergedTree = true).assertDoesNotExist()
        scenario.value = Scenario(type = HeroType.SPOTLIGHT, label = "TRENDING IN TRUE CRIME", genre = "True Crime")
        composeRule.waitForIdle()
        val genre = textLayout("home_hero_genre")
        assertEquals(2, genre.lineCount)
        assertTrue(!genre.hasVisualOverflow)
        assertTrue(genre.layoutInput.style.fontSize.value >= 20f)
        val lines = (0 until genre.lineCount).map { genre.layoutInput.text.text.substring(genre.getLineStart(it), genre.getLineEnd(it)).trim() }
        assertEquals(listOf("True", "Crime"), lines)
    }

    @Test fun `copy centers its actual content without reserving a hidden progress row`() {
        render()
        for (type in listOf(HeroType.RESUME, HeroType.SPOTLIGHT, HeroType.JUMP_BACK_IN)) {
            for (progress in listOf(null, 0f, 0.5f)) {
                scenario.value = Scenario(type = type, progress = progress, title = "One idea", showName = "Radiolab")
                composeRule.waitForIdle()
                val visible = type == HeroType.RESUME && progress != null && progress > 0f
                val end = when {
                    visible -> bounds("home_hero_progress").bottom
                    else -> bounds("home_hero_supporting_name").bottom
                }
                val center = (bounds("home_hero_title").top + end) / 2
                assertEquals(bounds("home_hero_copy").center.y, center, 1f)
                if (!visible) composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }

    @Test fun `native progress moves only for the exact playing episode and keeps real progress semantics`() {
        render()
        composeRule.mainClock.autoAdvance = false
        scenario.value = Scenario(playing = true, playingEpisode = "episode_0")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        progressPixels()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        val first = progressPixels()
        composeRule.mainClock.advanceTimeBy(333)
        composeRule.waitForIdle()
        assertTrue("Progress should move while this episode plays: ${bounds("home_hero_progress")}; colors=${first.toSet()}", !first.contentEquals(progressPixels()))
        val range = composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.5f, range.current, 0f)
        for (value in listOf(Scenario(playing = false), Scenario(playing = true, playingEpisode = "episode_other"))) {
            scenario.value = value
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            progressPixels()
            composeRule.mainClock.advanceTimeBy(1000)
            composeRule.waitForIdle()
            val paused = progressPixels()
            composeRule.mainClock.advanceTimeBy(333)
            composeRule.waitForIdle()
            assertTrue("Progress must settle when paused or another episode plays: $value", paused.contentEquals(progressPixels()))
        }
        composeRule.mainClock.autoAdvance = true
    }

    @Test fun `play details and card taps remain independent and pause requires the exact episode`() {
        render()
        composeRule.onNodeWithTag("home_hero_play").performTouchInput { click() }
        assertEquals(1, playClicks)
        assertEquals(0, detailsClicks)
        composeRule.onNodeWithTag("home_hero_details").performTouchInput { click() }
        assertEquals(1, detailsClicks)
        composeRule.onNodeWithTag("home_hero_title", useUnmergedTree = true).performTouchInput { click() }
        assertEquals(2, detailsClicks)
        scenario.value = Scenario(playing = true, playingEpisode = "episode_other")
        composeRule.onNodeWithText("Pause").assertDoesNotExist()
        scenario.value = Scenario(playing = true, playingEpisode = "episode_0")
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.onNodeWithTag("home_hero_play").performTouchInput { click() }
        assertEquals(2, playClicks)
    }

    @Test fun `spotlight keeps publisher in one quiet line and omits latest episode text without crowding artwork or controls`() {
        render()
        for (width in listOf(288, 320, 380)) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                for (direction in LayoutDirection.entries) {
                    scenario.value = Scenario(
                        type = HeroType.SPOTLIGHT,
                        width = width,
                        scale = scale,
                        direction = direction,
                        label = "TRENDING IN TRUE CRIME",
                        title = "A long latest episode name with plenty of context ".repeat(6),
                        showName = "A long show name that spans several lines ".repeat(6),
                        artist = "A long publisher name that should occupy one line ".repeat(6),
                    )
                    composeRule.waitForIdle()
                    val publisher = textLayout("home_hero_supporting_name")
                    assertEquals(1, publisher.lineCount)
                    assertTrue(publisher.isLineEllipsized(0))
                    assertEquals(3, textLayout("home_hero_title").lineCount)
                    assertTrue(bounds("home_hero_artwork").bottom < bounds("home_hero_title").top)
                    assertTrue(bounds("home_hero_title").bottom < bounds("home_hero_supporting_name").top)
                    assertTrue(bounds("home_hero_supporting_name").bottom < bounds("home_hero_play").top)
                    composeRule.onNodeWithText("Latest: ${scenario.value.title}", useUnmergedTree = true).assertDoesNotExist()
                    assertEquals(416f + 200f * (scale - 1f), bounds("hero").height, 1f)
                    val action = textLayout("home_hero_play_label")
                    assertTrue("Action at $width, $scale, $direction: size=${action.size}, style=${action.layoutInput.style}, width=${action.didOverflowWidth}, height=${action.didOverflowHeight}", !action.hasVisualOverflow)
                    composeRule.onNodeWithText("Play latest").assertIsDisplayed()
                    composeRule.onNodeWithContentDescription("Explore podcast").assertIsDisplayed()
                }
            }
        }
        composeRule.onNodeWithTag("home_hero_play").performTouchInput { click() }
        assertEquals(1, playClicks)
        assertEquals(0, detailsClicks)
        composeRule.onNodeWithTag("home_hero_details").performTouchInput { click() }
        assertEquals(1, detailsClicks)
    }

    @Test fun `spotlight without an episode offers only show exploration and preserves publisher fallback`() {
        render()
        scenario.value = Scenario(type = HeroType.SPOTLIGHT, hasEpisode = false)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Play latest").assertDoesNotExist()
        composeRule.onNodeWithText("Explore podcast").assertIsDisplayed()
        composeRule.onNodeWithTag("home_hero_details").assertDoesNotExist()
        assertEquals("Creator", textLayout("home_hero_supporting_name").layoutInput.text.text)
        composeRule.onNodeWithTag("home_hero_play").performTouchInput { click() }
        assertEquals(0, playClicks)
        assertEquals(1, detailsClicks)
        scenario.value = Scenario(type = HeroType.SPOTLIGHT, title = "Latest episode", playing = true, playingEpisode = "episode_other")
        composeRule.onNodeWithText("Play latest").assertIsDisplayed()
        scenario.value = scenario.value.copy(playingEpisode = "episode_0")
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        scenario.value = scenario.value.copy(playing = false)
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test fun `spotlight rank country and genre remain readable within their own region across text sizes and directions`() {
        render()
        val variants = listOf("#1 IN NETHERLANDS", "TRENDING IN TRUE CRIME", "TRENDING IN INTERNATIONAL HISTORY AND CURRENT AFFAIRS", "TRENDING").flatMap { label ->
            listOf(1f, 1.3f, 2f).flatMap { scale ->
                LayoutDirection.entries.map { direction -> Scenario(type = HeroType.SPOTLIGHT, width = 288, scale = scale, direction = direction, label = label, genre = "True Crime") }
            }
        }
        for (value in variants) {
            scenario.value = value.copy(showName = "A very long show name that fills all three lines of the card ".repeat(6), artist = "Several creators with lengthy names ".repeat(6))
            composeRule.waitForIdle()
            val context = bounds("home_hero_context_group")
            val lead = bounds("home_hero_lead")
            val art = bounds("home_hero_artwork")
            assertTrue(context.top >= lead.top && context.bottom <= lead.bottom)
            assertTrue(context.right <= art.left || context.left >= art.right || context.bottom <= art.top)
            val caption = textLayout("home_hero_context")
            assertTrue(caption.getLineBottom(caption.lineCount - 1) <= bounds("home_hero_context").height + 1f)
            if (value.label.startsWith("#")) {
                composeRule.onNodeWithText("#1", useUnmergedTree = true).assertIsDisplayed()
                val rank = textLayout("home_hero_rank")
                assertTrue(rank.getLineBottom(0) <= bounds("home_hero_rank").height + 1f)
            } else if (value.label.startsWith("TRENDING IN")) {
                composeRule.onNodeWithTag("home_hero_genre_icon", useUnmergedTree = true).assertIsDisplayed()
                val genre = textLayout("home_hero_genre")
                assertTrue(genre.getLineBottom(genre.lineCount - 1) <= bounds("home_hero_genre").height + 1f)
            }
            composeRule.onNodeWithTag("home_hero_duration", useUnmergedTree = true).assertDoesNotExist()
            composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertDoesNotExist()
            assertTrue(textLayout("home_hero_title").getLineBottom(2) <= bounds("home_hero_title").height + 1f)
        }
    }

    @Test fun `spotlight and new episode omit resume progress and unknown durations omit the time label`() {
        render()
        for (type in listOf(HeroType.SPOTLIGHT, HeroType.JUMP_BACK_IN)) {
            scenario.value = Scenario(type = type)
            composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertDoesNotExist()
            composeRule.onNodeWithText(if (type == HeroType.SPOTLIGHT) "Play latest" else "Play").assertIsDisplayed()
        }
        scenario.value = Scenario(progress = null)
        composeRule.onNodeWithTag("home_hero_duration", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_progress", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `collections preserve order sizes and progress with enlarged text in both directions`() {
        render()
        for (count in listOf(2, 3, 4)) {
            for (direction in LayoutDirection.entries) {
                scenario.value = Scenario(type = HeroType.RESUME_GRID, count = count, scale = 2f, direction = direction, title = "Long titles")
                composeRule.waitForIdle()
                var previousBottom = bounds("hero").top
                for (index in 0 until count) {
                    val row = bounds("home_hero_collection_$index")
                    val text = composeRule.onNodeWithText(collectionTitle(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue(row.top >= previousBottom)
                    assertTrue(row.bottom <= bounds("hero").bottom)
                    assertTrue(text.top >= row.top && text.bottom <= row.bottom && text.left >= row.left && text.right <= row.right)
                    previousBottom = row.bottom
                }
            }
        }
        scenario.value = Scenario(type = HeroType.NEW_EPISODES_GRID)
        composeRule.onNodeWithText("New", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_collection_2").performTouchInput { click() }
        assertEquals(listOf("show_2"), collectionClicks)
        scenario.value = Scenario(type = HeroType.RESUME_GRID, playing = true, playingEpisode = "episode_1")
        composeRule.onNodeWithTag("home_hero_collection_0").performTouchInput { click() }
        assertEquals(listOf("show_2", "show_0"), collectionClicks)
    }

    @Test fun `loading and loaded hero heights agree across text sizes`() {
        render(skeleton = true)
        for (scale in listOf(1f, 1.3f, 2f)) {
            scenario.value = Scenario(scale = scale)
            composeRule.waitForIdle()
            assertEquals(bounds("hero").height, bounds("skeleton").height, 1f)
        }
    }

    @Test fun `flat collections keep progress and time together with playback on the artwork`() {
        render()
        val variants = listOf(2, 3, 4).flatMap { count ->
            LayoutDirection.entries.flatMap { direction ->
                listOf(1f, 2f).map { scale ->
                    Scenario(type = HeroType.RESUME_GRID, count = count, width = 288, scale = scale, direction = direction, title = "Long titles", duration = 10560)
                }
            }
        }
        for (value in variants) {
            scenario.value = value
            composeRule.waitForIdle()
            val firstHeight = bounds("home_hero_collection_0").height
            for (index in 0 until value.count) {
                val tag = "home_hero_collection_$index"
                val row = bounds(tag)
                val art = bounds("${tag}_artwork")
                val play = bounds("${tag}_play")
                val title = bounds("${tag}_title")
                val progress = bounds("${tag}_progress")
                val time = bounds("${tag}_time")
                assertEquals(firstHeight, row.height, 1f)
                assertTrue(play.left >= art.left && play.right <= art.right && play.top >= art.top && play.bottom <= art.bottom)
                assertTrue(play.width >= 48f && play.height >= 48f)
                assertTrue(title.top >= row.top && time.bottom <= row.bottom)
                assertTrue(title.bottom <= progress.top && title.bottom <= time.top)
                assertEquals(time.center.y, progress.center.y, 1f)
                assertTrue(progress.width >= 24f)
                assertTrue(progress.right <= time.left || time.right <= progress.left)
                assertEquals(1, textLayout("${tag}_time").lineCount)
                assertTrue(!textLayout("${tag}_time").hasVisualOverflow)
                assertEquals("1h 28m left", textLayout("${tag}_time").layoutInput.text.text)
            }
        }
    }

    @Test fun `collection artwork playback is independent from row navigation and only the current episode pauses`() {
        render()
        for (type in listOf(HeroType.NEW_EPISODES_GRID, HeroType.RESUME_GRID)) {
            scenario.value = Scenario(type = type, playing = true, playingEpisode = "episode_1")
            composeRule.onNodeWithTag("home_hero_collection_0_artwork", useUnmergedTree = true).performTouchInput { click(Offset(6f, 6f)) }
            composeRule.onNodeWithTag("home_hero_collection_0_play", useUnmergedTree = true).performTouchInput { click() }
            assertEquals(if (type == HeroType.NEW_EPISODES_GRID) 0 else 2, collectionClicks.size)
            assertEquals(List(collectionPlayClicks.size) { "show_0" }, collectionPlayClicks)
            composeRule.onNodeWithTag("home_hero_collection_2_title", useUnmergedTree = true).performTouchInput { click() }
            composeRule.onNodeWithTag("home_hero_collection_3_time", useUnmergedTree = true).performTouchInput { click() }
            composeRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
            composeRule.onAllNodesWithContentDescription("Play", useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(3, it.size) }
        }
        assertEquals(listOf("show_2", "show_3", "show_2", "show_3"), collectionClicks)
        assertEquals(List(4) { "show_0" }, collectionPlayClicks)
        scenario.value = scenario.value.copy(playingEpisode = "episode_unrelated")
        composeRule.onNodeWithContentDescription("Pause").assertDoesNotExist()
    }

    @Test fun `collection metadata omits absent progress and unknown duration without reserving space`() {
        render()
        scenario.value = Scenario(type = HeroType.RESUME_GRID, progress = 0f)
        composeRule.onNodeWithTag("home_hero_collection_0_progress", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onAllNodesWithText("50m left", useUnmergedTree = true)[0].assertIsDisplayed()
        scenario.value = scenario.value.copy(progress = null, hasEpisode = false)
        composeRule.onNodeWithTag("home_hero_collection_0_metadata", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("home_hero_collection_0_play", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(bounds("home_hero_collection_0_artwork").center.y, bounds("home_hero_collection_0_title").center.y, 1f)
        scenario.value = Scenario(type = HeroType.NEW_EPISODES_GRID)
        composeRule.onNodeWithTag("home_hero_collection_0_progress", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onAllNodesWithText("50m", useUnmergedTree = true)[0].assertIsDisplayed()
    }

    @Test fun `editorial light dark and collection previews`() {
        scenario.value = Scenario(showName = "Radiolab")
        render()
        savePreview("single-light")
        scenario.value = Scenario(dark = true, showName = "Radiolab")
        savePreview("single-dark")
        scenario.value = Scenario(title = "A thoughtful conversation about the stories that shape how we see the world", showName = "Radiolab", duration = 10560)
        savePreview("single-long")
        scenario.value = Scenario(type = HeroType.SPOTLIGHT, showName = "Radiolab", artist = "WNYC Studios", label = "#1 IN INDIA", genre = "Science")
        savePreview("spotlight")
        scenario.value = Scenario(type = HeroType.SPOTLIGHT, showName = "Radiolab", artist = "WNYC Studios", label = "TRENDING IN SCIENCE", genre = "Science")
        savePreview("spotlight-genre")
        scenario.value = Scenario(type = HeroType.JUMP_BACK_IN, showName = "Radiolab", label = "FRESH DROP")
        savePreview("fresh-drop")
        scenario.value = Scenario(width = 288, scale = 2f, title = "A long episode title about surprising discoveries and the stories behind them", showName = "A very long podcast name that must stay on one line", duration = 10560)
        savePreview("single-large-text")
        scenario.value = Scenario(type = HeroType.RESUME_GRID)
        savePreview("collection-light")
        scenario.value = Scenario(type = HeroType.NEW_EPISODES_GRID, dark = true)
        savePreview("collection-dark")
    }

    private var expectedContainer = Color.Transparent

    private fun render(skeleton: Boolean = false) {
        composeRule.setContent {
            val value = scenario.value
            view = LocalView.current
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, value.scale),
                LocalLayoutDirection provides value.direction,
            ) {
                BoxLoreTheme(darkTheme = value.dark, dynamicColor = false) {
                    expectedContainer = HomeHeroStyle.container(MaterialTheme.colorScheme)
                    Box(Modifier.width(value.width.dp).height(homeHeroHeight()).testTag("hero")) {
                        if (value.type == HeroType.RESUME_GRID || value.type == HeroType.NEW_EPISODES_GRID) {
                            HeroGridCard(
                                (0 until value.count).map { podcast(it, collectionTitle(it)) },
                                if (value.type == HeroType.RESUME_GRID) "Jump back in" else "New episodes",
                                if (value.type == HeroType.RESUME_GRID) HeroGridMode.Resume else HeroGridMode.NewEpisodes,
                                { collectionClicks += it.id },
                                currentPlayingPodcastId = "show_0",
                                currentPlayingEpisodeId = value.playingEpisode,
                                isPlaying = value.playing,
                                onPlaybackClick = { collectionPlayClicks += it.id },
                            )
                        } else {
                            HeroCard(
                                SmartHeroItem(value.type, podcast(0, value.title), value.label, value.title),
                                { playClicks++ },
                                { detailsClicks++ },
                                {},
                                currentPlayingPodcastId = "show_0",
                                currentPlayingEpisodeId = value.playingEpisode,
                                isPlaying = value.playing,
                            )
                        }
                    }
                    if (skeleton) HeroSkeleton(Modifier.width(value.width.dp).testTag("skeleton"))
                }
            }
        }
    }

    private fun podcast(index: Int, title: String): Podcast = Podcast(
        "show_$index",
        if (index == 0) scenario.value.showName else "Show $index",
        scenario.value.artist,
        System.getenv("BOXLORE_HERO_PREVIEW_ARTWORK").orEmpty(),
        genre = scenario.value.genre,
        latestEpisode = if (scenario.value.hasEpisode) Episode("episode_$index", title, "", "", duration = if (scenario.value.progress == null) 0 else scenario.value.duration, publishedDate = System.currentTimeMillis() / 1000L) else null,
        resumeProgress = scenario.value.progress,
    )

    private fun collectionTitle(index: Int): String = if (scenario.value.title == "Long titles") {
        "Episode $index — a detailed conversation exploring interesting ideas and stories ".repeat(8)
    } else {
        "Episode $index"
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()

    private fun textLayout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun progressPixels(): IntArray = pixels("home_hero_progress")

    private fun pixels(tag: String): IntArray {
        val progress = bounds(tag)
        return composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = progress.width.toInt()
            val height = progress.height.toInt()
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, progress.left.toInt(), progress.top.toInt(), width, height)
            bitmap.recycle()
            pixels
        }
    }

    private fun savePreview(name: String) {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val card = bounds("hero")
        composeRule.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val background = bitmap.getPixel((card.left + 2).toInt(), card.center.y.toInt())
            assertEquals(255, android.graphics.Color.alpha(background))
            assertEquals(expectedContainer.toArgb(), background)
            System.getenv("BOXLORE_HERO_PREVIEW_DIR")?.let { directory ->
                val cropped = Bitmap.createBitmap(bitmap, card.left.toInt(), card.top.toInt(), card.width.toInt(), card.height.toInt())
                File(directory).mkdirs()
                File(directory, "$name.png").outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
            }
            bitmap.recycle()
        }
    }
}
