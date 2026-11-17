package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.playback.CarSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android Auto asking `:app` for one new-episode check per drive ([CarSignals.onCarNewEpisodesOpened]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoBrowseNewEpisodeCheckTest {
    private lateinit var context: Context
    private lateinit var host: AutoBrowseLibraryHost
    private lateinit var carCallback: AutoBrowseLibraryCallback
    private lateinit var restoreAnalytics: () -> Unit
    private var elapsedMs = 0L

    @Before
    fun setUp() {
        restoreAnalytics = AnalyticsHelper.installRecordingSink(mutableListOf())
        context = ApplicationProvider.getApplicationContext()
        host = mock(AutoBrowseLibraryHost::class.java)
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(host.asContext()).thenReturn(context)
        `when`(host.serviceScope).thenReturn(CoroutineScope(Dispatchers.Unconfined))
        `when`(host.database).thenReturn(database)
        val commandButton = mock(androidx.media3.session.CommandButton::class.java)
        `when`(host.seekBackAction).thenReturn(commandButton)
        `when`(host.seekForwardAction).thenReturn(commandButton)
        `when`(host.markCompleteAction).thenReturn(commandButton)
        `when`(host.skipNextAction).thenReturn(commandButton)
        // The car check runs in place, so each browse has finished it before the next line.
        carCallback =
            AutoBrowseLibraryCallback(
                host = host,
                mediaResolver = mock(AutoMediaResolver::class.java),
                resumptionHandler = mock(AutoPlaybackResumptionHandler::class.java),
                treeBuilder = mock(AutoBrowseTreeBuilder::class.java),
                io = Dispatchers.Unconfined,
                elapsedMs = { elapsedMs },
            )
    }

    @After
    fun tearDown() {
        restoreAnalytics()
    }

    private fun controllerFrom(packageName: String): MediaSession.ControllerInfo {
        val controller = mock(MediaSession.ControllerInfo::class.java)
        `when`(controller.packageName).thenReturn(packageName)
        return controller
    }

    private fun inCarMode(block: () -> Unit) {
        val uiMode = context.getSystemService(android.app.UiModeManager::class.java)
        uiMode.enableCarMode(0)
        try {
            block()
        } finally {
            uiMode.disableCarMode(0)
        }
    }

    /**
     * Counts [CarSignals.onCarNewEpisodesOpened] and [CarSignals.onCarDriveEnded] while [block] runs;
     * [starts] answers whether `:app` started the check it was asked for.
     */
    private fun recordingNewEpisodeChecks(
        starts: () -> Boolean = { true },
        block: (asked: () -> Int, ended: () -> Int) -> Unit,
    ) {
        val asked = java.util.concurrent.atomic.AtomicInteger()
        val ended = java.util.concurrent.atomic.AtomicInteger()
        val previousOpened = CarSignals.onCarNewEpisodesOpened
        val previousEnded = CarSignals.onCarDriveEnded
        CarSignals.onCarNewEpisodesOpened = {
            asked.incrementAndGet()
            starts()
        }
        CarSignals.onCarDriveEnded = { ended.incrementAndGet() }
        try {
            block(asked::get, ended::get)
        } finally {
            CarSignals.onCarNewEpisodesOpened = previousOpened
            CarSignals.onCarDriveEnded = previousEnded
        }
    }

    @Test
    fun `the car opening New episodes or Shows asks once per drive for a new-episode check`() {
        // Before, the car's New episodes only ever showed what boxlore saved while it was last on screen.
        recordingNewEpisodeChecks { asked, _ ->
            val session = mock(MediaLibrarySession::class.java)
            val auto = controllerFrom("com.google.android.projection.gearhead")

            // Android Auto also lists media apps with no car connected.
            carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
            assertEquals(0, asked())

            inCarMode {
                carCallback.onGetChildren(session, auto, AutoBrowseContract.ROOT_ID, 0, 10, null)
                carCallback.onGetChildren(session, auto, AutoBrowseContract.DOWNLOADS_ID, 0, 10, null)
                carCallback.onGetChildren(session, controllerFrom("com.android.systemui"), AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
                assertEquals(0, asked())

                carCallback.onGetChildren(session, auto, AutoBrowseContract.OPEN_PREFIX + AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
                repeat(3) { carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null) }
                carCallback.onGetChildren(session, auto, AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID, 0, 10, null)
                assertEquals(1, asked())
            }
        }
    }

    @Test
    fun `Shows asks for the check too, and the drive ending lets the next drive ask again`() {
        recordingNewEpisodeChecks { asked, ended ->
            val session = mock(MediaLibrarySession::class.java)
            val player = mock(Player::class.java)
            `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
            `when`(session.player).thenReturn(player)
            val auto = controllerFrom("com.google.android.projection.gearhead")

            inCarMode {
                carCallback.onConnect(session, auto)
                carCallback.onGetChildren(session, auto, AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID, 0, 10, null)
                assertEquals(1, asked())
                assertEquals(0, ended())

                carCallback.onDisconnected(session, auto)
                assertEquals(1, ended())

                carCallback.onConnect(session, auto)
                carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
                assertEquals(2, asked())
                carCallback.onDisconnected(session, auto)
                assertEquals(2, ended())

                // A drive that never opened those lists has no check to end.
                carCallback.onConnect(session, auto)
                carCallback.onGetChildren(session, auto, AutoBrowseContract.DOWNLOADS_ID, 0, 10, null)
                carCallback.onDisconnected(session, auto)
                assertEquals(2, asked())
                assertEquals(2, ended())
            }
        }
    }

    @Test
    fun `a check turned down early in a drive is asked for again later in the same drive`() {
        // A drive that begins within half an hour of the last car check is turned down at first;
        // however long it lasts, it must still get its check once the half hour has passed.
        var startsCheck = false
        recordingNewEpisodeChecks(starts = { startsCheck }) { asked, ended ->
            val session = mock(MediaLibrarySession::class.java)
            val player = mock(Player::class.java)
            `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
            `when`(session.player).thenReturn(player)
            val auto = controllerFrom("com.google.android.projection.gearhead")

            inCarMode {
                carCallback.onConnect(session, auto)
                carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
                assertEquals(1, asked())

                // Browsing again straight away asks nothing.
                elapsedMs += 1_000L
                repeat(3) { carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null) }
                assertEquals(1, asked())

                // Later in the same drive, once the half hour has passed, the check starts.
                startsCheck = true
                elapsedMs += 30 * 60_000L
                carCallback.onGetChildren(session, auto, AutoBrowseContract.LIBRARY_SUBSCRIPTIONS_ID, 0, 10, null)
                assertEquals(2, asked())
                elapsedMs += 30 * 60_000L
                carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
                assertEquals(2, asked())

                carCallback.onDisconnected(session, auto)
                assertEquals(1, ended())
            }
        }
    }

    @Test
    fun `a drive whose only ask was turned down has no check to end`() {
        recordingNewEpisodeChecks(starts = { false }) { asked, ended ->
            val session = mock(MediaLibrarySession::class.java)
            val player = mock(Player::class.java)
            `when`(player.availableCommands).thenReturn(Player.Commands.EMPTY)
            `when`(session.player).thenReturn(player)
            val auto = controllerFrom("com.google.android.projection.gearhead")

            inCarMode {
                carCallback.onConnect(session, auto)
                carCallback.onGetChildren(session, auto, AutoBrowseContract.HOME_NEW_EPISODES_ID, 0, 10, null)
                carCallback.onDisconnected(session, auto)
                assertEquals(1, asked())
                assertEquals(0, ended())
            }
        }
    }
}
