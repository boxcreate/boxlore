package cx.aswin.boxlore.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreMiniPlayerVisible
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle
import cx.aswin.boxlore.feature.home.components.homeHeroStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pinning a sixth show tells the listener it can't be pinned. The message used to sit at the very
 * bottom of Home, behind the floating bar and the mini player, so nobody saw it. It now sits above
 * them on phones, the Fold 8 cover and tablets, in every hero style and at large text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1400dp-h1400dp-mdpi")
class HomeSnackbarPlacementTest {
    @get:Rule val composeRule = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val style = mutableStateOf(HeroCardStyle.COVER)
    private val fontScale = mutableFloatStateOf(1f)
    private var chrome = 0.dp

    @Test
    fun `the pin message sits above the bottom bar and mini player at every size, in both directions`() {
        composeRule.setContent {
            val base = LocalDensity.current
            val layout = window.value
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale.floatValue),
                LocalBoxloreWindowLayout provides layout,
                LocalBoxloreMiniPlayerVisible provides true,
                LocalHeroCardStyle provides homeHeroStyle(style.value, layout.usesTabletLayout),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    chrome = appBottomChromeContentPadding(isMiniPlayerVisible = true)
                    val host = remember { SnackbarHostState() }
                    LaunchedEffect(host) { host.showSnackbar(MESSAGE, duration = SnackbarDuration.Indefinite) }
                    Box(Modifier.size(layout.widthDp.dp, layout.heightDp.dp)) {
                        HomeScreen(
                            uiState = HomeUiState(heroItems = emptyList(), discoverPodcasts = emptyList(), isLoading = false),
                            playback = HomePlaybackUi(null, null, false),
                            sheets = HomeSheetUi(),
                            callbacks = HomeScreenCallbacks(feed = feedCallbacks()),
                            snackbarHostState = host,
                        )
                    }
                }
            }
        }
        val steps = listOf(
            Triple(BoxloreTestWindows.PHONE, HeroCardStyle.COVER, 1f),
            Triple(BoxloreTestWindows.COVER_475, HeroCardStyle.COVER, 1f),
            Triple(BoxloreTestWindows.TABLET, HeroCardStyle.COVER, 1f),
            Triple(BoxloreTestWindows.COVER_475, HeroCardStyle.COVER, 1.3f),
            Triple(BoxloreTestWindows.PHONE, HeroCardStyle.MARQUEE, 1f),
            Triple(BoxloreTestWindows.PHONE_LAND, HeroCardStyle.COVER, 1f),
            Triple(BoxloreTestWindows.PHONE, HeroCardStyle.COVER, 1f),
        )
        for ((layout, current, scale) in steps) {
            window.value = layout
            style.value = current
            fontScale.floatValue = scale
            composeRule.waitForIdle()
            val label = "${layout.widthDp}x${layout.heightDp} $current at ${scale}x"
            val message = composeRule.onAllNodesWithText(MESSAGE).onFirst().fetchSemanticsNode().boundsInRoot
            val chromeTop = layout.heightDp - chrome.value
            assertTrue("$label: the message ends at ${message.bottom}, under the bar and mini player from $chromeTop", message.bottom <= chromeTop)
            assertTrue("$label: the message leaves the window", message.left >= 0f && message.right <= layout.widthDp)
            assertTrue("$label: the message is ${message.width}dp wide", message.width <= 480f)
        }
    }

    private fun feedCallbacks() = HomeFeedCallbacks(
        onPodcastClick = { _, _, _, _ -> }, onHeroArrowClick = { _, _ -> }, onEpisodeClick = { _: Episode, _: Podcast, _: String? -> },
        onPlayClick = null, onNavigateToLibrary = null, onNavigateToDownloads = null, onNavigateToExplore = null,
        onToggleSubscription = {}, onTogglePlayback = {}, onSelectCategory = {}, onPodcastSelected = {},
        onPlayMix = {}, onMixModeChanged = {}, onPlayEpisode = { _, _, _ -> }, onImportClick = {},
        onAiOnboardingClick = {}, onDismissImportBanner = {}, onBriefingClick = {}, onDismissBriefing = {},
        onDismissBriefingForever = {}, onFeedbackClick = {},
    )

    private companion object {
        const val MESSAGE = "You can pin up to 5 shows. Unpin one to pin another."
    }
}
