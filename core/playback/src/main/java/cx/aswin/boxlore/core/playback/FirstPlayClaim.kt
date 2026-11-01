package cx.aswin.boxlore.core.playback

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Claims this install's first play once, from the player, so `first_episode_played` fires whatever
 * started playback (the app, Android Auto, a notification) and names the episode. The claim lives in
 * a prefs file kept out of backups, so a restored or new phone counts its own first play.
 */
internal class FirstPlayClaim(
    private val store: FirstPlayStore,
    /** True when an install updated from an older boxlore already sent the event from the screen. */
    private val loggedBeforeUpdate: suspend () -> Boolean,
) {
    private val lock = Mutex()

    /** True exactly once per install: for the first play that should send the event. */
    suspend fun claim(): Boolean = lock.withLock {
        if (store.isClaimed()) return@withLock false
        val alreadySent = runCatching { loggedBeforeUpdate() }.getOrDefault(false)
        store.markClaimed()
        !alreadySent
    }
}

/** Where [FirstPlayClaim] keeps its flag: SharedPreferences in the app, a fake in tests. */
internal interface FirstPlayStore {
    fun isClaimed(): Boolean

    fun markClaimed()
}

/** [FirstPlayStore] in `boxlore_install_milestones.xml`, which the backup rules exclude. */
internal class PrefsFirstPlayStore(context: Context) : FirstPlayStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isClaimed(): Boolean = prefs.getBoolean(KEY_FIRST_PLAY, false)

    override fun markClaimed() {
        prefs.edit().putBoolean(KEY_FIRST_PLAY, true).apply()
    }

    private companion object {
        const val PREFS_NAME = "boxlore_install_milestones"
        const val KEY_FIRST_PLAY = "first_play_claimed"
    }
}

/** True when this install was updated after it was installed, so it may predate [FirstPlayClaim]. */
internal fun Context.isUpdatedInstall(): Boolean = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    info.lastUpdateTime > info.firstInstallTime
}.getOrDefault(false)
