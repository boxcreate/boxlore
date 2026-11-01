package cx.aswin.boxlore.core.prefs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the NPS survey may appear: only at a finish moment, after real listening here, and every 90 days. */
class NpsSurveyScheduleTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val processStart = now - 30 * 60 * 1000

    private fun moment(
        finished: Int = 3,
        lastFinishedAt: Long? = now - 60_000,
        installedAt: Long = now - 8 * day,
        launches: Int = 2,
        lastShownAt: Long? = null,
        quiet: Boolean = false,
        cooldown: Boolean = true,
    ) = NpsSurveyMoment(
        nowMs = now,
        installedAtMs = installedAt,
        finishedSinceInstall = finished,
        lastFinishedAtMs = lastFinishedAt,
        processStartedAtMs = processStart,
        record = NpsSurveyRecord(lastShownAtMs = lastShownAt, launches = launches),
        inQuietDays = quiet,
        cooldownElapsed = cooldown,
    )

    @Test
    fun dueRightAfterTheThirdFinishAWeekIn() {
        assertTrue(NpsSurveySchedule.isDue(moment()))
    }

    @Test
    fun neverOnAnOpenWithoutAFinishInThisRun() {
        // Finished before boxlore started this time: opening the app is not the moment.
        assertFalse(NpsSurveySchedule.isDue(moment(lastFinishedAt = processStart - 1)))
        assertFalse(NpsSurveySchedule.isDue(moment(lastFinishedAt = null)))
    }

    @Test
    fun aFinishLongAgoInThisRunIsNotTheMoment() {
        val longRun = moment(lastFinishedAt = now - NpsSurveySchedule.FINISH_MOMENT_WINDOW_MS - 1)
        assertFalse(NpsSurveySchedule.isDue(longRun.copy(processStartedAtMs = now - 10 * day)))
    }

    @Test
    fun needsThreeFinishedEpisodesHere() {
        assertFalse(NpsSurveySchedule.isDue(moment(finished = 2)))
    }

    @Test
    fun needsAWeekSinceInstallAndAKnownInstallTime() {
        assertFalse(NpsSurveySchedule.isDue(moment(installedAt = now - 6 * day)))
        assertFalse(NpsSurveySchedule.isDue(moment(installedAt = 0L)))
    }

    @Test
    fun neverInTheFirstRun() {
        assertFalse(NpsSurveySchedule.isDue(moment(launches = 1)))
        assertFalse(NpsSurveySchedule.isDue(moment(launches = 0)))
    }

    @Test
    fun waitsOutTheQuietDaysAndTheSharedCooldown() {
        assertFalse(NpsSurveySchedule.isDue(moment(quiet = true)))
        assertFalse(NpsSurveySchedule.isDue(moment(cooldown = false)))
    }

    @Test
    fun asksAgainOnlyAfterNinetyDays() {
        assertFalse(NpsSurveySchedule.isDue(moment(lastShownAt = now - 89 * day)))
        assertTrue(NpsSurveySchedule.isDue(moment(lastShownAt = now - 90 * day)))
    }
}
