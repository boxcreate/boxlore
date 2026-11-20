package cx.aswin.boxlore.feature.player.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackAudioState
import cx.aswin.boxlore.core.playback.PlaybackRouteState
import cx.aswin.boxlore.core.playback.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player's speed controls: the playing show's own speed with Use app speed, and the Trim silence
 * switch, in the speed sheet and the inline speed tray, across the 600dp and 480dp window gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerSpeedAudioControlsTest {
    @get:Rule val compose = createComposeRule()
    private val layout = mutableStateOf(COMPACT)
    private val audio = mutableStateOf(PlayerSpeedAudio(showTitle = "Darknet Diaries", hasShowSpeed = true))
    private val trims = mutableListOf<Boolean>()
    private var appSpeedUses = 0
    private val speeds = mutableListOf<Float>()

    @Test
    fun `the supporting line says casting first, then the show's own setting`() {
        assertEquals("Skip long pauses", trimSilenceSupportingText(PlayerSpeedAudio()))
        assertEquals("Saved for Serial", trimSilenceSupportingText(PlayerSpeedAudio(showTitle = "Serial", hasShowTrimSilence = true)))
        assertEquals("Saved for this show", trimSilenceSupportingText(PlayerSpeedAudio(hasShowTrimSilence = true)))
        assertEquals(
            "Not available while casting",
            trimSilenceSupportingText(PlayerSpeedAudio(hasShowTrimSilence = true, isCasting = true)),
        )
    }

    @Test
    fun `the speed chip lights up for a custom speed or trimmed silence, not while casting`() {
        assertFalse(speedChipIsActive(1f, PlayerSpeedAudio()))
        assertTrue(speedChipIsActive(1.5f, PlayerSpeedAudio()))
        assertTrue(speedChipIsActive(1f, PlayerSpeedAudio(trimSilence = true)))
        assertFalse(speedChipIsActive(1f, PlayerSpeedAudio(trimSilence = true, isCasting = true)))
    }

    @Test
    fun `the player reads the playing show's own values from the player state`() {
        val state = PlayerState(
            currentPodcast = Podcast(id = "p1", title = "Serial", artist = "", imageUrl = ""),
            audio = PlaybackAudioState(podcastId = "p1", showSpeed = 1.5f, showTrimSilence = true),
            playbackRoute = PlaybackRouteState(isRemote = true),
        )
        assertEquals(
            PlayerSpeedAudio(showTitle = "Serial", hasShowSpeed = true, trimSilence = true, hasShowTrimSilence = true, isCasting = true),
            PlayerSpeedAudio.from(state),
        )
        assertEquals(PlayerSpeedAudio(), PlayerSpeedAudio.from(PlayerState()))
    }

    @Test
    fun `speed sheet keeps the show's speed and trim silence across window changes and runs each action once`() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout.value) {
                BoxLoreTheme {
                    SpeedSheet(
                        currentSpeed = 1.5f,
                        colorScheme = MaterialTheme.colorScheme,
                        onSpeedSelected = { speeds += it },
                        onDismiss = {},
                        audio = audio.value,
                        onUseAppSpeed = { appSpeedUses++ },
                        onTrimSilenceChange = { enabled ->
                            trims += enabled
                            audio.value = audio.value.copy(trimSilence = enabled)
                        },
                    )
                }
            }
        }
        acrossWindowChanges {
            compose.onNodeWithText("Saved for Darknet Diaries").assertExists()
            compose.onNodeWithText("Trim silence").assertExists()
        }
        compose.onNodeWithText("Use app speed").performClick()
        compose.onNodeWithText("Trim silence").performClick()
        compose.waitForIdle()
        acrossWindowChanges { compose.onNodeWithText("Skip long pauses").assertExists() }
        assertEquals(1, appSpeedUses)
        assertEquals(listOf(true), trims)
        assertTrue(audio.value.trimSilence)

        audio.value = PlayerSpeedAudio(trimSilence = true, isCasting = true)
        compose.waitForIdle()
        compose.onNodeWithText("Saved for Darknet Diaries").assertDoesNotExist()
        compose.onNodeWithText("Not available while casting").assertExists()
        compose.onNodeWithText("Trim silence").performClick()
        assertEquals("casting can't trim, so the switch does nothing", listOf(true), trims)
        assertEquals(emptyList<Float>(), speeds)
    }

    @Test
    fun `inline speed tray offers trim silence and the show's app speed across window changes`() {
        compose.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides layout.value) {
                BoxLoreTheme {
                    val shape = MaterialTheme.shapes.medium
                    InlineSpeedSelector(
                        currentSpeed = 1.5f,
                        colorScheme = MaterialTheme.colorScheme,
                        shapes = QuickControlShapes(shape, shape, shape, shape, shape),
                        onBack = {},
                        onSpeedSelected = { speeds += it },
                        audio = InlineSpeedAudio(
                            state = audio.value,
                            onUseAppSpeed = { appSpeedUses++ },
                            onTrimSilenceChange = { enabled ->
                                trims += enabled
                                audio.value = audio.value.copy(trimSilence = enabled, hasShowSpeed = false)
                            },
                        ),
                    )
                }
            }
        }
        acrossWindowChanges {
            compose.onNodeWithText("Use app speed").assertExists()
            compose.onNodeWithText("Speed saved for this show").assertExists()
        }
        compose.onNodeWithText("Use app speed").performClick()
        compose.onNodeWithText("Trim silence").performClick()
        compose.waitForIdle()
        acrossWindowChanges {
            compose.onNodeWithText("Use app speed").assertDoesNotExist()
            compose.onNodeWithText("Trim silence").assertExists()
        }
        assertEquals(1, appSpeedUses)
        assertEquals(listOf(true), trims)

        audio.value = audio.value.copy(isCasting = true)
        compose.waitForIdle()
        compose.onNodeWithText("Not available while casting").assertExists()
        compose.onNodeWithText("Trim silence").assertIsNotEnabled()
        assertEquals(emptyList<Float>(), speeds)
    }

    /** Compact → tablet → short wide → compact, crossing both gates each way. */
    private fun acrossWindowChanges(assertion: () -> Unit) {
        for (step in listOf(COMPACT, TABLET, SHORT_WIDE, TABLET, COMPACT)) {
            layout.value = step
            compose.waitForIdle()
            assertion()
        }
    }

    private companion object {
        val COMPACT = BoxloreWindowLayout(599f, 780f)
        val TABLET = BoxloreWindowLayout(1_000f, 780f)
        val SHORT_WIDE = BoxloreWindowLayout(1_000f, 479f)
    }
}
