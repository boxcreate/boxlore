package cx.aswin.boxlore.core.prefs

/** One moment Home could show the NPS survey: the listening done on this install and what else is going on. */
data class NpsSurveyMoment(
    val nowMs: Long,
    val installedAtMs: Long,
    /** Episodes played to the end in boxlore since it was installed here. */
    val finishedSinceInstall: Int,
    /** When the latest of them finished; null when none has. */
    val lastFinishedAtMs: Long?,
    val processStartedAtMs: Long,
    val record: NpsSurveyRecord,
    /** The quiet days after installing, onboarding, an import or a first sign-in. */
    val inQuietDays: Boolean,
    /** The gap shared with the review prompt has passed. */
    val cooldownElapsed: Boolean,
)

/**
 * When the NPS survey may appear: right after an episode finishes in this run of boxlore (never on an
 * open alone), once 3 episodes have finished here and boxlore has been installed for a week, never in
 * the first run, never in the quiet days, and again only 90 days after it last appeared. Playback and
 * other sheets are checked by the caller.
 */
object NpsSurveySchedule {
    const val MIN_FINISHED_EPISODES = 3
    const val MIN_DAYS_INSTALLED = 7
    const val ASK_AGAIN_DAYS = 90

    /** How long after an episode finishes Home still counts it as the moment to ask. */
    const val FINISH_MOMENT_WINDOW_MS = 2L * 60 * 60 * 1000

    /** An episode finished since this run of boxlore started, and recently. */
    fun isFinishMoment(lastFinishedAtMs: Long?, processStartedAtMs: Long, nowMs: Long,): Boolean =
        lastFinishedAtMs != null && lastFinishedAtMs >= processStartedAtMs && nowMs - lastFinishedAtMs <= FINISH_MOMENT_WINDOW_MS

    fun isDue(moment: NpsSurveyMoment): Boolean {
        val now = moment.nowMs
        val lastShown = moment.record.lastShownAtMs
        return isFinishMoment(moment.lastFinishedAtMs, moment.processStartedAtMs, now) &&
            moment.finishedSinceInstall >= MIN_FINISHED_EPISODES &&
            moment.installedAtMs > 0L &&
            now - moment.installedAtMs >= MIN_DAYS_INSTALLED * DAY_MS &&
            moment.record.launches >= 2 &&
            !moment.inQuietDays &&
            moment.cooldownElapsed &&
            (lastShown == null || now - lastShown >= ASK_AGAIN_DAYS * DAY_MS)
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
