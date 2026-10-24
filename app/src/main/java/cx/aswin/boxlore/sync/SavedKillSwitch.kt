package cx.aswin.boxlore.sync

import android.content.Context
import com.posthog.PostHog
import cx.aswin.boxlore.core.analytics.PostHogStartup
import java.io.File

/**
 * A fail-open PostHog kill switch for a v2 build job that can start before PostHog is set up (the
 * upgrade repair runs on headless and worker launches too). Unlike the plain [FeedFixSwitches]
 * reads, a launch that asks before PostHog is ready doesn't simply run the job: it uses the last
 * answer this phone saw, so a switch turned on keeps the job paused from the very start of later
 * processes.
 * - **PostHog ready:** PostHog's answer decides, and is saved. A missing or inactive flag counts as
 *   off, so the job runs and that answer replaces a saved "paused".
 * - **PostHog still starting, or the read fails:** the saved answer, else the job runs.
 * - **Flags loaded** ([remember], from [RolloutFlagMemory]): the new answer is saved for later reads.
 *
 * The saved answer lives in `noBackupFilesDir` (see [FileLastKnownFlag]), so a restore or device
 * transfer never brings another phone's answer.
 *
 * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
 *
 * @param flag the PostHog flag that pauses the job when on.
 * @param readFlag whether [flag] is on; null while PostHog is still starting. Replaced in tests.
 */
internal class SavedKillSwitch(
    val flag: String,
    private val readFlag: () -> Boolean?,
    private val lastKnown: LastKnownFlag,
) {
    /** True unless [flag] is on, live or as last saved. */
    fun isOn(): Boolean {
        val live = runCatching { readFlag() }.getOrNull()
        live?.let(lastKnown::write)
        val off = live ?: lastKnown.read() ?: false
        return !off
    }

    /** Saves PostHog's freshly loaded raw value of [flag]; see [flagIsOn]. */
    fun remember(answer: Any?) {
        lastKnown.write(flagIsOn(answer))
    }

    companion object {
        /**
         * PostHog's raw value as a kill-switch answer, like `PostHog.isFeatureEnabled`: missing is
         * off, a boolean is itself, and a variant counts as on.
         */
        internal fun flagIsOn(raw: Any?): Boolean =
            when (raw) {
                null -> false
                is Boolean -> raw
                else -> true
            }

        /** Saved answer file under `noBackupFilesDir` for [flag]. */
        internal fun lastKnownFile(flag: String): String = "kill_switch_" + flag.replace('-', '_')

        /** Production switch: reads PostHog once it is set up (no exposure), saves answers in `noBackupFilesDir`. */
        fun create(context: Context, flag: String): SavedKillSwitch =
            SavedKillSwitch(
                flag = flag,
                readFlag = { if (PostHogStartup.isReady) flagIsOn(PostHog.getFeatureFlag(flag, null, false)) else null },
                lastKnown = FileLastKnownFlag(File(context.applicationContext.noBackupFilesDir, lastKnownFile(flag))),
            )
    }
}
