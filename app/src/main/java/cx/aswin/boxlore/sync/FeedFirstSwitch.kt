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
 * - **Fixed for the process.** The first [isOn] decides the value. Answers loaded later (see
 *   [remember]) only update [lastKnown] for the next process, so a page's episode source never
 *   switches while the app runs.
 * - **Exposure only when known.** PostHog's `$feature_flag_called` is recorded once, when the
 *   process value comes from a live PostHog answer, and the process uses the value that exposure
 *   read returned. A value from [lastKnown] or the default records nothing, so devices that never
 *   get an answer don't count toward the rollout.
 *
 * **Rolling back.** Keep `feed_first` active and set its release condition to 0%, so PostHog
 * answers `false` and each phone saves it. Never disable or delete the flag before sunset step S7:
 * PostHog leaves a disabled or deleted flag out of its answer, which counts as no answer here, so
 * a phone that saved `true` would stay on the feed-first source in every later process.
 *
 * Added in P4 (own episode IDs, plan section 11 "P4: App v2 build", Rollback).
 *
 * LEGACY(own-ids): staged feed-first rollout. Remove at sunset step S7 (MIGRATION.md §6) once
 * `feed_first` has been at 100% for 30 days.
 *
 * @param readFlag PostHog's raw flag value, recording an exposure when `sendExposure` is true;
 *   null while PostHog is still starting. Replaced in tests.
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
     * Saves a freshly loaded [answer] (PostHog's raw `feed_first` value) for the next process,
     * without changing this process's value or recording an exposure. A non-boolean answer keeps
     * the saved one. Called by [RolloutFlagMemory] when PostHog finishes loading flags.
     */
    fun remember(answer: Any?) {
        knownValue(answer)?.let(lastKnown::write)
    }

    private fun decide(): Boolean {
        val first = readLive(sendExposure = false) ?: return lastKnown.read() ?: false
        // The exposure read is the value this process uses, so the recorded response matches it
        // even when a flag load lands between the two reads. If that read has no boolean answer
        // (the flag vanished in between, or recording threw), the first answer is used.
        val live = readLive(sendExposure = true) ?: first
        lastKnown.write(live)
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
