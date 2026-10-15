package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletPlayerContentTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(673, 841, false))
    private val window = mutableStateOf(BoxloreWindowLayout(673f, 841f))
    private val episode = TestFixtures.episode(title = "A curious journey", description = "Notes about the places we discover.", imageUrl = PhoneGoldenRule.ARTWORK_URL)
    private val podcast = TestFixtures.podcast(imageUrl = PhoneGoldenRule.ARTWORK_URL)
    private val playback = mock(PlaybackRepository::class.java)
    private lateinit var view: View
    private var foreground = 0

    @Test
    fun `posture changes keep artwork controls and tabs inside their physical regions`() {
        render()
        fixture.compose.runOnIdle { window.value = tabletop() }
        advance()
        val art = fixture.compose.onNodeWithTag("tablet_player_artwork_region").fetchSemanticsNode().boundsInRoot
        val controls = fixture.compose.onNodeWithTag("tablet_player_controls_region").fetchSemanticsNode().boundsInRoot
        val title = fixture.compose.onNodeWithText(episode.title).fetchSemanticsNode().boundsInRoot
        val tabs = fixture.compose.onNodeWithText("Up next").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= 410f)
        assertTrue("Tabletop support tabs remain visible at rest", tabs.top >= 431f && tabs.bottom <= 841f)
        scrollToSupport()
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").fetchSemanticsNode().boundsInRoot
        assertTrue(art.bottom <= 410f)
        assertTrue("Controls: $controls; artwork: $art; support: $support", controls.top >= 431f)
        assertTrue(support.top >= 431f)
        assertTrue(support.bottom <= 841f)
    }

    @Test
    fun `support tabs reuse chapter seeking and notes without opening a sheet`() {
        render()
        fixture.compose.onNodeWithText("Chapters").performClick()
        advance()
        fixture.compose.onNodeWithText("Chapter two").performClick()
        verify(playback).seekTo(120_000L)
        fixture.compose.onNodeWithText("Notes").performClick()
        advance()
        fixture.compose.onNodeWithText(episode.description).assertExists()
    }

    @Test
    fun `context pane opens immersive presentation through its owner callback`() {
        var opens = 0
        window.value = BoxloreWindowLayout(1_280f, 800f)
        render(contextPane = true, onOpen = { opens++ })
        fixture.compose.onNodeWithContentDescription("Open full-screen player").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun `bunpod keeps its title above a tabletop fold and its transport below`() {
        window.value = tabletop()
        render(style = FullPlayerStyle.BUNPOD)
        val title = fixture.compose.onNodeWithText(episode.title).fetchSemanticsNode().boundsInRoot
        val controls = fixture.compose.onNodeWithTag("tablet_player_controls_region").fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= 410f)
        assertTrue(controls.top >= 431f)
    }

    @Test
    fun `tabletop video mode controls follow the artwork without crossing the hinge`() {
        window.value = tabletop()
        render(video = true)
        val art = fixture.compose.onNodeWithContentDescription("Album Art", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val videoAction = fixture.compose.onNodeWithText("Audio Only").fetchSemanticsNode().boundsInRoot
        assertTrue(videoAction.top >= art.bottom)
        assertTrue(videoAction.bottom <= 410f)
    }

    @Test
    fun `empty queue message remains readable in the short tabletop supporting region`() {
        window.value = tabletop()
        render(emptyQueue = true)
        scrollToSupport()
        val message = fixture.compose.onNodeWithText("Queue is empty").fetchSemanticsNode().boundsInRoot
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").fetchSemanticsNode().boundsInRoot
        assertTrue(message.height >= 16f)
        assertTrue(message.top >= support.top && message.bottom <= support.bottom)
    }

    @Test
    fun `context player exposes every control before supporting content`() {
        window.value = BoxloreWindowLayout(1_280f, 800f)
        render(contextPane = true)
        fixture.compose.onNodeWithContentDescription("Complete", useUnmergedTree = true).assertIsDisplayed()
        val lastControl = fixture.compose.onNodeWithContentDescription("Complete", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val support = fixture.compose.onNodeWithTag("tablet_player_support_region").fetchSemanticsNode().boundsInRoot
        assertTrue(lastControl.bottom <= support.top)
    }

    @Test
    fun `dark player header paints artwork foreground despite a black inherited content color`() {
        render(dark = true)
        val nodes = listOf(
            fixture.compose.onNodeWithText("Now playing"),
            fixture.compose.onNodeWithContentDescription("Share episode", useUnmergedTree = true),
            fixture.compose.onNodeWithContentDescription("Minimize player", useUnmergedTree = true),
        )
        nodes.forEach { node ->
            val bounds = node.fetchSemanticsNode().boundsInRoot
            fixture.compose.runOnIdle {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val width = bounds.width.toInt()
                val pixels = IntArray(width * bounds.height.toInt())
                bitmap.getPixels(pixels, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, bounds.height.toInt())
                assertTrue("Header must paint its own foreground", pixels.any { it == foreground })
                bitmap.recycle()
            }
        }
    }

    @Test
    fun `tablet transcript keeps its tab and notifies the window owner on expand and close`() {
        val requests = mutableListOf<Pair<PlayerFullscreenOverlay, Boolean>>()
        render(transcript = true, fullscreenRequest = { overlay, visible -> requests += overlay to visible })
        fixture.compose.onNodeWithText("Transcript").performClick()
        advance()
        fixture.compose.onNodeWithContentDescription("Open full-screen transcript").performClick()
        advance()
        fixture.compose.onNodeWithContentDescription("Close").performClick()
        advance()
        assertEquals(listOf(PlayerFullscreenOverlay.Transcript to true, PlayerFullscreenOverlay.Transcript to false), requests)
        fixture.compose.onNodeWithContentDescription("Open full-screen transcript").assertExists()
        fixture.compose.onNodeWithText("Words that follow the episode").assertExists()
    }

    @Test
    fun `live player restores its inline transcript across width and height gates`() {
        val store = PlayerPresentationStore(episode.id, FullPlayerUiState().apply { showInlineTranscript = true })
        window.value = BoxloreWindowLayout(599f, 841f)
        render(transcript = true, presentationStore = store)
        fixture.compose.onNodeWithContentDescription("Open fullscreen transcript").assertExists()

        listOf(
            BoxloreWindowLayout(600f, 841f),
            BoxloreWindowLayout(599f, 841f),
            BoxloreWindowLayout(840f, 480f),
            BoxloreWindowLayout(840f, 479f),
            tabletop(),
            BoxloreWindowLayout(599f, 841f),
        ).forEach { layout ->
            fixture.compose.runOnIdle { window.value = layout }
            advance()
            assertEquals("The same episode state survives resizing", store.ui, store.forEpisode(episode.id))
            assertEquals("Inline transcript at ${layout.widthDp} x ${layout.heightDp}", !layout.usesTabletLayout, store.ui.showInlineTranscript)
            fixture.compose.onNodeWithContentDescription(if (layout.usesTabletLayout) "Open full-screen transcript" else "Open fullscreen transcript").assertExists()
        }
    }

    @Suppress("LongParameterList")
    private fun render(style: String = FullPlayerStyle.STANDARD, contextPane: Boolean = false, onOpen: () -> Unit = {}, video: Boolean = false, emptyQueue: Boolean = false, dark: Boolean = false, transcript: Boolean = false, fullscreenRequest: ((PlayerFullscreenOverlay, Boolean) -> Unit)? = null, presentationStore: PlayerPresentationStore? = null) {
        val downloads = mock(DownloadRepository::class.java)
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(playback.playerState).thenReturn(
            MutableStateFlow(
                PlayerState(
                    currentEpisode = if (video) episode.copy(enclosureType = "video/mp4") else episode,
                    currentPodcast = podcast,
                    duration = 3_600_000L,
                    queue = if (emptyQueue) listOf(episode) else listOf(episode, episode.copy(id = "next", title = "Next journey")),
                    currentChapters = listOf(Chapter(0.0, "Chapter one"), Chapter(120.0, "Chapter two")),
                    currentTranscript = if (transcript) listOf(TranscriptSegment(0, 120_000, "Words that follow the episode")) else emptyList(),
                ),
            ),
        )
        `when`(downloads.isDownloaded(episode.id)).thenReturn(flowOf(false))
        `when`(downloads.isDownloading(episode.id)).thenReturn(flowOf(false))
        `when`(prefs.fullPlayerStyleStream).thenReturn(flowOf(style))
        `when`(prefs.cachedFullPlayerStyle).thenReturn(style)
        `when`(prefs.sameShowQueueOnlyStream).thenReturn(flowOf(false))
        fixture.prepare()
        fixture.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides window.value,
                LocalPlayerPresentationStore provides presentationStore,
                LocalPlayerFullscreenRequest provides fullscreenRequest,
                LocalBoxlorePaneBounds provides if (contextPane) BoxloreLayoutBounds(800f, 0f, 1_273f, 800f) else null,
            ) {
                BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                    foreground = MaterialTheme.colorScheme.onSurface.toArgb()
                    CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    Box(if (contextPane) Modifier.width(473.dp).height(800.dp) else Modifier) {
                        FullPlayerV2(
                            FullPlayerDependencies(playback, downloads, prefs),
                            FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true, isContextPane = contextPane),
                            FullPlayerActions({}, {}, {}, {}, onOpenImmersivePlayer = onOpen),
                        )
                    }
                    }
                }
            }
        }
        advance()
    }

    private fun scrollToSupport() {
        fixture.compose.mainClock.autoAdvance = true
        try {
            fixture.compose.onNodeWithTag("tablet_player_support_region").performScrollTo()
            fixture.compose.waitForIdle()
        } finally {
            fixture.compose.mainClock.autoAdvance = false
        }
        advance()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }

    private fun tabletop() = BoxloreWindowLayout(
        673f,
        841f,
        listOf(
            BoxloreFold(
                BoxloreFoldState.HalfOpened,
                BoxloreFoldOrientation.Horizontal,
                BoxloreLayoutBounds(0f, 410f, 673f, 431f),
                true,
                true,
            ),
        ),
    )
}
