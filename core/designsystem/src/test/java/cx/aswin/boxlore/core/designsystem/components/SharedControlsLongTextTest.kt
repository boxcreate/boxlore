package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.component.BoxloreTopBar
import cx.aswin.boxlore.core.designsystem.component.boxloreLargeTitlePresentation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shared controls with long and translated text and large font sizes: they wrap or shorten inside
 * their own row instead of being cut, and keep their state when the window changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedControlsLongTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `large titles step down a size for large text and narrow panes only`() {
        val typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
        )
        assertEquals(32.sp, boxloreLargeTitlePresentation(typography, 0f, paneWidthDp = 412f).style.fontSize)
        assertEquals(28.sp, boxloreLargeTitlePresentation(typography, 0f, paneWidthDp = 360f).style.fontSize)
        assertEquals("an unknown width is not narrow", 32.sp, boxloreLargeTitlePresentation(typography, 0f, paneWidthDp = 0f).style.fontSize)
        assertEquals(28.sp, boxloreLargeTitlePresentation(typography, 0f, fontScale = 1.3f, paneWidthDp = 412f).style.fontSize)
        val collapsed = boxloreLargeTitlePresentation(typography, 0f, expanded = false, fontScale = 1.3f, paneWidthDp = 360f)
        assertEquals(22.sp, collapsed.style.fontSize)
        assertEquals(1, collapsed.maxLines)
        assertEquals(2, boxloreLargeTitlePresentation(typography, 0.5f).maxLines)
    }

    @Test
    fun `long translated page titles fit their header at 360dp and 130 percent text`() {
        val title = mutableStateOf(LONG_TITLES.first())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    BoxloreTopBar(title.value, onBack = {})
                }
            }
        }
        LONG_TITLES.forEach { text ->
            compose.runOnIdle { title.value = text }
            compose.waitForIdle()
            // The expanded header shows the title in its lower row; the hidden slim row may shorten it.
            val shown = compose.onAllNodesWithText(text).fetchSemanticsNodes().maxBy { it.boundsInRoot.top }
            val results = mutableListOf<TextLayoutResult>()
            shown.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.single()
            assertFalse("$text is cut at the bottom", layout.didOverflowHeight)
            assertFalse("$text is shortened", layout.isLineEllipsized(layout.lineCount - 1))
            assertTrue("$text stays inside the window", shown.boundsInRoot.right <= BoxloreTestWindows.PHONE_SMALL.widthDp)
        }
    }

    @Test
    fun `a short pane forces the slim bar and the same bar opens up again when the list is at its top`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val states = mutableListOf<TopAppBarState>()
        compose.setContent {
            Page(window.value) {
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                states += behavior.state
                Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
                    BoxloreTopBar("Downloads", Modifier.testTag("bar"), onBack = {}, scrollBehavior = behavior)
                    LazyColumn(Modifier.fillMaxSize()) { items(40) { Text("Row $it", Modifier.padding(16.dp)) } }
                }
            }
        }
        val expanded = TopAppBarDefaults.LargeAppBarExpandedHeight.value
        val slim = TopAppBarDefaults.TopAppBarExpandedHeight.value
        val heights = mapOf(BoxloreTestWindows.PHONE to expanded, BoxloreTestWindows.PHONE_LAND to slim)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { step ->
            val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            assertEquals("$step", heights.getValue(step), bar.height, 0.5f)
            states.forEach { assertSame("one bar state for the page", states.first(), it) }
        }
    }

    @Test
    fun `a page that opens in a short pane scrolls under the slim bar, and keeps scrolling as it turns`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE_LAND)
        val list = LazyListState()
        compose.setContent {
            Page(window.value) {
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
                    BoxloreTopBar("Downloads", Modifier.testTag("bar"), onBack = {}, scrollBehavior = behavior)
                    LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list) { items(200) { Text("Row $it", Modifier.padding(16.dp)) } }
                }
            }
        }
        fun scrolled(): Int {
            compose.onNodeWithTag("list").performTouchInput { swipeUp() }
            compose.waitForIdle()
            return list.firstVisibleItemIndex
        }
        var last = scrolled()
        assertTrue("the first swipe moved the list", last > 0)
        compose.walkWindows(window, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND) { step ->
            if (step == BoxloreTestWindows.PHONE) {
                val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot.height
                assertEquals("$step: a scrolled page keeps the bar collapsed", TopAppBarDefaults.LargeAppBarCollapsedHeight.value, bar, 0.5f)
            }
            val next = scrolled()
            assertTrue("$step: $last then $next", next > last)
            last = next
        }
    }

    @Test
    fun `on a tablet the title lines up with the content column`() {
        val back = mutableStateOf(false)
        compose.setContent {
            Page(BoxloreTestWindows.TABLET) {
                Column(Modifier.fillMaxWidth()) {
                    BoxloreTopBar("Library", onBack = if (back.value) ({}) else null)
                    Box(Modifier.fillMaxWidth().boxloreCardWidth(BoxloreCardFamily.Utility).padding(horizontal = 16.dp)) {
                        Text("First row", Modifier.testTag("content"))
                    }
                }
            }
        }
        listOf(false, true).forEach { withBack ->
            compose.runOnIdle { back.value = withBack }
            compose.waitForIdle()
            val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
            val titles = compose.onAllNodesWithText("Library").fetchSemanticsNodes().map { it.boundsInRoot }
            // With a Back button the large title is the second row's; the first row holds Back.
            val largeTitle = titles.maxBy { it.top }
            assertEquals("back $withBack", content.left, largeTitle.left, 0.5f)
        }
    }

    @Test
    fun `icon-only top bar segments name themselves on long-press and hover`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Page(BoxloreTestWindows.TABLET) {
                TopBarUtilityActions(onFeedbackClick = {}, onSettingsClick = {}, modifier = Modifier.padding(top = 120.dp, start = 400.dp))
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Send Feedback").assertDoesNotExist()
        compose.onNodeWithContentDescription("Send Feedback").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Send Feedback").assertExists()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("Send Feedback").assertDoesNotExist()

        compose.onNodeWithContentDescription("Settings").performMouseInput { enter(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun `the update sweep runs three times and then holds still`() {
        compose.mainClock.autoAdvance = false
        val phases = mutableListOf<Float>()
        compose.setContent {
            val sweep = rememberUpdateShimmerSweep()
            LaunchedEffect(sweep) { snapshotFlow { sweep.value }.collect { phases += it } }
        }
        compose.mainClock.advanceTimeBy(UpdateShimmer.DURATION_MILLIS / 2L)
        assertTrue("the sweep moves", phases.any { it > UpdateShimmer.START && it < UpdateShimmer.END })
        compose.mainClock.advanceTimeBy(UpdateShimmer.DURATION_MILLIS * UpdateShimmer.SWEEPS.toLong())
        val settled = phases.last()
        assertEquals(UpdateShimmer.END, settled, 0.001f)
        val count = phases.size
        compose.mainClock.advanceTimeBy(UpdateShimmer.DURATION_MILLIS * 2L)
        assertEquals("the sweep holds still after the last pass", count, phases.size)
    }

    @Test
    fun `with animations off the update sweep never starts`() {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            compose.mainClock.autoAdvance = false
            val phases = mutableListOf<Float>()
            compose.setContent {
                val sweep = rememberUpdateShimmerSweep()
                LaunchedEffect(sweep) { snapshotFlow { sweep.value }.collect { phases += it } }
            }
            compose.mainClock.advanceTimeBy(UpdateShimmer.DURATION_MILLIS * 4L)
            assertTrue(phases.isNotEmpty())
            assertTrue("phases $phases", phases.all { it == UpdateShimmer.START })
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `filter chips are full touch targets, filled when selected and shortened when long`() {
        val colors = mutableListOf<ColorScheme>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    colors += MaterialTheme.colorScheme
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        BoxloreFilterChip(selected = true, onClick = {}, label = "Musik", modifier = Modifier.testTag("selected"))
                        BoxloreFilterChip(selected = false, onClick = {}, label = "Wissenschaft", modifier = Modifier.testTag("unselected"))
                        BoxloreFilterChip(
                            selected = false,
                            onClick = {},
                            label = "Gesellschaft und Kultur und noch viel mehr",
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                }
            }
        }
        val scheme = colors.last()
        listOf("selected" to scheme.primaryContainer, "unselected" to scheme.surfaceContainer).forEach { (tag, expected) ->
            val node = compose.onNodeWithTag(tag)
            assertTrue("$tag is shorter than 48dp", node.fetchSemanticsNode().boundsInRoot.height >= 48f)
            val image = node.captureToImage().asAndroidBitmap()
            assertEquals(tag, expected.toArgb(), image.getPixel(image.width - 6, image.height / 2))
        }
        compose.onNodeWithTag("selected").assertIsSelected()
        compose.onNodeWithTag("unselected").assertIsNotSelected()
        val long = textLayout("Gesellschaft und Kultur und noch viel mehr")
        assertEquals(1, long.lineCount)
        assertTrue("a long chip label shortens", long.isLineEllipsized(0))
    }

    @Test
    fun `chips in scrolling rows keep their whole names`() {
        compose.setContent {
            Page(BoxloreTestWindows.PHONE) {
                Column {
                    LazyRow { item { PillFilterChip(label = "True Crime", selected = false, onClick = {}) } }
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        BoxloreFilterChip(selected = true, onClick = {}, label = "Science")
                        PillFilterChip(label = "Comedy", selected = false, onClick = {}, icon = Icons.Rounded.ExpandMore)
                    }
                }
            }
        }
        listOf("True Crime", "Science", "Comedy").forEach { label ->
            compose.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
            val layout = textLayout(label)
            assertTrue("$label has no width", layout.size.width > 0)
            assertFalse("$label is shortened", layout.isLineEllipsized(0))
            assertFalse("$label is cut", layout.hasVisualOverflow)
        }
    }

    @Test
    fun `chips on sheets stay visible against the sheet`() {
        val pairs = mutableListOf<Pair<Color, Color>>()
        compose.setContent {
            listOf(false, true).forEach { dark ->
                BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                    pairs += BoxloreFilterChipDefaults.SheetContainer to MaterialTheme.colorScheme.surfaceContainer
                }
            }
        }
        assertEquals(2, pairs.size)
        pairs.forEach { (chip, sheet) -> assertTrue("$chip on $sheet", chip != sheet) }
    }

    @Test
    fun `option names in French, Spanish, Portuguese and German fit their buttons at 296dp`() {
        val sets = mutableStateOf(SELECTOR_LABELS.first())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    Box(Modifier.width(296.dp)) {
                        ConnectedOptionSelector(options = sets.value.mapIndexed { index, label -> index to label }, selected = 0, onSelect = {})
                    }
                }
            }
        }
        SELECTOR_LABELS.forEach { labels ->
            compose.runOnIdle { sets.value = labels }
            compose.waitForIdle()
            labels.forEach { label ->
                val layout = textLayout(label)
                assertFalse("$label is cut", layout.hasVisualOverflow)
                assertTrue("$label breaks inside a word", layout.fitsWholeWords(label))
            }
        }
    }

    @Test
    fun `when one option name wraps, every option in the row is as tall`() {
        compose.setContent {
            Page(BoxloreTestWindows.PHONE) {
                Box(Modifier.width(360.dp)) {
                    ConnectedOptionSelector(options = listOf(0 to "Home", 1 to "Explore more shows", 2 to "Library"), selected = 0, onSelect = {})
                }
            }
        }
        assertEquals(2, textLayout("Explore more shows").lineCount)
        val heights = listOf("Home", "Explore more shows", "Library").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.height }
        assertTrue("one row, even heights: $heights", heights.all { abs(it - heights.first()) < 0.5f })
    }

    @Test
    fun `options stack as a list when a name cannot fit even on two lines, and go back to a row when there is room`() {
        val width = mutableStateOf(200.dp)
        compose.setContent {
            Page(BoxloreTestWindows.TABLET) {
                Box(Modifier.width(width.value)) {
                    ConnectedOptionSelector(
                        options = listOf(0 to "Herunterladen", 1 to "Benachrichtigungen", 2 to "Aus"),
                        selected = 1,
                        onSelect = {},
                    )
                }
            }
        }
        fun tops() = listOf("Herunterladen", "Benachrichtigungen", "Aus").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        val stacked = tops()
        assertTrue("stacked at 200dp: $stacked", stacked.zipWithNext().all { (a, b) -> b > a })
        compose.onNodeWithText("Benachrichtigungen").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        compose.runOnIdle { width.value = 600.dp }
        compose.waitForIdle()
        val row = tops()
        assertTrue("one row at 600dp: $row", row.all { it == row.first() })
        compose.onNodeWithText("Benachrichtigungen").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `English options on a phone keep their full size on one line`() {
        val styles = mutableListOf<TextStyle>()
        compose.setContent {
            Page(BoxloreTestWindows.PHONE) {
                styles += MaterialTheme.typography.labelLarge
                ConnectedOptionSelector(
                    options = listOf(0 to "Floating", 1 to "Classic"),
                    selected = 0,
                    onSelect = {},
                    labelStyle = MaterialTheme.typography.labelLarge,
                )
            }
        }
        listOf("Floating", "Classic").forEach { label ->
            val layout = textLayout(label)
            assertEquals(1, layout.lineCount)
            assertEquals(styles.last().fontSize, layout.layoutInput.style.fontSize)
        }
    }

    @Test
    fun `field hints stay on one line in German at large text`() {
        val hint = "Titel, Hosts oder Themen in allen Podcasts suchen"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    Column {
                        BoxloreTextField(value = "", onValueChange = {}, placeholder = "$hint.", modifier = Modifier.fillMaxWidth())
                        BoxloreSearchField(query = "", onQueryChange = {}, onSearch = {}, placeholder = hint)
                    }
                }
            }
        }
        listOf("$hint.", hint).forEach { text ->
            val layout = textLayout(text)
            assertEquals(text, 1, layout.lineCount)
        }
    }

    @Test
    fun `clear empties the search and keeps the keyboard, and the search key searches once`() {
        val query = mutableStateOf("comedy")
        val searches = mutableListOf<String>()
        compose.setContent {
            Page(BoxloreTestWindows.PHONE) {
                BoxloreSearchField(query = query.value, onQueryChange = { query.value = it }, onSearch = { searches += it }, placeholder = "Search")
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        field.assertIsFocused()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitForIdle()
        assertEquals("", query.value)
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("history")
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(listOf("history"), searches)
    }

    @Test
    fun `the search field stops at 720dp on tablets`() {
        compose.setContent {
            Page(BoxloreTestWindows.TABLET) {
                BoxloreSearchField(query = "", onQueryChange = {}, onSearch = {}, placeholder = "Search", modifier = Modifier.testTag("search"))
            }
        }
        assertTrue(compose.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot.width <= 720f)
    }

    @Test
    fun `a destructive confirm is filled in the error colour and Cancel has no container`() {
        val schemes = mutableListOf<ColorScheme>()
        compose.setContent {
            Page(BoxloreTestWindows.PHONE) {
                schemes += MaterialTheme.colorScheme
                Row(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                    Box(Modifier.testTag("dismiss")) { BoxloreDialogDismissButton("Cancel", onClick = {}) }
                    Box(Modifier.testTag("confirm")) { BoxloreDialogConfirmButton("Remove", onClick = {}, destructive = true) }
                }
            }
        }
        val scheme = schemes.last()
        val confirm = compose.onNodeWithTag("confirm").captureToImage().asAndroidBitmap()
        assertEquals(scheme.error.toArgb(), confirm.getPixel(confirm.width / 2, 8))
        val dismiss = compose.onNodeWithTag("dismiss").captureToImage().asAndroidBitmap()
        assertEquals(scheme.surface.toArgb(), dismiss.getPixel(dismiss.width / 2, 8))
        assertTrue(compose.onNodeWithTag("confirm").fetchSemanticsNode().boundsInRoot.height >= 48f)
    }

    @Test
    fun `German dialog buttons wrap instead of running out of a 360dp dialog at large text`() {
        val labels = listOf("Heruntergeladene Folge endgültig entfernen", "Abbrechen und behalten")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    BoxloreAlertDialog(
                        onDismissRequest = {},
                        title = { Text("Download entfernen?") },
                        confirmButton = { BoxloreDialogConfirmButton(labels[0], onClick = {}, destructive = true) },
                        dismissButton = { BoxloreDialogDismissButton(labels[1], onClick = {}) },
                    )
                }
            }
        }
        labels.forEach { label ->
            val layout = textLayout(label)
            assertFalse("$label is cut at the bottom", layout.didOverflowHeight)
            assertFalse("$label is shortened", layout.isLineEllipsized(layout.lineCount - 1))
            assertTrue("$label breaks inside a word", (0 until layout.lineCount - 1).all { label[layout.getLineEnd(it) - 1] == ' ' || label[layout.getLineEnd(it)] == ' ' })
        }
        // Stacked, the filled confirm keeps Material's 8dp above Cancel.
        val confirm = compose.onNodeWithText(labels[0]).fetchSemanticsNode().boundsInRoot
        val dismiss = compose.onNodeWithText(labels[1]).fetchSemanticsNode().boundsInRoot
        assertEquals(8f, dismiss.top - confirm.bottom, 0.5f)
    }

    @Test
    fun `skeleton text lines are as tall as the loaded text at normal and large text`() {
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = fontScale.value)) {
                Page(BoxloreTestWindows.PHONE) {
                    // A theme style, an unset one, and an em line height.
                    Column {
                        listOf(MaterialTheme.typography.titleMedium, TextStyle(), TextStyle(fontSize = 14.sp, lineHeight = 1.2.em)).forEachIndexed { index, style ->
                            Row {
                                SkeletonTextLines(style, 2, Modifier.width(160.dp).testTag("lines$index"))
                                Text("Line one\nLine two", style = style, modifier = Modifier.testTag("text$index"))
                            }
                        }
                    }
                }
            }
        }
        listOf(1f, 1.3f, 1f).forEach { scale ->
            fontScale.value = scale
            compose.waitForIdle()
            repeat(3) { index ->
                val text = compose.onNodeWithTag("text$index").fetchSemanticsNode().boundsInRoot.height
                assertEquals("style $index, font scale $scale", text, compose.onNodeWithTag("lines$index").fetchSemanticsNode().boundsInRoot.height, 0.5f)
            }
        }
    }

    @Test
    fun `cards without a show name keep their height and the show name line matches its skeleton`() {
        val expected = mutableMapOf<Boolean, Float>()
        compose.setContent {
            Page(BoxloreTestWindows.PHONE) {
                expected[false] = expressivePosterCardHeight(160.dp).value
                expected[true] = expressivePosterCardHeight(160.dp, withSubtitle = true).value
                Column {
                    Row {
                        FeedMediaCard("", "Title", null, {}, Modifier.width(160.dp).testTag("plain"), presentation = FeedMediaCardPresentation.ExpressivePoster)
                        FeedMediaCardSkeleton(Modifier.width(160.dp).testTag("plain-skeleton")) { Box(it) }
                    }
                    Row {
                        FeedMediaCard("", "Title", "Show", {}, Modifier.width(160.dp).testTag("named"), presentation = FeedMediaCardPresentation.ExpressivePoster)
                        FeedMediaCardSkeleton(Modifier.width(160.dp).testTag("named-skeleton"), showSubtitle = true) { Box(it) }
                    }
                }
            }
        }
        fun height(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().size.height.toFloat()
        assertEquals(expected.getValue(false), height("plain"), 0.5f)
        assertEquals(height("plain"), height("plain-skeleton"), 0.5f)
        assertTrue("the show name adds a line", height("named") > height("plain"))
        assertEquals(height("named"), height("named-skeleton"), 0.5f)
        assertEquals(expected.getValue(true), height("named"), 0.5f)
    }

    @Test
    fun `a long German show name stays on one line at 360dp and large text`() {
        val show = "Die wöchentliche Wissenschaftssendung für Neugierige"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    FeedMediaCard("", "Folge 12", show, {}, Modifier.width(156.dp), presentation = FeedMediaCardPresentation.ExpressivePoster)
                }
            }
        }
        val layout = textLayout(show)
        assertEquals(1, layout.lineCount)
        assertTrue("a long show name shortens", layout.isLineEllipsized(0))
    }

    @Test
    fun `the featured title leads on tablets with room and stays small on phones and narrow columns`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val width = mutableStateOf(380.dp)
        val typography = mutableListOf<Typography>()
        compose.setContent {
            Page(window.value) {
                typography += MaterialTheme.typography
                FeedMediaCard("", "Featured", null, {}, Modifier.width(width.value), presentation = FeedMediaCardPresentation.ExpressiveFeatured)
            }
        }
        val titleLarge = typography.last().titleLarge.fontSize
        val steps = listOf(
            Triple(BoxloreTestWindows.PHONE, 380.dp, 16.sp),
            Triple(BoxloreWindowLayout(600f, 960f), 560.dp, titleLarge),
            Triple(BoxloreWindowLayout(600f, 960f), 340.dp, 16.sp),
            Triple(BoxloreWindowLayout(600f, 960f), 560.dp, titleLarge),
            Triple(BoxloreTestWindows.W599_H900, 560.dp, 16.sp),
        )
        steps.forEach { (step, cardWidth, expected) ->
            window.value = step
            width.value = cardWidth
            compose.waitForIdle()
            assertEquals("$step at $cardWidth", expected, textLayout("Featured").layoutInput.style.fontSize)
        }
    }

    @Test
    @Config(qualifiers = "+de")
    fun `the compact country chip shows a long country whole at 360dp and 130 percent text`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) { RegionSegmentedSelector("de", onSwitchRegion = {}, compact = true) }
            }
        }
        val layout = textLayout("Deutschland")
        assertFalse("the country is cut", layout.didOverflowHeight || layout.isLineEllipsized(0))
        assertEquals(1, layout.lineCount)
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Composable
    private fun Page(window: BoxloreWindowLayout, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.size(window.widthDp.dp, window.heightDp.dp)) { content() }
            }
        }
    }

    private companion object {
        val LONG_TITLES = listOf("Téléchargements intelligents", "Episodios que te gustan")
        val SELECTOR_LABELS = listOf(
            listOf("Désactivé", "Téléchargements", "Toujours"),
            listOf("Recortadas", "Portadas enteras"),
            listOf("Desativado", "Wi-Fi", "Sempre"),
            listOf("Aus", "Automatisch", "Immer"),
        )
    }
}
