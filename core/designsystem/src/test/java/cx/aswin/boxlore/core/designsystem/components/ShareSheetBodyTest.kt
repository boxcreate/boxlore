package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.share.ShareCardText
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.ShareTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareSheetBodyTest {
    @get:Rule val compose = createComposeRule()

    private var window by mutableStateOf(BoxloreWindowLayout(widthDp = 412f, heightDp = 900f))
    private val drawn = mutableListOf<ShareCardText>()
    private val shared = mutableListOf<Pair<Long?, ShareTarget>>()
    private var dismissals = 0
    private var position by mutableStateOf(65_000L)
    private var linkOpensShow = false
    private var cardDraws = true

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
                    Box(Modifier.width(window.widthDp.dp)) {
                        ShareSheetBody(
                            id = "ep1",
                            type = "episode",
                            title = "The Buried Bodies Case",
                            subtitle = "Radiolab",
                            imageUrl = null,
                            durationMs = 3_600_000L,
                            currentPositionMs = position,
                            showTimestampOption = true,
                            episode = null,
                            onShare = { _, _, timestampMs, target -> shared += timestampMs to target },
                            onDismissRequest = { dismissals++ },
                            renderPreview = { card, _ ->
                                drawn += card
                                if (cardDraws) ImageBitmap(8, 8) else null
                            },
                            opensShow = { linkOpensShow },
                        )
                    }
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
        compose.waitForIdle()
    }

    private fun resize(widthDp: Float, heightDp: Float) {
        window = BoxloreWindowLayout(widthDp, heightDp)
        compose.waitForIdle()
    }

    @Test
    fun `the card is drawn once and kept while the window crosses the tablet gates both ways`() {
        show()
        compose.onNodeWithContentDescription("Share card for The Buried Bodies Case").assertExists()
        assertEquals(1, drawn.size)
        assertNull(drawn.single().moment)

        // Compact → tablet (600dp wide, 480dp+ tall), → split (840dp landscape), and back, either side of each gate.
        for ((w, h) in listOf(599f to 900f, 600f to 900f, 839f to 800f, 840f to 800f, 1280f to 800f, 600f to 800f, 412f to 900f)) {
            resize(w, h)
            compose.onNodeWithTag(SharePreviewTag).assertExists()
        }
        assertEquals(1, drawn.size)
    }

    @Test
    fun `the start time stays on and on the card through resizing, rotation and folding`() {
        show()
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.waitForIdle()
        assertEquals("Starts at 1:05", drawn.last().moment)
        val drawsAfterToggle = drawn.size

        // Phone portrait → landscape (short: the preview steps aside) → unfolded tablet → back to the phone.
        resize(900f, 412f)
        compose.onNodeWithTag(SharePreviewTag).assertDoesNotExist()
        compose.onNodeWithText("The Buried Bodies Case").assertExists()
        resize(840f, 900f)
        resize(412f, 900f)
        compose.onNodeWithTag(SharePreviewTag).assertExists()
        assertEquals("Starts at 1:05", drawn.last().moment)
        assertEquals(drawsAfterToggle + 1, drawn.size)

        compose.onNodeWithText("Send artwork card").performClick()
        assertEquals(listOf(65_000L to ShareTarget.MESSAGE), shared)
        assertEquals(1, dismissals)
    }

    @Test
    fun `the start time survives the sheet being recreated`() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("Instagram Story").performClick()
        assertEquals(listOf(65_000L to ShareTarget.INSTAGRAM_STORY), shared)
    }

    @Test
    fun `short windows show the name row instead of the card`() {
        window = BoxloreWindowLayout(widthDp = 800f, heightDp = 599f)
        show()
        compose.onNodeWithTag(SharePreviewTag).assertDoesNotExist()
        compose.onNodeWithText("Radiolab").assertExists()
        assertEquals(0, drawn.size)
    }

    @Test
    fun `the start time is taken when the switch turns on, so the card and the share agree while playback goes on`() {
        show()
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.waitForIdle()
        position = 125_000L
        compose.waitForIdle()

        compose.onNodeWithText("Start at 1:05").assertExists()
        assertEquals("Starts at 1:05", drawn.last().moment)
        compose.onNodeWithText("Send artwork card").performClick()
        assertEquals(listOf(65_000L to ShareTarget.MESSAGE), shared)
    }

    @Test
    fun `an episode whose link opens its show gets no start time on the card`() {
        linkOpensShow = true
        show()
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.waitForIdle()
        assertEquals(2, drawn.size)
        assertNull(drawn.last().moment)
    }

    @Test
    fun `the sheet still names what is shared when the card can't be drawn`() {
        cardDraws = false
        show()
        compose.onNodeWithTag(SharePreviewTag).assertExists()
        compose.onNodeWithText("The Buried Bodies Case").assertExists()
        compose.onNodeWithText("Radiolab").assertExists()
    }
}
