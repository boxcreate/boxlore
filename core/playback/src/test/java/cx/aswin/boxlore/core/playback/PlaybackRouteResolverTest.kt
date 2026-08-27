package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackRouteResolverTest {
    @Test
    fun `local route has no remote controls`() {
        val route =
            PlaybackRouteResolver.resolveState(
                isRemote = false,
                deviceName = null,
                volume = 4,
                minimumVolume = 0,
                maximumVolume = 10,
                isMuted = false,
            )

        assertFalse(route.isRemote)
        assertFalse(route.canControlVolume)
        assertEquals("Cast device", route.displayName)
    }

    @Test
    fun `remote route preserves device identity and clamps volume bounds`() {
        val route =
            PlaybackRouteResolver.resolveState(
                isRemote = true,
                deviceName = "Living room",
                volume = 30,
                minimumVolume = 2,
                maximumVolume = 20,
                isMuted = true,
            )

        assertTrue(route.isRemote)
        assertTrue(route.canControlVolume)
        assertEquals("Living room", route.displayName)
        assertEquals(20, route.volume)
        assertEquals(2, route.minimumVolume)
        assertEquals(20, route.maximumVolume)
        assertTrue(route.isMuted)
    }

    @Test
    fun `blank remote device name uses the Cast fallback`() {
        val route =
            PlaybackRouteResolver.resolveState(
                isRemote = true,
                deviceName = " ",
                volume = -1,
                minimumVolume = 0,
                maximumVolume = 10,
                isMuted = false,
            )

        assertEquals("Cast device", route.displayName)
        assertEquals(0, route.volume)
    }

    @Test
    fun `output volume requires a controllable remote route and command`() {
        val remoteRoute =
            PlaybackRouteState(
                isRemote = true,
                minimumVolume = 2,
                maximumVolume = 20,
            )
        val localRoute = remoteRoute.copy(isRemote = false)

        assertEquals(
            20,
            PlaybackOutputVolumePolicy.targetVolume(
                requestedVolume = 40,
                route = remoteRoute,
                commandAvailable = true,
            ),
        )
        assertEquals(
            2,
            PlaybackOutputVolumePolicy.targetVolume(
                requestedVolume = -1,
                route = remoteRoute,
                commandAvailable = true,
            ),
        )
        assertEquals(
            null,
            PlaybackOutputVolumePolicy.targetVolume(
                requestedVolume = 10,
                route = localRoute,
                commandAvailable = true,
            ),
        )
        assertEquals(
            null,
            PlaybackOutputVolumePolicy.targetVolume(
                requestedVolume = 10,
                route = remoteRoute,
                commandAvailable = false,
            ),
        )
    }
}
