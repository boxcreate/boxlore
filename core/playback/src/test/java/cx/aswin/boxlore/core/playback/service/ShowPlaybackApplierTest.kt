package cx.aswin.boxlore.core.playback.service

import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.playback.ShowPlaybackOverrides
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The service's one speed and trim silence path: the playing show's own value wins over the app's, an
 * app change never undoes it, a briefing follows the app, and a stored change to the playing show
 * re-runs the guarded resolution once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShowPlaybackApplierTest {
    private val speeds = mutableListOf<Float>()
    private val trims = mutableListOf<Boolean>()
    private val applier = ShowPlaybackApplier(applySpeed = { speeds += it }, applyTrimSilence = { trims += it })

    @Test
    fun `the app speed and trim silence apply until a show has its own`() {
        applier.onAppSpeed(1.2f)
        applier.onAppTrimSilence(true)

        assertEquals(1.2f, speeds.last())
        assertTrue(trims.last())

        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 1.75f, trimSilence = false))

        assertEquals(1.75f, speeds.last())
        assertFalse(trims.last())
    }

    @Test
    fun `an app change while a show keeps its own values doesn't undo them`() {
        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 2f, trimSilence = true))

        applier.onAppSpeed(1f)
        applier.onAppTrimSilence(false)

        assertEquals(listOf(2f, 2f), speeds)
        assertEquals(listOf(true, true), trims)
    }

    @Test
    fun `a show without its own values, or a briefing, uses the app's`() {
        applier.onAppSpeed(1.5f)
        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 2f))
        applier.onShowResolved(ShowPlaybackOverrides("p2"))
        assertEquals(1.5f, speeds.last())

        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 2f))
        applier.onShowResolved(null)
        assertEquals(1.5f, speeds.last())
        assertEquals(null, applier.activePodcastId)
    }

    @Test
    fun `a stored change to the playing show re-resolves it once, other shows never`() = runTest {
        val stored = MutableStateFlow<ShowPlaybackOverrides?>(null)
        var refreshes = 0
        applier.start(
            scope = backgroundScope,
            appSettings = AppPlaybackSettings(MutableStateFlow(1f), MutableStateFlow(false)),
            observeShow = { podcastId ->
                check(podcastId == "p1")
                stored.filterNotNull()
            },
            onActiveShowChanged = { refreshes++ },
        )
        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 1.5f))
        stored.value = ShowPlaybackOverrides("p1", speed = 1.5f)
        runCurrent()
        assertEquals("the stored row already matches what plays", 0, refreshes)

        stored.value = ShowPlaybackOverrides("p1", speed = 2f)
        runCurrent()
        assertEquals(1, refreshes)

        // The re-run resolves the new value; the same row emitted again changes nothing.
        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 2f))
        stored.value = ShowPlaybackOverrides("p1", speed = 2f, skipBeginningMs = null)
        runCurrent()
        assertEquals(1, refreshes)
        assertTrue(applier.isChangeToActiveShow(ShowPlaybackOverrides("p1", trimSilence = true)))
        assertFalse(applier.isChangeToActiveShow(ShowPlaybackOverrides("p9", trimSilence = true)))
    }

    @Test
    fun `app settings from preferences reach the player through the show's own values`() = runTest {
        val speed = MutableStateFlow(1.25f)
        val trim = MutableStateFlow(false)
        applier.start(backgroundScope, AppPlaybackSettings(speed, trim), observeShow = { emptyFlow() }, onActiveShowChanged = {})
        runCurrent()
        assertEquals(1.25f, speeds.last())

        applier.onShowResolved(ShowPlaybackOverrides("p1", speed = 0.8f))
        speed.value = 1.5f
        trim.value = true
        runCurrent()

        assertEquals(0.8f, speeds.last())
        assertTrue(trims.last())
    }

    @Test
    fun `trimming silence on the local player turns offload off, and back on after`() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            player.applyTrimSilence(true)
            assertTrue(player.skipSilenceEnabled)
            assertEquals(
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode,
            )

            player.applyTrimSilence(false)
            assertFalse(player.skipSilenceEnabled)
            assertEquals(
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED,
                player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode,
            )
        } finally {
            player.release()
        }
    }
}
