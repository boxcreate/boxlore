package cx.aswin.boxlore.feature.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle
import cx.aswin.boxlore.feature.home.components.homeHeroStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On a sideways phone with 3-button navigation at one side and the camera cutout at the other, Home's
 * Settings button sat under the system buttons and the hero started under the cutout. Home's top bar,
 * hero and sections now keep clear of both, while Marquee's cover still runs to the screen edges.
 * Portrait phones are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeHorizontalInsetsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_000, 1_000, false))

    private val window = mutableStateOf(BoxloreTestWindows.PHONE_LAND)
    private val style = mutableStateOf(HeroCardStyle.COVER)
    private var systemInsets: SystemInsets? = SystemInsets.SIDEWAYS_3_BUTTON

    private val podcasts = List(6) { index ->
        TestFixtures.podcast(id = "${index + 1}", title = "Show ${index + 1}", imageUrl = PhoneGoldenRule.ARTWORK_URL)
            .copy(latestEpisode = TestFixtures.episode(id = "e$index", title = "Episode $index", podcastId = "${index + 1}"))
    }
    private val state = HomeUiState(
        heroItems = podcasts.take(3).map { SmartHeroItem(HeroType.RESUME, it, "Continue listening") },
        discoverPodcasts = podcasts,
        subscribedPodcasts = podcasts,
        isRecommendationsLoading = false,
        isEditorialRowsLoading = false,
    )

    @Test
    fun `Home's top bar and hero keep clear of the side buttons and the cutout`() {
        render()
        for (layout in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.COVER_LAND, BoxloreTestWindows.PHONE_LAND)) {
            show(layout)
            val label = "${layout.widthDp}x${layout.heightDp}"
            assertInsideSafeArea("$label Settings", rect("home_settings_button"), layout)
            assertInsideSafeArea("$label top bar", rect("home_top_bar"), layout)
            assertInsideSafeArea("$label hero row", rect("home_hero_row"), layout)
            fixture.compose.onNodeWithTag("home_settings_button").assertHasClickAction()
        }
    }

    @Test
    fun `Marquee's cover reaches the screen edges while its words keep clear`() {
        style.value = HeroCardStyle.MARQUEE
        render()
        show(BoxloreTestWindows.PHONE_LAND)
        val layout = BoxloreTestWindows.PHONE_LAND
        val cover = rect("home_hero_marquee")
        assertEquals("cover start", 0f, cover.left, 0.5f)
        assertEquals("cover end", layout.widthDp, cover.right, 0.5f)
        assertInsideSafeArea("Marquee Play", rect("home_hero_play"), layout)
        assertInsideSafeArea("Marquee chip", rect("home_hero_marquee_chip"), layout)
        // The top bar over the cover keeps clear too, and stays tappable.
        assertInsideSafeArea("Marquee wordmark", rect("home_wordmark"), layout)
        assertInsideSafeArea("Marquee Settings", rect("home_settings_button"), layout)
        val feedback = fixture.compose.onNodeWithContentDescription("Send Feedback", useUnmergedTree = true)
        assertInsideSafeArea("Marquee Feedback", feedback.fetchSemanticsNode().boundsInRoot, layout)
        fixture.compose.onNodeWithTag("home_settings_button").assertHasClickAction()
        fixture.compose.onNodeWithContentDescription("Send Feedback").assertHasClickAction()
    }

    @Test
    fun `portrait phones keep today's gutter`() {
        systemInsets = SystemInsets(statusTop = 24, navBottom = 48)
        window.value = BoxloreTestWindows.PHONE
        render()
        show(BoxloreTestWindows.PHONE)
        assertEquals("hero start", 16f, rect("home_hero_row").left, 0.5f)
        assertEquals("hero end", BoxloreTestWindows.PHONE.widthDp - 16f, rect("home_hero_row").right, 0.5f)
        // The top bar lines up with the cards: the wordmark at their start, Settings at their end.
        assertEquals("wordmark start", rect("home_hero_row").left, rect("home_wordmark").left, 1f)
        assertEquals("Settings end", rect("home_hero_row").right, rect("home_settings_button").right, 1f)
    }

    /** A turn changes Explore's poster columns (2 to 5), which used to send Home back to the hero. */
    @Test
    fun `Home keeps its place when the phone turns, in both directions`() {
        val portrait = SystemInsets(statusTop = 24, navBottom = 48)
        systemInsets = portrait
        window.value = BoxloreTestWindows.PHONE
        render()
        fixture.compose.onNodeWithTag("home_feed_grid").performScrollToKey("your_shows")
        advance()
        val grid = rect("home_feed_grid")
        assertTrue("Home scrolled past the hero", rect("home_hero_row").bottom <= grid.top + 0.5f)
        val before = rect("home_show_selector_mix").top
        for ((layout, insets) in listOf(BoxloreTestWindows.PHONE_LAND to SystemInsets.SIDEWAYS_3_BUTTON, BoxloreTestWindows.PHONE to portrait)) {
            systemInsets = insets
            show(layout)
            val label = "${layout.widthDp}x${layout.heightDp}"
            assertTrue("$label: the hero came back", rect("home_hero_row").bottom <= rect("home_feed_grid").top + 0.5f)
        }
        assertEquals("Your shows moved after turning back", before, rect("home_show_selector_mix").top, 1f)
    }

    private fun assertInsideSafeArea(label: String, bounds: Rect, layout: BoxloreWindowLayout) {
        val insets = systemInsets ?: SystemInsets()
        val start = maxOf(insets.cutoutStart, insets.navStart).toFloat()
        val end = layout.widthDp - insets.navEnd
        assertTrue("$label $bounds starts under the cutout ($start)", bounds.left >= start - 0.5f)
        assertTrue("$label $bounds ends under the side buttons ($end)", bounds.right <= end + 0.5f)
    }

    private fun rect(tag: String): Rect = fixture.compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    private fun render() {
        fixture.prepare()
        fixture.compose.setContent {
            val layout = window.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides layout,
                LocalHeroCardStyle provides homeHeroStyle(style.value, layout.usesTabletLayout),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.size(layout.widthDp.dp, layout.heightDp.dp)) {
                        HomeScreen(
                            uiState = state,
                            playback = HomePlaybackUi(null, null, false),
                            sheets = HomeSheetUi(),
                            callbacks = HomeScreenCallbacks(feed = feedCallbacks()),
                        )
                    }
                }
            }
        }
        advance()
    }

    private fun show(layout: BoxloreWindowLayout) {
        fixture.compose.runOnIdle { window.value = layout }
        advance()
    }

    /** Sends the fake insets again after each change, as the window would, and lets Home settle. */
    private fun advance() {
        systemInsets?.let { insets -> fixture.compose.runOnUiThread { applyFakeSystemInsets(fixture.compose.activity, insets) } }
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(2_000)
        fixture.compose.waitForIdle()
    }

    private fun feedCallbacks() = HomeFeedCallbacks(
        onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = { _: Episode, _: Podcast, _: String? -> },
        onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null, onNavigateToExplore = null,
        onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
        onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {},
        onAiOnboardingClick = {}, onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {},
        onDismissBriefingForever = {}, onFeedbackClick = {},
    )
}
