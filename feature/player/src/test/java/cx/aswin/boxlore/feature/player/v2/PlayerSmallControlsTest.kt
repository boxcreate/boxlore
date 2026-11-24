package cx.aswin.boxlore.feature.player.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.playback.PlaybackRouteState
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.player.CHAPTER_SKELETON_TAG
import cx.aswin.boxlore.feature.player.ChaptersSheetContent
import cx.aswin.boxlore.feature.player.QueueItemDisplay
import cx.aswin.boxlore.feature.player.QueueItemRow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The player's small controls: full touch targets, real buttons and names for screen readers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
class PlayerSmallControlsTest {
    @get:Rule val compose = createComposeRule()

    private fun assertTarget(description: String) {
        // Touch bounds include Material's minimum touch target around a smaller visual button (mdpi: 1px = 1dp).
        val bounds = compose.onNodeWithContentDescription(description).fetchSemanticsNode().touchBoundsInRoot
        assertTrue("$description touch area is ${bounds.width} x ${bounds.height}", bounds.width >= 48f && bounds.height >= 48f)
    }

    @Test
    fun `queue rows give remove and reorder full touch targets and show the episode length`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                QueueItemRow(
                    display = QueueItemDisplay(
                        episode = TestFixtures.episode(
                            title = "The longest night of the year, and why it matters more than you think",
                            podcastTitle = "The Rest Is History: Extended Edition with a very long show name",
                            duration = 2_700,
                        ),
                        podcast = null,
                        dragHandleModifier = androidx.compose.ui.Modifier,
                    ),
                    colorScheme = MaterialTheme.colorScheme,
                    onClick = {},
                    onRemove = {},
                )
            }
        }
        assertTarget("Remove from queue")
        assertTarget("Reorder")
        // A long show name shortens; the length after it shows in full.
        val length = compose.onNodeWithText("·", substring = true, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("Length shown: ${length.config[SemanticsProperties.Text]}", length.config[SemanticsProperties.Text].single().text.matches(Regex(""" · \d+.*""")))
        assertTrue("Length ends inside the row", length.boundsInRoot.right <= 360f && length.boundsInRoot.width > 0f)
    }

    @Test
    fun `chapter rows stand out from the tablet panel they sit on, in light and dark`() {
        val contrasts = mutableListOf<Double>()
        compose.setContent {
            listOf(false, true).forEach { dark ->
                BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                    val scheme = MaterialTheme.colorScheme
                    // The tablet panel is surfaceContainerLow (TabletPlayerSupport).
                    contrasts += androidx.core.graphics.ColorUtils.calculateContrast(
                        cx.aswin.boxlore.feature.player.chapterInactiveRowColor(scheme).toArgb(),
                        scheme.surfaceContainerLow.toArgb(),
                    )
                }
            }
        }
        compose.waitForIdle()
        contrasts.forEach { assertTrue("Row against panel contrast $it", it >= 1.08) }
    }

    @Test
    fun `chapters load as placeholder rows and the sheet closes from an icon button`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                ChaptersSheetContent(emptyList(), flowOf(0L), MaterialTheme.colorScheme, onSeek = {}, onClose = {}, isChaptersLoading = true)
            }
        }
        compose.onNodeWithTag(CHAPTER_SKELETON_TAG).assertExists()
        compose.onNodeWithContentDescription("Close").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertTarget("Close")
    }

    @Test
    fun `the Cast volume slider is named`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                FullPlayerCastHero(
                    FullPlayerCastHeroModel(
                        PlaybackRouteState(isRemote = true, deviceName = "Living room", volume = 5, maximumVolume = 20),
                        HeroDimensions(300.dp, 300.dp),
                        canSkipNext = false,
                        colorScheme = MaterialTheme.colorScheme,
                    ),
                    FullPlayerCastHeroActions({}, {}, {}, {}),
                )
            }
        }
        compose.onNodeWithContentDescription("Cast volume").assertExists()
    }

    @Test
    fun `episode details is a button like View queue`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                NotesPreviewCard("Notes about the episode.", MaterialTheme.colorScheme, onOpenEpisodeInfo = {})
            }
        }
        compose.onNodeWithText("Episode details").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
