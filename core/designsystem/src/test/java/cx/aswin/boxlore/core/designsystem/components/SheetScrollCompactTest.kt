package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.ContentRegions
import cx.aswin.boxlore.core.model.ShareTarget
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sheets reach their last action on short windows: the Galaxy Z Fold 8 cover (also at 130% text), a
 * sideways phone, a phone split-screen half and both halves of a half-open fold. The display is the
 * window itself, so the sheet and the centred panel have their real heights. The share sheet shows
 * its card only where the actions fit beside it, and the name row elsewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetScrollCompactTest(private val case: Case) {
    data class Case(val name: String, val window: BoxloreWindowLayout, val fontScale: Float, val showsCard: Boolean) {
        override fun toString() = name
    }

    @get:Rule val fixture = PhoneGoldenRule(
        PhoneGoldenConfiguration(case.window.widthDp.toInt(), case.window.heightDp.toInt(), dark = false, fontScale = case.fontScale),
    )

    private val compose get() = fixture.compose

    private fun show(content: @Composable () -> Unit) {
        fixture.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides case.window, content = content)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `every share action is reachable and acts once`() {
        val shared = mutableListOf<Pair<Long?, ShareTarget>>()
        var dismissals = 0
        show {
            BoxloreModalBottomSheet(
                onDismissRequest = { dismissals++ },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                ShareSheetBody(
                    id = "ep1",
                    type = "episode",
                    title = "The Buried Bodies Case",
                    subtitle = "Radiolab",
                    imageUrl = null,
                    durationMs = 3_600_000L,
                    currentPositionMs = 65_000L,
                    showTimestampOption = true,
                    episode = null,
                    details = "49 min · 30 Oct",
                    onShare = { _, _, timestampMs, target -> shared += timestampMs to target },
                    onDismissRequest = { dismissals++ },
                    renderPreview = { _, _ -> ImageBitmap(8, 8) },
                )
            }
        }

        if (case.showsCard) {
            compose.onNodeWithTag(SharePreviewTag).assertExists()
        } else {
            compose.onNodeWithTag(SharePreviewTag).assertDoesNotExist()
        }
        compose.onNodeWithText("Start at 1:05").performScrollTo().performClick()
        compose.onNodeWithText("Instagram Story").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(65_000L to ShareTarget.INSTAGRAM_STORY), shared)
        assertEquals(1, dismissals)
    }

    @Test
    fun `the content languages sheet reaches Done`() {
        var dismissals = 0
        show {
            ContentLanguagesSheet(
                country = "IN",
                selectedLanguages = listOf("en", "hi"),
                englishOff = false,
                onLanguagesChange = {},
                onEnglishOffChange = {},
                onDismiss = { dismissals++ },
            )
        }
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, dismissals)
    }

    @Test
    fun `the region picker reaches its last region`() {
        assumeTrue(case.window == BoxloreTestWindows.PHONE_LAND)
        val picked = mutableListOf<String>()
        show { ContentRegionPickerSheet(activeRegion = "US", onSwitchRegion = { picked += it }, onDismiss = {}) }
        val last = ContentRegions.all.last()
        // The list scrolls inside the sheet; the sheet then scrolls the list's end into view.
        compose.onNode(hasText(contentRegionName(last.code, Locale.ENGLISH))).performScrollTo()
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasScrollAction())).performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onNode(hasText(contentRegionName(last.code, Locale.ENGLISH))).assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(listOf(last.code), picked)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases() = listOf(
            Case("cover-443", BoxloreTestWindows.COVER_443, 1f, showsCard = false),
            Case("cover-475-fs130", BoxloreTestWindows.COVER_475, 1.3f, showsCard = true),
            Case("phone-sideways", BoxloreTestWindows.PHONE_LAND, 1f, showsCard = false),
            Case("split-half", BoxloreTestWindows.SPLIT_HALF, 1f, showsCard = false),
            Case("fold-flat", BoxloreTestWindows.FOLD_FLAT, 1f, showsCard = false),
            Case("fold-tabletop", BoxloreTestWindows.FOLD_TABLETOP, 1f, showsCard = false),
            Case("phone", BoxloreTestWindows.PHONE, 1f, showsCard = true),
        ).map { arrayOf<Any>(it) }
    }
}
