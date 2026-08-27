package cx.aswin.boxlore.feature.player.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastRoutePickerLogicTest {
    @Test
    fun onlyShowsEnabledCastDestinations() {
        assertTrue(
            shouldShowCastRoute(
                isEnabled = true,
                isDefault = false,
                isBluetooth = false,
                matchesSelector = true,
            ),
        )
        assertFalse(
            shouldShowCastRoute(
                isEnabled = false,
                isDefault = false,
                isBluetooth = false,
                matchesSelector = true,
            ),
        )
        assertFalse(
            shouldShowCastRoute(
                isEnabled = true,
                isDefault = true,
                isBluetooth = false,
                matchesSelector = true,
            ),
        )
        assertFalse(
            shouldShowCastRoute(
                isEnabled = true,
                isDefault = false,
                isBluetooth = true,
                matchesSelector = true,
            ),
        )
        assertFalse(
            shouldShowCastRoute(
                isEnabled = true,
                isDefault = false,
                isBluetooth = false,
                matchesSelector = false,
            ),
        )
    }

    @Test
    fun connectionStateTakesPriorityOverDeviceDescription() {
        assertEquals(
            "Connected",
            castRouteSubtitle(
                isSelected = true,
                isConnecting = true,
                description = "Living room TV",
            ),
        )
        assertEquals(
            "Connecting…",
            castRouteSubtitle(
                isSelected = false,
                isConnecting = true,
                description = "Living room TV",
            ),
        )
        assertEquals(
            "Living room TV",
            castRouteSubtitle(
                isSelected = false,
                isConnecting = false,
                description = "Living room TV",
            ),
        )
        assertEquals(
            "Ready to cast",
            castRouteSubtitle(
                isSelected = false,
                isConnecting = false,
                description = null,
            ),
        )
    }

    @Test
    fun connectionCompletesWhenTheChosenRouteIsSelectedAndTheCastSessionIsActive() {
        assertTrue(
            isCastConnectionComplete(
                pendingRouteId = "bedroom",
                routeId = "bedroom",
                isSelected = true,
                hasActiveCastSession = true,
            ),
        )
        assertFalse(
            isCastConnectionComplete(
                pendingRouteId = "bedroom",
                routeId = "living-room",
                isSelected = true,
                hasActiveCastSession = true,
            ),
        )
        assertFalse(
            isCastConnectionComplete(
                pendingRouteId = "bedroom",
                routeId = "bedroom",
                isSelected = true,
                hasActiveCastSession = false,
            ),
        )
    }

    @Test
    fun castHeroUsesOneStableContentMode() {
        assertEquals(
            CastHeroDisplayMode.CAST_CONTROLS,
            resolveCastHeroDisplayMode(
                isRemote = true,
                showInlineTranscript = false,
                showCastControls = true,
            ),
        )
        assertEquals(
            CastHeroDisplayMode.ARTWORK,
            resolveCastHeroDisplayMode(
                isRemote = true,
                showInlineTranscript = false,
                showCastControls = false,
            ),
        )
        assertEquals(
            CastHeroDisplayMode.TRANSCRIPT,
            resolveCastHeroDisplayMode(
                isRemote = true,
                showInlineTranscript = true,
                showCastControls = true,
            ),
        )
        assertEquals(
            CastHeroDisplayMode.ARTWORK,
            resolveCastHeroDisplayMode(
                isRemote = false,
                showInlineTranscript = false,
                showCastControls = true,
            ),
        )
    }

    @Test
    fun castHeroOnlyEnablesNextForARealQueuedEpisode() {
        assertTrue(canSkipFromCastHero("next-episode"))
        assertFalse(canSkipFromCastHero(""))
        assertFalse(canSkipFromCastHero(null))
    }

    @Test
    fun castVolumeUsesDiscreteIntegerPositions() {
        assertEquals(9, castVolumeSliderSteps(minimumVolume = 0, maximumVolume = 10))
        assertEquals(0, castVolumeSliderSteps(minimumVolume = 4, maximumVolume = 5))
        assertEquals(0, snapCastVolume(value = -1f, minimumVolume = 0, maximumVolume = 10))
        assertEquals(6, snapCastVolume(value = 5.6f, minimumVolume = 0, maximumVolume = 10))
        assertEquals(10, snapCastVolume(value = 11f, minimumVolume = 0, maximumVolume = 10))
    }

    @Test
    fun remoteArtworkDoesNotExposeLocalVideoModeButtons() {
        assertFalse(
            shouldShowVideoModeButtons(
                isVideo = false,
                heroMode = CastHeroDisplayMode.ARTWORK,
            ),
        )
        assertTrue(
            shouldShowVideoModeButtons(
                isVideo = true,
                heroMode = CastHeroDisplayMode.ARTWORK,
            ),
        )
    }
}
