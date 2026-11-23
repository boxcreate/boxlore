package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
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
import org.robolectric.annotation.GraphicsMode

/**
 * Above a tabletop fold, on sideways phones and in split-screen halves the hero can be as short as
 * 240dp. Long titles and large text there squeezed Play into a sliver, cut Tide's disc at the card's
 * foot and pushed its title under the disc. Every artwork style must keep a full, readable Play button
 * inside the card, measured unclipped, with the longest real title at normal and 130% text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w900dp-h1500dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShortHeroCardsTest {
    @get:Rule val composeRule = createComposeRule()
    private val style = mutableStateOf(HeroCardStyle.COVER)
    private val grid = mutableStateOf(false)
    private val fontScale = mutableFloatStateOf(1f)
    private val cardHeight = mutableStateOf(240.dp)
    private val cardWidth = mutableStateOf(320.dp)

    private fun show(index: Int) = TestFixtures.podcast(id = "show-$index", title = "The Joe Rogan Experience", imageUrl = "").copy(
        latestEpisode = TestFixtures.episode(id = "e$index", title = LONG_TITLE, podcastId = "show-$index").copy(duration = 3600),
        resumeProgress = 0.4f,
    )

    private fun item(): SmartHeroItem = SmartHeroItem(HeroType.RESUME, show(0), "Resume")

    @Test
    fun `short artwork cards keep a full Play button with long titles and large text`() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale.floatValue),
                LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE_LAND,
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(cardWidth.value, cardHeight.value).testTag("short_hero")) { if (grid.value) ShortCollection() else ShortCard() }
                }
            }
        }
        val failures = mutableListOf<String>()
        for (scale in listOf(1f, 1.3f)) {
            for (height in listOf(240.dp, shortHeroFloor(scale)).distinct()) {
                SINGLE_STYLES.forEach { current ->
                    show(current, collection = false, scale, height)
                    failures += singleCardFailures("$current at ${height.value}dp, text ${scale}x", tide = current == HeroCardStyle.TIDE)
                }
                failures += collectionsAt(scale, height)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Every style's collection at [height] and text [scale], on a split-screen half's card and a sideways phone's wider one. */
    private fun collectionsAt(scale: Float, height: Dp): List<String> {
        val failures = mutableListOf<String>()
        for (width in listOf(320.dp, 520.dp)) {
            cardWidth.value = width
            SINGLE_STYLES.forEach { current ->
                show(current, collection = true, scale, height)
                failures += collectionFailures("$current collection ${width.value}x${height.value}dp, text ${scale}x")
            }
        }
        cardWidth.value = 320.dp
        return failures
    }

    @Composable
    private fun ShortCard() {
        when (style.value) {
            HeroCardStyle.BLOCK -> HeroCard(item(), {}, {}, {})
            HeroCardStyle.DUOTONE -> DuotoneHeroCard(item(), {}, {}, null, null, false)
            HeroCardStyle.HALO -> HaloHeroCard(item(), {}, {}, null, null, false)
            HeroCardStyle.TIDE -> TideHeroCard(item(), {}, {}, null, null, false)
            else -> CoverHeroCard(item(), {}, {}, null, null, false)
        }
    }

    @Composable
    private fun ShortCollection() {
        val items = List(4) { show(it) }
        when (style.value) {
            HeroCardStyle.COVER -> CoverHeroGridCard(items, "Jump back in", HeroGridMode.Resume, {}, {}, null, null, false)
            HeroCardStyle.DUOTONE -> DuotoneHeroGridCard(items, "Jump back in", HeroGridMode.Resume, {}, {}, null, null, false)
            HeroCardStyle.HALO -> HaloHeroGridCard(items, "Jump back in", HeroGridMode.Resume, {}, {}, null, null, false)
            HeroCardStyle.TIDE -> TideHeroGridCard(items, "Jump back in", HeroGridMode.Resume, {}, {}, null, null, false)
            else -> HeroGridCard(items, "Jump back in", HeroGridMode.Resume, {})
        }
    }

    /**
     * Every one of the four episodes keeps its Play target whole inside the card, at least 48dp each way
     * and clear of Tide's panel, and the count the card shows is the number of episodes it lets you play.
     */
    private fun collectionFailures(label: String): List<String> {
        val hero = rect("short_hero")
        // Block and Duotone rows play from their whole cover; the other styles from a Play button on it.
        val target = if (style.value == HeroCardStyle.BLOCK || style.value == HeroCardStyle.DUOTONE) "artwork" else "play"
        val targets = (0 until 4).map { index -> index to rectOrNull("home_hero_collection_${index}_$target") }
        val panel = rectOrNull("home_hero_tide_panel")
        val failures = targets.flatMapTo(mutableListOf()) { (index, target) ->
            listOfNotNull(playTargetFailure(label, index, target, hero, panel), target?.let { coverTitleFailure(label, index, it) })
        }
        val count = composeRule.onAllNodesWithTag("home_hero_collection_count", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        val shown = count?.config?.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        if (count != null && shown != targets.count { it.second != null }.toString()) failures += "$label: the count says $shown"
        return failures
    }

    /** Why episode [index]'s Play [target] fails: missing, outside the card, under 48dp or under Tide's [panel]. */
    private fun playTargetFailure(label: String, index: Int, target: Rect?, hero: Rect, panel: Rect?): String? = when {
        target == null -> "$label: episode ${index + 1} has no Play target"
        !hero.contains(target) -> "$label: Play $target leaves the card $hero"
        target.width < 48f - 0.5f || target.height < 48f - 0.5f -> "$label: Play target is ${target.width}x${target.height}dp"
        panel != null && target.overlaps(panel) -> "$label: Play ${index + 1} $target is under the panel $panel"
        else -> null
    }

    /** Cover's tiles draw their words over the cover: they keep clear of the Play button's 32dp disc. */
    private fun coverTitleFailure(label: String, index: Int, target: Rect): String? {
        if (style.value != HeroCardStyle.COVER) return null
        val title = rectOrNull("home_hero_collection_${index}_title") ?: return null
        return "$label: episode ${index + 1}'s title $title runs under its Play button $target".takeIf { title.overlaps(target.deflate(8f)) }
    }

    @Test
    fun `collection cards keep every row on tall heroes`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(320.dp, 416.dp)) { HeroGridCard(List(4) { show(it) }, "Jump back in", HeroGridMode.Resume, {}) }
                }
            }
        }
        (0 until 4).forEach { index -> composeRule.onNodeWithTag("home_hero_collection_$index", useUnmergedTree = true).assertExists() }
    }

    @Test
    fun `the hero fits short compact windows and keeps its full height on portrait phones`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val miniPlayer = mutableStateOf(false)
        var bottomChrome = 0.dp
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale.floatValue),
                LocalBoxloreWindowLayout provides window.value,
                LocalBoxloreMiniPlayerVisible provides miniPlayer.value,
            ) {
                bottomChrome = appBottomChromeContentPadding(isMiniPlayerVisible = true)
                Box(Modifier.height(homeHeroHeight()).width(10.dp).testTag("hero_height"))
            }
        }
        fun height(): Float = composeRule.onNodeWithTag("hero_height").fetchSemanticsNode().boundsInRoot.height
        for (scale in listOf(1f, 1.3f)) {
            fontScale.floatValue = scale
            // Portrait phones keep the full hero in both directions of every change.
            for (layout in listOf(BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE_SMALL, BoxloreTestWindows.SPLIT_HALF, BoxloreTestWindows.PHONE)) {
                window.value = layout
                composeRule.waitForIdle()
                val natural = 416f + 200f * (scale - 1f)
                if (layout.heightDp >= 480f) {
                    assertEquals("Hero at ${layout.widthDp}x${layout.heightDp}, text ${scale}x", natural, height(), 0.5f)
                } else {
                    // Short windows: never below the floor for this text size, never taller than the room
                    // the top bar and the bottom bar with the mini player leave, unless the floor needs it.
                    val floor = shortHeroFloor(scale).value
                    val room = layout.heightDp - 80f - bottomChrome.value - 12f
                    assertTrue("Hero at ${layout.widthDp}x${layout.heightDp}, text ${scale}x: ${height()}", height() in floor - 0.5f..maxOf(room, floor) + 0.5f)
                    assertTrue("Hero at ${layout.widthDp}x${layout.heightDp} is shorter than the full hero", height() < natural)
                }
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

    private fun show(current: String, collection: Boolean, scale: Float, height: Dp) {
        style.value = current
        grid.value = collection
        fontScale.floatValue = scale
        cardHeight.value = height
        composeRule.waitForIdle()
    }

    private fun singleCardFailures(label: String, tide: Boolean): List<String> {
        val failures = mutableListOf<String>()
        val hero = rect("short_hero")
        val play = rect("home_hero_play")
        if (play.height < 48f - 0.5f) failures += "$label: Play is ${play.height}dp tall"
        if (!hero.contains(play)) failures += "$label: Play $play leaves the card $hero"
        val title = rect("home_hero_title")
        if (title.top < hero.top - 0.5f) failures += "$label: the title starts above the card"
        if (tide) {
            if (title.overlaps(play)) failures += "$label: the title $title runs under the Play disc $play"
        } else if (layout("home_hero_play_label")?.hasVisualOverflow != false) {
            failures += "$label: the Play label is cut"
        }
        return failures
    }

    private fun Rect.contains(other: Rect) =
        other.left >= left - 0.5f && other.top >= top - 0.5f && other.right <= right + 0.5f && other.bottom <= bottom + 0.5f

    /** The node's own bounds, not cut to its parents, so a button pushed past the card's edge shows as such. */
    private fun rect(tag: String): Rect = requireNotNull(rectOrNull(tag)) { "No $tag" }

    private fun rectOrNull(tag: String): Rect? {
        val node = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        return Rect(node.positionInRoot, node.size.toSize())
    }

    private fun layout(tag: String): TextLayoutResult? {
        val node = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    private companion object {
        val SINGLE_STYLES = listOf(HeroCardStyle.COVER, HeroCardStyle.TIDE, HeroCardStyle.DUOTONE, HeroCardStyle.HALO, HeroCardStyle.BLOCK)
        const val LONG_TITLE = "A Thanksgiving Dinner for \$100? Yes, It's Possible"
    }
}
