package cx.aswin.boxlore.sync

import android.content.Context
import com.posthog.PostHog
import cx.aswin.boxlore.core.analytics.PostHogStartup
import java.io.File

/**
 * The `feed_first` rollout flag: whether show pages read the show's own feed (on) or the server's
 * episode list (off). Unlike [FeedFixSwitches], this is a rollout, so it fails closed:
 * - **Default off.** With no PostHog answer (not ready, flag missing, a non-boolean value, an
 *   error) the last known answer is used, and without one the switch is off.
 * - **Last known answer.** Every boolean answer PostHog gives is saved in [lastKnown], which lives
 *   in `noBackupFilesDir` so a restore or device transfer never carries another phone's answer.
 * - **Fixed for the process.** The first [isOn] decides the value; a later flag reload (PostHog
 *   reloads flags mid-session) only updates [lastKnown] for the next process, so a page's episode
 *   source never switches while the app runs.
 * - **Exposure only when known.** PostHog's `$feature_flag_called` is recorded once, when the
 *   process value comes from a live PostHog answer. A value from [lastKnown] or the default
 *   records nothing, so devices that never get an answer don't count toward the rollout.
 *
 * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
 *
 * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
 * `feed_first` has been at 100% for 30 days.
 *
 * @param readFlag PostHog's raw flag value, recording an exposure when `sendExposure` is true;
 *   replaced in tests.
 */
internal class FeedFirstSwitch(
    private val readFlag: (sendExposure: Boolean) -> Any?,
    private val lastKnown: LastKnownFlag,
) {
    private val lock = Any()

    @Volatile
    private var processValue: Boolean? = null

    /** This process's value; decided on the first call and never changed afterwards. */
    fun isOn(): Boolean {
        processValue?.let { return it }
        return synchronized(lock) {
            processValue ?: decide().also { processValue = it }
        }
    }

    /**
     * Saves PostHog's current answer for the next process, without changing this process's value
     * or recording an exposure. Called when PostHog finishes loading flags.
     */
    fun rememberLatest() {
        readLive(sendExposure = false)?.let(lastKnown::write)
    }

    private fun decide(): Boolean {
        val live = readLive(sendExposure = false) ?: return lastKnown.read() ?: false
        lastKnown.write(live)
        // The exposure belongs to the value this process uses, so it is sent only for a live answer.
        readLive(sendExposure = true)
        return live
    }

    private fun readLive(sendExposure: Boolean): Boolean? =
        runCatching { knownValue(readFlag(sendExposure)) }.getOrNull()

    companion object {
        /** PostHog flag key. Rolled out 10% → 50% → 100%. */
        const val FLAG = "feed_first"

        /** Saved last known answer under `noBackupFilesDir`; see [FileLastKnownFlag]. */
        internal const val LAST_KNOWN_FILE = "feed_first_flag"

        /** Only a boolean answer is known; a missing flag, a variant string or anything else is not. */
        internal fun knownValue(raw: Any?): Boolean? = raw as? Boolean

        /** Production switch: reads PostHog once it is set up, saves answers in `noBackupFilesDir`. */
        fun create(context: Context): FeedFirstSwitch =
            FeedFirstSwitch(
                readFlag = { sendExposure ->
                    if (PostHogStartup.isReady) PostHog.getFeatureFlag(FLAG, null, sendExposure) else null
                },
                lastKnown = FileLastKnownFlag(File(context.applicationContext.noBackupFilesDir, LAST_KNOWN_FILE)),
            )
    }
}

/** A saved boolean flag answer; null when none was saved or it can't be read. */
internal interface LastKnownFlag {
    fun read(): Boolean?

    fun write(value: Boolean)
}

/**
 * [LastKnownFlag] in one small file holding `1` or `0`. Writes go to a temporary file that
 * replaces the old one, so a crash mid-write leaves the previous answer. Failures are silent:
 * an unreadable file counts as no answer, and a failed write keeps the old one.
 */
internal class FileLastKnownFlag(private val file: File) : LastKnownFlag {
    @Synchronized
    override fun read(): Boolean? =
        runCatching {
            when (file.takeIf { it.isFile }?.readText()?.trim()) {
                "1" -> true
                "0" -> false
                else -> null
            }
        }.getOrNull()

    @Synchronized
    override fun write(value: Boolean) {
        runCatching {
            val text = if (value) "1" else "0"
            if (file.isFile && file.readText().trim() == text) return
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            try {
                temp.writeText(text)
                if (!temp.renameTo(file)) file.writeText(text)
            } finally {
                temp.delete()
            }
        }
    }
}
