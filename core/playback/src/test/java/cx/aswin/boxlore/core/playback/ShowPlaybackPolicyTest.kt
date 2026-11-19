package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A show's own speed and trim silence win over the app's; the player saves to the show only while it has one. */
class ShowPlaybackPolicyTest {
    @Test
    fun `a show's own speed wins over the app speed, which applies otherwise`() {
        assertEquals(1.5f, ShowPlaybackPolicy.effectiveSpeed(appSpeed = 1.2f, showSpeed = 1.5f))
        assertEquals(1.2f, ShowPlaybackPolicy.effectiveSpeed(appSpeed = 1.2f, showSpeed = null))
    }

    @Test
    fun `an unusable stored speed falls back to the app speed and both stay in range`() {
        assertEquals(1.2f, ShowPlaybackPolicy.effectiveSpeed(appSpeed = 1.2f, showSpeed = Float.NaN))
        assertEquals(3f, ShowPlaybackPolicy.effectiveSpeed(appSpeed = 1f, showSpeed = 9f))
        assertEquals(0.5f, ShowPlaybackPolicy.effectiveSpeed(appSpeed = 0.1f, showSpeed = null))
    }

    @Test
    fun `a show's own trim silence wins either way, else the app's applies`() {
        assertTrue(ShowPlaybackPolicy.effectiveTrimSilence(appTrimSilence = false, showTrimSilence = true))
        assertFalse(ShowPlaybackPolicy.effectiveTrimSilence(appTrimSilence = true, showTrimSilence = false))
        assertTrue(ShowPlaybackPolicy.effectiveTrimSilence(appTrimSilence = true, showTrimSilence = null))
    }

    @Test
    fun `the player saves to the show only while it has its own value`() {
        assertEquals(ShowPlaybackPolicy.ChangeTarget.SHOW, ShowPlaybackPolicy.changeTarget("p1", 1.5f))
        assertEquals(ShowPlaybackPolicy.ChangeTarget.APP, ShowPlaybackPolicy.changeTarget("p1", null))
        assertEquals(ShowPlaybackPolicy.ChangeTarget.APP, ShowPlaybackPolicy.changeTarget(null, 1.5f))
        assertEquals(ShowPlaybackPolicy.ChangeTarget.APP, ShowPlaybackPolicy.changeTarget("", false))

        val audio = PlaybackAudioState(podcastId = "p1", showSpeed = 2f, showTrimSilence = null, appTrimSilence = true)
        assertTrue(audio.hasShowSpeed)
        assertFalse(audio.hasShowTrimSilence)
        assertTrue(audio.trimSilence)
        assertFalse(PlaybackAudioState().hasShowSpeed)
    }

    @Test
    fun `the offered speeds and their labels match the player's`() {
        assertEquals(listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f), ShowPlaybackPolicy.SPEED_OPTIONS)
        assertEquals("1×", ShowPlaybackPolicy.speedLabel(1f))
        assertEquals("1.25×", ShowPlaybackPolicy.speedLabel(1.25f))
    }

    @Test
    fun `clearing the player keeps the app's trim silence but drops the show's`() {
        val previous = PlayerState(audio = PlaybackAudioState(podcastId = "p1", showSpeed = 2f, showTrimSilence = true, appTrimSilence = true))
        val cleared = PlaybackControlSync.clearedStatePreservingControls(previous, controllerSpeed = 2f)
        assertEquals(PlaybackAudioState(appTrimSilence = true), cleared.audio)
    }
}
