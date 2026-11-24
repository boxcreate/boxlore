package cx.aswin.boxlore.feature.player.v2

import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.player.R
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
            UiText.res(R.string.player_cast_connected),
            castRouteSubtitle(
                isConnected = true,
                isConnecting = true,
                description = "Living room TV",
            ),
        )
        assertEquals(
            UiText.res(R.string.player_cast_connecting),
            castRouteSubtitle(
                isConnected = false,
                isConnecting = true,
                description = "Living room TV",
            ),
        )
        assertEquals(
            UiText.raw("Living room TV"),
            castRouteSubtitle(
                isConnected = false,
                isConnecting = false,
                description = "Living room TV",
            ),
        )
        assertEquals(
            UiText.res(R.string.player_cast_ready),
            castRouteSubtitle(
                isConnected = false,
                isConnecting = false,
                description = null,
            ),
        )
    }

    @Test
    fun `a picked TV finishes connecting once its Cast session connects`() {
        // Regression: the sheet waited for the picked row to report itself selected, which never
        // happens with media transfer on, so it stayed on "Connecting…" while the TV played.
        val livingRoom = ConnectedCastDevice(id = "living-room-id", name = "Living room TV")

        assertEquals(CastPickProgress.CONNECTING, castPickProgress(deviceIdAtPick = null, connected = null, startFailed = false))
        assertEquals(CastPickProgress.CONNECTED, castPickProgress(deviceIdAtPick = null, connected = livingRoom, startFailed = false))
    }

    @Test
    fun `switching TVs waits for the new one and a failed start ends the wait`() {
        val livingRoom = ConnectedCastDevice(id = "living-room-id", name = "Living room TV")
        val bedroom = ConnectedCastDevice(id = "bedroom-id", name = "Bedroom TV")

        assertEquals(CastPickProgress.CONNECTING, castPickProgress(deviceIdAtPick = livingRoom.id, connected = livingRoom, startFailed = false))
        assertEquals(CastPickProgress.CONNECTING, castPickProgress(deviceIdAtPick = livingRoom.id, connected = null, startFailed = false))
        assertEquals(CastPickProgress.CONNECTED, castPickProgress(deviceIdAtPick = livingRoom.id, connected = bedroom, startFailed = false))
        assertEquals(CastPickProgress.FAILED, castPickProgress(deviceIdAtPick = livingRoom.id, connected = null, startFailed = true))
    }

    @Test
    fun `the connected TV is the row with the session's Cast device`() {
        val livingRoom = ConnectedCastDevice(id = "living-room-id", name = "Living room TV")

        assertTrue(isConnectedCastRoute(routeDeviceId = "living-room-id", routeName = "Living room TV", connected = livingRoom))
        assertFalse(isConnectedCastRoute(routeDeviceId = "bedroom-id", routeName = "Living room TV", connected = livingRoom))
        // A row without a Cast device falls back to its name.
        assertTrue(isConnectedCastRoute(routeDeviceId = null, routeName = "Living room TV", connected = livingRoom))
        assertFalse(isConnectedCastRoute(routeDeviceId = "living-room-id", routeName = "Living room TV", connected = null))
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

    @Test
    fun castButtonTintFollowsThemeColorsAndActiveState() {
        val darkSurfaceTint = Color(0xFFE6DFCF)
        val lightSurfaceTint = Color(0xFF1E1B16)
        val primaryAccent = Color(0xFFFF9E80)

        assertEquals(
            darkSurfaceTint,
            resolveCastButtonTint(
                enabled = true,
                isCasting = false,
                tint = darkSurfaceTint,
                activeTint = primaryAccent,
            ),
        )

        assertEquals(
            lightSurfaceTint,
            resolveCastButtonTint(
                enabled = true,
                isCasting = false,
                tint = lightSurfaceTint,
                activeTint = primaryAccent,
            ),
        )

        assertEquals(
            primaryAccent,
            resolveCastButtonTint(
                enabled = true,
                isCasting = true,
                tint = darkSurfaceTint,
                activeTint = primaryAccent,
            ),
        )
        assertEquals(
            primaryAccent,
            resolveCastButtonTint(
                enabled = true,
                isCasting = true,
                tint = lightSurfaceTint,
                activeTint = primaryAccent,
            ),
        )

        assertEquals(
            darkSurfaceTint.copy(alpha = 0.38f),
            resolveCastButtonTint(
                enabled = false,
                isCasting = false,
                tint = darkSurfaceTint,
                activeTint = primaryAccent,
            ),
        )
    }

    @Test
    fun `Android 16 and older open the Cast sheet without asking`() {
        assertEquals(CastButtonAction.SHOW_SHEET, castButtonAction(sdkInt = 36, hasLocalNetworkAccess = false))
        assertEquals(CastButtonAction.SHOW_SHEET, castButtonAction(sdkInt = 31, hasLocalNetworkAccess = false))
    }

    @Test
    fun `Android 17 asks for local network access before the sheet searches for speakers`() {
        assertEquals(CastButtonAction.REQUEST_LOCAL_NETWORK, castButtonAction(sdkInt = 37, hasLocalNetworkAccess = false))
        assertEquals(CastButtonAction.SHOW_SHEET, castButtonAction(sdkInt = 37, hasLocalNetworkAccess = true))
        assertEquals(CastButtonAction.REQUEST_LOCAL_NETWORK, castButtonAction(sdkInt = 38, hasLocalNetworkAccess = false))
    }

    @Test
    fun `declining local network access opens the system picker instead`() {
        assertEquals(CastButtonAction.SHOW_SHEET, castActionAfterLocalNetworkRequest(granted = true))
        assertEquals(CastButtonAction.SHOW_SYSTEM_PICKER, castActionAfterLocalNetworkRequest(granted = false))
    }
}
