package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackSkipPolicyTest {
    @Test
    fun explicitPositionWinsOverResumeAndBeginningSkip() {
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = 1_000L,
                savedProgressMs = 45_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
            )

        assertEquals(1_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.EXPLICIT, result.reason)
    }

    @Test
    fun meaningfulResumeWinsEvenInsideBeginningWindow() {
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 3_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
            )

        assertEquals(3_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.RESUME, result.reason)
    }

    @Test
    fun tinyProgressIsTreatedAsFreshStart() {
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 2_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
            )

        assertEquals(30_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.SKIP_BEGINNING, result.reason)
    }

    @Test
    fun resumeIntentMapsHeroResumeAndHistoryAsExplicit() {
        assertEquals(
            PlaybackSkipPolicy.ResumeIntent.EXPLICIT,
            PlaybackSkipPolicy.resumeIntentFromEntryPoint("home_hero_resume_grid"),
        )
        assertEquals(
            PlaybackSkipPolicy.ResumeIntent.EXPLICIT,
            PlaybackSkipPolicy.resumeIntentFromEntryPoint("home_hero_resume"),
        )
        assertEquals(
            PlaybackSkipPolicy.ResumeIntent.EXPLICIT,
            PlaybackSkipPolicy.resumeIntentFromEntryPoint("library_history"),
        )
        assertEquals(
            PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
            PlaybackSkipPolicy.resumeIntentFromEntryPoint("home_mixtape"),
        )
        assertEquals(
            PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
            PlaybackSkipPolicy.resumeIntentFromEntryPoint("smart_queue"),
        )
        assertEquals(
            PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
            PlaybackSkipPolicy.resumeIntentFromEntryPoint(null),
        )
    }

    @Test
    fun explicitIntentAlwaysResumesEvenWhenStaleAndFlagOn() {
        val now = 1_000_000_000_000L
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 45_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
                resumeIntent = PlaybackSkipPolicy.ResumeIntent.EXPLICIT,
                lastPlayedAtMs = now - PlaybackSkipPolicy.STALE_RESUME_MS - 1L,
                staleRestartEnabled = true,
                nowMs = now,
            )

        assertEquals(45_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.RESUME, result.reason)
    }

    @Test
    fun implicitWarmResumesWhenFlagOn() {
        val now = 1_000_000_000_000L
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 45_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
                resumeIntent = PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
                lastPlayedAtMs = now - 2L * 24L * 60L * 60L * 1_000L,
                staleRestartEnabled = true,
                nowMs = now,
            )

        assertEquals(45_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.RESUME, result.reason)
    }

    @Test
    fun implicitStaleRestartsWhenFlagOn() {
        val now = 1_000_000_000_000L
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 45_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
                resumeIntent = PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
                lastPlayedAtMs = now - PlaybackSkipPolicy.STALE_RESUME_MS - 1L,
                staleRestartEnabled = true,
                nowMs = now,
            )

        assertEquals(30_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.SKIP_BEGINNING, result.reason)
    }

    @Test
    fun implicitStaleStillResumesWhenFlagOff() {
        val now = 1_000_000_000_000L
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 45_000L,
                isCompleted = false,
                skipBeginningMs = 30_000L,
                resumeIntent = PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
                lastPlayedAtMs = now - PlaybackSkipPolicy.STALE_RESUME_MS - 1L,
                staleRestartEnabled = false,
                nowMs = now,
            )

        assertEquals(45_000L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.RESUME, result.reason)
    }

    @Test
    fun missingLastPlayedAtIsStaleForImplicitWhenFlagOn() {
        val result =
            PlaybackSkipPolicy.resolveInitialPosition(
                explicitPositionMs = null,
                savedProgressMs = 45_000L,
                isCompleted = false,
                skipBeginningMs = 0L,
                resumeIntent = PlaybackSkipPolicy.ResumeIntent.IMPLICIT,
                lastPlayedAtMs = null,
                staleRestartEnabled = true,
                nowMs = 1_000_000_000_000L,
            )

        assertEquals(0L, result.positionMs)
        assertEquals(PlaybackSkipPolicy.InitialPositionReason.START, result.reason)
    }

    @Test
    fun nullablePodcastOverridesFallBackIndependently() {
        val result =
            PlaybackSkipPolicy.resolveEffectiveTrim(
                globalSkipBeginningMs = 15_000L,
                globalSkipEndingMs = 30_000L,
                podcastSkipBeginningOverrideMs = 0L,
                podcastSkipEndingOverrideMs = null,
            )

        assertEquals(0L, result.skipBeginningMs)
        assertEquals(30_000L, result.skipEndingMs)
    }

    @Test
    fun unsafeContentWindowDisablesTrimming() {
        assertFalse(
            PlaybackSkipPolicy.hasSafePlayableWindow(
                durationMs = 60_000L,
                skipBeginningMs = 30_000L,
                skipEndingMs = 15_000L,
            ),
        )
        assertTrue(
            PlaybackSkipPolicy.hasSafePlayableWindow(
                durationMs = 120_000L,
                skipBeginningMs = 30_000L,
                skipEndingMs = 30_000L,
            ),
        )
    }

    @Test
    fun outroOnlyTriggersOnArmedNaturalCrossing() {
        assertTrue(
            PlaybackSkipPolicy.isNaturalOutroCrossing(
                previousPositionMs = 89_000L,
                currentPositionMs = 90_500L,
                durationMs = 120_000L,
                skipBeginningMs = 0L,
                skipEndingMs = 30_000L,
                armed = true,
                isPlaying = true,
            ),
        )
        assertFalse(
            PlaybackSkipPolicy.isNaturalOutroCrossing(
                previousPositionMs = 95_000L,
                currentPositionMs = 96_000L,
                durationMs = 120_000L,
                skipBeginningMs = 0L,
                skipEndingMs = 30_000L,
                armed = false,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun activeEndingTrimSuppressesEarlyProgressCompletion() {
        assertTrue(
            PlaybackSkipPolicy.shouldCompleteFromProgress(
                positionMs = 1_000_000L,
                durationMs = 1_200_000L,
                effectiveSkipEndingMs = 0L,
            ),
        )
        assertFalse(
            PlaybackSkipPolicy.shouldCompleteFromProgress(
                positionMs = 1_000_000L,
                durationMs = 1_200_000L,
                effectiveSkipEndingMs = 30_000L,
            ),
        )
    }

    @Test
    fun effectiveEndingTrimRequiresSafePlayableWindow() {
        assertEquals(
            30_000L,
            PlaybackSkipPolicy.effectiveEndingTrimForCompletion(
                durationMs = 120_000L,
                skipBeginningMs = 30_000L,
                skipEndingMs = 30_000L,
            ),
        )
        assertEquals(
            0L,
            PlaybackSkipPolicy.effectiveEndingTrimForCompletion(
                durationMs = 60_000L,
                skipBeginningMs = 30_000L,
                skipEndingMs = 15_000L,
            ),
        )
    }
}
