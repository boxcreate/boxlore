package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HeroType
import cx.aswin.boxlore.feature.home.SmartHeroItem
import cx.aswin.boxlore.feature.home.StableHeroList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Issue 1254: between 431dp and 599dp wide (the Galaxy Z Fold 8 cover, split-screen halves) the hero
 * switched from one large card and a peek to three or four narrow cards from one width to the next,
 * splitting titles mid-word. Every compact width now shows one large card and a 40-56dp peek, phones
 * keep their card widths, and sideways phones show a row of whole cards. In every style the title and
 * a full Play button stay inside the card there and in short windows, at normal and enlarged text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeroCarouselCompactWidthTest {
    @get:Rule val composeRule = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val count = mutableIntStateOf(8)
    private val style = mutableStateOf(HeroCardStyle.COVER)
    private val fontScale = mutableFloatStateOf(1f)

    /** Each card's visible width on screen: its carousel mask, cut to the row. */
    private val visible = mutableStateMapOf<Int, Float>()

    @Test fun `every width up to 599dp shows one large card and a narrow peek`() {
        renderRow()
        val failures = mutableListOf<String>()
        for (cards in listOf(3, 8)) {
            count.intValue = cards
            for (width in PHONE_WIDTHS.keys + COMPACT_WIDTHS) {
                val widths = measure(width)
                val focal = widths.max()
                val expected = PHONE_WIDTHS[width] ?: heroCompactGeometry(rowWidth(width).dp).card.value
                if (kotlin.math.abs(focal - expected) > 1f) failures += "$cards cards at ${width}dp: focal $focal, expected $expected"
                val others = widths - focal
                if (others.any { it > MAX_PEEK }) failures += "$cards cards at ${width}dp: a card between the peek and the focal card ($widths)"
                if (others.none { it in MIN_PEEK..MAX_PEEK }) failures += "$cards cards at ${width}dp: no 40-56dp peek ($widths)"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun `sideways phones show a row of whole cards`() {
        renderRow()
        for (layout in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreWindowLayout(600f, 412f))) {
            val widths = measure(layout.widthDp.toInt(), layout)
            // Every card but the last, which the row's end cuts, is a whole card of the same width.
            assertTrue("Whole cards at ${layout.widthDp}dp: $widths", widths.size >= 2 && widths.first() == widths.max())
            assertTrue("No narrower middle card at ${layout.widthDp}dp: $widths", widths.dropLast(1).all { kotlin.math.abs(it - widths.first()) <= 1f })
        }
    }

    @Test fun `every style's title and Play fit the large card on compact and short windows at normal and large text`() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides window.value,
                LocalHeroCardStyle provides style.value,
                LocalDensity provides Density(base.density, fontScale.floatValue),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Column {
                        Box(Modifier.width(1.dp).height(homeHeroHeight()).testTag("hero_height"))
                        HeroCarousel(
                            heroItems = StableHeroList(List(8) { heroItem(it) }),
                            onPlayClick = { _, _ -> },
                            onDetailsClick = {},
                            onArrowClick = { _, _ -> },
                            onToggleSubscription = {},
                            onTogglePlayback = {},
                            modifier = Modifier.width(rowWidth(window.value.widthDp.toInt()).dp).testTag("hero_row"),
                        )
                    }
                }
            }
        }
        val failures = mutableListOf<String>()
        val windows = listOf(
            BoxloreTestWindows.COVER_443, BoxloreTestWindows.COVER_475, BoxloreWindowLayout(485f, 700f), BoxloreWindowLayout(520f, 860f),
            BoxloreWindowLayout(540f, 860f), BoxloreWindowLayout(560f, 860f), BoxloreWindowLayout(599f, 900f),
            BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.PHONE_LAND, BoxloreWindowLayout(800f, 360f), BoxloreTestWindows.COVER_LAND,
        )
        for (scale in listOf(1f, 1.3f)) {
            for (current in listOf(HeroCardStyle.COVER, HeroCardStyle.BLOCK, HeroCardStyle.TIDE, HeroCardStyle.DUOTONE, HeroCardStyle.HALO)) {
                for (layout in windows) {
                    style.value = current
                    window.value = layout
                    fontScale.floatValue = scale
                    composeRule.waitForIdle()
                    failures += cardFailures("$current at ${layout.widthDp.toInt()}x${layout.heightDp.toInt()}, text ${scale}x", tide = current == HeroCardStyle.TIDE)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The first card's title and Play, measured unclipped so a button pushed past the card's foot counts. */
    private fun cardFailures(label: String, tide: Boolean): List<String> {
        val failures = mutableListOf<String>()
        val title = layoutOf("home_hero_title")
        val play = unclipped("home_hero_play")
        if (title == null || play == null) return listOf("$label shows no title or Play")
        val split = (0 until title.lineCount - 1).filter { line ->
            val end = title.getLineEnd(line, visibleEnd = true)
            end in 1 until title.layoutInput.text.length && !title.layoutInput.text[end - 1].isWhitespace() && !title.layoutInput.text[end].isWhitespace()
        }
        if (split.isNotEmpty()) failures += "$label splits a word after line $split"
        val rowTop = unclipped("hero_row")!!.top
        val heroHeight = unclipped("hero_height")!!.height
        if (play.top < rowTop - 0.5f || play.bottom > rowTop + heroHeight + 0.5f) failures += "$label: Play $play leaves the card ($rowTop..${rowTop + heroHeight})"
        if (play.height < 48f - 0.5f) failures += "$label: Play is ${play.height}dp tall "
        if (tide) {
            val titleBounds = unclipped("home_hero_title")!!
            if (titleBounds.overlaps(play)) failures += "$label: the title $titleBounds runs under the Play disc $play"
        } else if (layoutOf("home_hero_play_label")?.hasVisualOverflow != false) {
            failures += "$label cuts the Play label"
        }
        return failures
    }

    private fun unclipped(tag: String): Rect? {
        val node = startNode(tag) ?: return null
        return Rect(node.positionInRoot, node.size.toSize())
    }

    /**
     * The node for [tag] in the card nearest the row's start. The carousel composes cards in no set
     * order and keeps unplaced ones (zero size) for reuse, so those are skipped.
     */
    private fun startNode(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .filter { it.size.width > 0 && it.size.height > 0 }
        .minByOrNull { it.positionInRoot.x }

    private fun renderRow() {
        composeRule.setContent {
            val layout = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout) {
                val state = rememberCarouselState { count.intValue }
                val row = rowWidth(layout.widthDp.toInt()).toFloat()
                ResponsiveHeroCarousel(state, 320.dp, 16.dp, PaddingValues(0.dp), Modifier.width(row.dp).height(300.dp)) { i ->
                    Box(
                        Modifier.fillMaxSize().onGloballyPositioned {
                            val left = it.boundsInRoot().left + carouselItemDrawInfo.maskRect.left
                            val right = it.boundsInRoot().left + carouselItemDrawInfo.maskRect.right
                            visible[i] = (minOf(right, row) - maxOf(left, 0f)).coerceAtLeast(0f)
                        },
                    )
                }
            }
        }
    }

    /** The visible card widths, start to end, in a window [width] wide (mdpi: one pixel per dp). */
    private fun measure(width: Int, layout: BoxloreWindowLayout = BoxloreWindowLayout(width.toFloat(), 900f)): List<Float> {
        visible.clear()
        window.value = layout
        composeRule.waitForIdle()
        return visible.toSortedMap().values.filter { it > 1f }
    }

    /** The first card's text layout for [tag], or null when the card shows no such text. */
    private fun layoutOf(tag: String): TextLayoutResult? {
        val node = startNode(tag) ?: return null
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    private fun heroItem(index: Int): SmartHeroItem {
        val episode = TestFixtures.episode(id = "e$index", title = "A Thanksgiving Dinner for \$100? Yes, It's Possible", podcastId = "s$index").copy(duration = 3_900)
        val show = TestFixtures.podcast(id = "s$index", title = "The Joe Rogan Experience", imageUrl = "").copy(latestEpisode = episode, resumeProgress = 0.4f)
        return SmartHeroItem(HeroType.RESUME, show, "Resume")
    }

    private companion object {
        /** Home's grid keeps 16dp each side of the hero row. */
        fun rowWidth(window: Int) = window - 32

        /** Phone windows keep today's focal card widths. */
        val PHONE_WIDTHS = mapOf(360 to 272f, 393 to 305f, 412 to 320f, 430 to 326f)
        val COMPACT_WIDTHS = listOf(431, 443, 460, 470, 475, 480, 485, 490, 495, 500, 540, 584, 599)
        const val MIN_PEEK = 40f
        const val MAX_PEEK = 56f
    }
}
