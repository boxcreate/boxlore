package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.isRoot
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.PlaybackRouteState
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.core.prefs.FullPlayerStyle
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review renders of every player surface for `-PtabletCaptures=audit` (light only; filter sizes with
 * `-PauditSizes`). Outside audit runs the matrix is empty, so CI and phone goldens never run it.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerAuditCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    private val tabletSize: Boolean get() = fixture.configuration.width >= 600 && fixture.configuration.height >= 480

    @Test fun capture() {
        fixture.assumeAuditCapture()
        if (scene.startsWith("player-tablet-tabs")) assumeTrue("Supporting tabs are tablet-only", tabletSize)
        fixture.prepare()
        when (scene) {
            "player-video-hud" -> setVideoHud()
            else -> setPlayer()
        }
        // Phones start from an empty player state for one frame; let the episode and any sheet arrive.
        fixture.compose.mainClock.advanceTimeBy(600)
        fixture.compose.waitForIdle()
        check(fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty()) { "$scene rendered nothing" }
        fixture.capture(scene, allWindows = scene in SHEET_SCENES)
    }

    private fun setPlayer() {
        val episode = fixtureEpisode()
        val state = fixtureState(episode)
        val playback = mock(PlaybackRepository::class.java)
        `when`(playback.playerState).thenReturn(MutableStateFlow(state))
        val downloads = mock(DownloadRepository::class.java)
        `when`(downloads.isDownloaded(episode.id)).thenReturn(flowOf(false))
        `when`(downloads.isDownloading(episode.id)).thenReturn(flowOf(false))
        val style = if (scene.startsWith("player-bunpod")) FullPlayerStyle.BUNPOD else FullPlayerStyle.STANDARD
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.fullPlayerStyleStream).thenReturn(flowOf(style))
        `when`(prefs.cachedFullPlayerStyle).thenReturn(style)
        // The empty queue with Smart Queue off offers to turn it on.
        `when`(prefs.sameShowQueueOnlyStream).thenReturn(flowOf(scene == "player-queue-empty"))
        val store = PlayerPresentationStore(episodeId = episode.id, ui = sceneUi(), expanded = true, tabletUsed = true)
        fixture.setContent {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                CompositionLocalProvider(LocalPlayerPresentationStore provides store) {
                    FullPlayerV2(
                        FullPlayerDependencies(playback, downloads, prefs),
                        FullPlayerDisplay(MaterialTheme.colorScheme, false, object : NestedScrollConnection {}, true),
                        FullPlayerActions({}, {}, {}, {}),
                    )
                }
            }
        }
    }

    private fun setVideoHud() {
        fixture.setContent {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                VideoFullscreenOverlay(
                    content = VideoFullscreenContent(
                        visible = true,
                        episodeTitle = LONG_TITLE,
                        isPlaying = false,
                        durationMs = DURATION_MS,
                        seekBackwardSeconds = 10,
                        seekForwardSeconds = 30,
                        positionFlow = flowOf(POSITION_MS),
                        controller = null,
                    ),
                    colorScheme = MaterialTheme.colorScheme,
                    actions = VideoFullscreenActions({}, {}, {}, {}, {}),
                )
            }
        }
    }

    private fun sceneUi() = FullPlayerUiState().apply {
        when (scene) {
            "player-speed-sheet", "player-bunpod-speed-sheet" -> showSpeedSheet = true
            "player-sleep-sheet", "player-bunpod-sleep-sheet" -> showSleepSheet = true
            "player-queue-sheet", "player-queue-empty", "player-continuation-banner" -> showQueueSheet = true
            "player-chapters-sheet", "player-chapters-loading" -> showChaptersSheet = true
            "player-transcript-fullscreen", "player-transcript-loading" -> showFullscreenTranscript = true
            "player-transcript-fullscreen-sync-off" -> {
                showFullscreenTranscript = true
                isSyncEnabled = false
            }
            "player-tray-speed" -> quickControlMode = QuickControlMode.SPEED
            "player-tray-sleep" -> quickControlMode = QuickControlMode.SLEEP
            "player-tablet-tabs-chapters" -> supportingTab = PlayerSupportingTab.Chapters
            "player-tablet-tabs-transcript" -> supportingTab = PlayerSupportingTab.Transcript
            "player-tablet-tabs-notes" -> supportingTab = PlayerSupportingTab.Notes
        }
    }

    private fun fixtureEpisode() = Episode(
        transcriptUrl = "https://transcripts.example.invalid/episode.vtt".takeIf { scene == "player-transcript-loading" },
        id = "audit-episode",
        title = LONG_TITLE,
        description = NOTES,
        audioUrl = "https://audio.example.invalid/episode.mp3",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
        podcastImageUrl = PhoneGoldenRule.ARTWORK_URL,
        podcastTitle = SHOW_TITLE,
        podcastId = "audit-show",
        duration = (DURATION_MS / 1_000).toInt(),
    )

    private fun fixtureState(episode: Episode): PlayerState {
        val queue = if (scene == "player-queue-empty") {
            listOf(episode)
        } else {
            listOf(episode) + List(5) { index ->
                episode.copy(id = "queued-$index", title = QUEUE_TITLES[index], podcastTitle = SHOW_TITLE, duration = 1_800 + index * 600)
            }
        }
        return PlayerState(
            currentEpisode = episode,
            currentPodcast = cx.aswin.boxlore.core.testing.TestFixtures.podcast(title = SHOW_TITLE, imageUrl = PhoneGoldenRule.ARTWORK_URL),
            duration = DURATION_MS,
            position = POSITION_MS,
            queue = queue,
            currentChapters = if (scene in setOf("player-queue-empty", "player-chapters-loading")) emptyList() else CHAPTERS,
            isChaptersLoading = scene == "player-chapters-loading",
            currentTranscript = if (scene == "player-transcript-loading") emptyList() else TRANSCRIPT,
            // A 30-minute timer set five minutes ago.
            sleepTimerEnd = if (scene in SLEEP_SCENES) System.currentTimeMillis() + 25 * 60_000L else null,
            sleepTimerPresetMinutes = if (scene in SLEEP_SCENES) 30 else null,
            sameShowContinuation = if (scene == "player-continuation-banner") {
                cx.aswin.boxlore.core.playback.SameShowContinuationState(
                    visible = true,
                    podcastTitle = SHOW_TITLE,
                    availableCount = 3,
                    nextEpisodes = List(3) { index -> episode.copy(id = "more-$index", title = QUEUE_TITLES[index]) },
                )
            } else {
                cx.aswin.boxlore.core.playback.SameShowContinuationState.HIDDEN
            },
            playbackRoute = if (scene == "full-player-casting") {
                PlaybackRouteState(isRemote = true, deviceName = "Living room speaker", volume = 8, maximumVolume = 20)
            } else {
                PlaybackRouteState()
            },
        )
    }

    companion object {
        private const val LONG_TITLE = "A Thanksgiving Dinner for \$100? Yes, It's Possible (and Delicious)."
        private const val SHOW_TITLE = "The Rest Is History: Extended Edition"
        private const val DURATION_MS = 3_480_000L
        private const val POSITION_MS = 1_563_000L
        private const val NOTES = "This week we cook a whole holiday dinner on a budget. We compare prices across " +
            "three supermarkets, test which shortcuts are worth it, and ask a chef what to make ahead. " +
            "Plus: listener questions about leftovers, timing the oven and feeding a crowd without stress."
        private val QUEUE_TITLES = listOf(
            "How we find our way",
            "The longest night of the year, and why it matters more than you think",
            "Short one: a quick update",
            "Interview: the people who map the ocean floor",
            "Mailbag",
        )
        private val CHAPTERS = List(12) { index ->
            Chapter(
                startTime = index * 290.0,
                title = if (index == 6) "Which shortcuts are worth it, and which ones cost you more" else "Chapter ${index + 1}: part of the story",
                img = if (index % 3 == 0) PhoneGoldenRule.ARTWORK_URL else null,
            )
        }
        private val TRANSCRIPT = List(40) { index ->
            TranscriptSegment(index * 45_000L, index * 45_000L + 44_000L, "Line ${index + 1}. We talk about the shopping list, the oven timing and what to make the day before.")
        }
        private val SLEEP_SCENES = setOf("player-sleep-sheet", "player-bunpod-sleep-sheet", "player-tray-sleep")
        private val SHEET_SCENES = setOf(
            "player-speed-sheet",
            "player-bunpod-speed-sheet",
            "player-sleep-sheet",
            "player-bunpod-sleep-sheet",
            "player-queue-sheet",
            "player-queue-empty",
            "player-continuation-banner",
            "player-chapters-sheet",
            "player-chapters-loading",
        )
        private val SCENES = listOf(
            "full-player-rich",
            "player-speed-sheet",
            "player-sleep-sheet",
            "player-queue-sheet",
            "player-queue-empty",
            "player-chapters-sheet",
            "player-transcript-fullscreen",
            "player-video-hud",
            "full-player-casting",
            "player-bunpod",
            "player-bunpod-speed-sheet",
            "player-tablet-tabs-chapters",
            "player-tablet-tabs-transcript",
            "player-tablet-tabs-notes",
            "player-tray-speed",
            "player-tray-sleep",
            "player-bunpod-sleep-sheet",
            "player-continuation-banner",
            "player-chapters-loading",
            "player-transcript-loading",
            "player-transcript-fullscreen-sync-off",
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> {
            if (System.getProperty("boxlore.tabletCaptures") != "audit") return emptyList()
            return PhoneGoldenConfiguration.auditConfigurations().flatMap { configuration ->
                SCENES.map { scene -> arrayOf<Any>(scene, configuration) }
            }
        }
    }
}
