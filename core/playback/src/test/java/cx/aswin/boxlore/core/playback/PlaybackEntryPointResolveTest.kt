package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackEntryPointResolveTest {
    @Test
    fun mapsKnownEntryPoints() {
        assertEquals(PlaybackEntryPoint.HOME_MIXTAPE, PlaybackEntryPointResolve.fromEntryPointString("home_mixtape"))
        assertEquals(PlaybackEntryPoint.LEARN, PlaybackEntryPointResolve.fromEntryPointString("learn"))
        assertEquals(PlaybackEntryPoint.LEARN, PlaybackEntryPointResolve.fromEntryPointString("learn_history"))
        assertEquals(PlaybackEntryPoint.BRIEFING, PlaybackEntryPointResolve.fromEntryPointString("briefing"))
        assertEquals(PlaybackEntryPoint.BRIEFING, PlaybackEntryPointResolve.fromEntryPointString(" Briefing "))
    }

    @Test
    fun unknownOrMissingFallsBackToGeneric() {
        assertEquals(PlaybackEntryPoint.GENERIC, PlaybackEntryPointResolve.fromEntryPointString(null))
        assertEquals(PlaybackEntryPoint.GENERIC, PlaybackEntryPointResolve.fromEntryPointString(""))
        assertEquals(PlaybackEntryPoint.GENERIC, PlaybackEntryPointResolve.fromEntryPointString("episode_info_screen"))
        assertEquals(PlaybackEntryPoint.GENERIC, PlaybackEntryPointResolve.fromEntryPointString("unknown"))
    }
}
