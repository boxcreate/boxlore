package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * One live Lore deck (the real [LearnScreen] over a mocked ViewModel) in a test window that tests
 * can change while it runs: [window], the shell's [pane] (null for a page filling the window), the
 * text [fontScale] and the screen's [state].
 */
internal class LoreDeckHost(private val fixture: PhoneGoldenRule) {
    val window = mutableStateOf(BoxloreTestWindows.PHONE)
    val pane = mutableStateOf<BoxloreLayoutBounds?>(null)
    val fontScale = mutableFloatStateOf(1f)

    /** Screen density for the deck; 0 keeps the test window's own. */
    val density = mutableFloatStateOf(0f)
    val state = MutableStateFlow<LearnUiState>(LearnUiState.Success(cards()))
    val playerState = MutableStateFlow(PlayerState())
    val viewModel: LearnViewModel = mock(LearnViewModel::class.java)
    var historyOpened = 0
        private set

    fun render(miniPlayer: Boolean = false) {
        `when`(viewModel.uiState).thenReturn(state)
        `when`(viewModel.artworkAccentColors).thenReturn(MutableStateFlow(emptyMap()))
        val playback = mock(PlaybackRepository::class.java)
        `when`(playback.playerState).thenReturn(playerState)
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = !state.value.animatesForever()
        fixture.compose.setContent {
            val size = window.value
            val bounds = pane.value
            val width = bounds?.width ?: size.widthDp
            val height = bounds?.height ?: size.heightDp
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides width,
                LocalBoxlorePaneBounds provides bounds,
                LocalDensity provides Density(density.floatValue.takeIf { it > 0f } ?: LocalDensity.current.density, fontScale.floatValue),
            ) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(width.dp, height.dp).testTag(ROOT)) {
                        Surface {
                            LearnScreen(
                                viewModel = viewModel,
                                playbackRepository = playback,
                                bottomContentPadding = appBottomChromeContentPadding(isMiniPlayerVisible = miniPlayer),
                                onEpisodeClick = {},
                                onQueueEpisode = {},
                                onPodcastClick = { _, _, _, _ -> },
                                onNavigateToHistory = { historyOpened++ },
                            )
                        }
                    }
                }
            }
        }
        settle()
    }

    /** Shows [next]; the clock pauses while it is Loading, whose placeholder never stops moving. */
    fun show(next: LearnUiState) {
        fixture.compose.mainClock.autoAdvance = false
        state.value = next
        settle()
        fixture.compose.mainClock.autoAdvance = !next.animatesForever()
        if (!next.animatesForever()) fixture.compose.waitForIdle()
    }

    private fun LearnUiState.animatesForever() = this is LearnUiState.Loading

    fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeBy(SETTLE_MS)
        fixture.compose.waitForIdle()
    }

    fun moveTo(next: BoxloreWindowLayout, nextPane: BoxloreLayoutBounds? = null) {
        fixture.compose.runOnIdle {
            window.value = next
            pane.value = nextPane
        }
        settle()
    }

    fun rootBounds(): DpRect = bounds(fixture.compose.onNode(hasTestTag(ROOT)))

    fun activeCard(): SemanticsNodeInteraction = fixture.compose.onNode(hasTestTag(ACTIVE_CARD), useUnmergedTree = true)

    /** A node inside the active card, found in the unmerged tree (the card merges its text). */
    fun onActiveCard(matcher: SemanticsMatcher): SemanticsNodeInteraction =
        fixture.compose.onNode(matcher and hasAnyAncestor(hasTestTag(ACTIVE_CARD)), useUnmergedTree = true)

    fun play(): SemanticsNodeInteraction = onActiveCard(hasContentDescription(string(R.string.lore_play_episode)))

    fun activeText(text: String): SemanticsNodeInteraction = onActiveCard(hasText(text))

    /** Drags the active card's text as far as it goes: up to its end ([toEnd]) or down to its start. */
    fun dragText(toEnd: Boolean) {
        repeat(TEXT_DRAGS) {
            // From the card's middle, which is its text: a part of the text may be out of view.
            activeCard().performTouchInput { if (toEnd) swipeUp(startY = centerY, endY = top) else swipeDown(startY = centerY, endY = bottom) }
            settle()
        }
    }

    /** [node]'s whole bounds, including any part scrolled or clipped out of view. */
    fun unclipped(node: SemanticsNodeInteraction): DpRect = node.getUnclippedBoundsInRoot()

    /** Scrolls [node] into view when its text scrolls; text that fits has nothing to scroll. */
    fun reveal(node: SemanticsNodeInteraction) {
        val scrolls = node.fetchSemanticsNode().let { semantics ->
            generateSequence(semantics.parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }
        }
        if (scrolls) node.performScrollTo()
    }

    fun string(id: Int): String = fixture.compose.activity.getString(id)

    fun bounds(node: SemanticsNodeInteraction): DpRect = with(fixture.compose.density) {
        val rect = node.fetchSemanticsNode().boundsInRoot
        DpRect(rect.left.toDp(), rect.top.toDp(), rect.right.toDp(), rect.bottom.toDp())
    }

    companion object {
        const val ROOT = "lore_test_root"
        private const val TEXT_DRAGS = 6
        const val ACTIVE_CARD = "lore_active_card"
        private const val SETTLE_MS = 1_600L

        /** Four cards, each with its own question, so the active one can be told apart. */
        fun cards(question: (Int) -> String = { "Why do familiar places feel different when we return? ($it)" }, explanation: String? = "Discover how memory and attention shape the places we call home.") =
            List(4) { index ->
                LearnCuriosityCard(
                    "episode-$index", question(index), explanation, 8, "How we find our way", "Curious world",
                    PhoneGoldenRule.ARTWORK_URL, PhoneGoldenRule.ARTWORK_URL, "1", null, 3_600, null,
                )
            }
    }
}
