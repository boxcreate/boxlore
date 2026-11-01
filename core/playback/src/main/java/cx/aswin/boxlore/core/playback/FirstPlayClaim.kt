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
    /** True when this install was updated after it was installed; read only if the first launch didn't note it. */
    private val isUpdatedInstall: () -> Boolean,
    /** The flag older builds set when they sent the event from the screen. */
    private val loggedByOlderBuild: suspend () -> Boolean,
) {
    private val lock = Mutex()

    @Volatile private var held = false

    /**
     * A [Ticket] for the listen that should send the event, or null: it was sent, an older build sent
     * it, or another listen holds the ticket. Saved only once the event is sent ([Ticket.sent]), so a
     * listen whose lookups fail hands it to a later one ([Ticket.releaseIfUnsent]).
     */
    suspend fun claim(): Ticket? = lock.withLock {
        if (held || store.isClaimed()) return@withLock null
        val updated = store.updatedFromOlderBuild() ?: isUpdatedInstall()
        // Only an install that ran an older build can have sent it; a restored copy of that flag can't count.
        if (updated && runCatching { loggedByOlderBuild() }.getOrDefault(false)) {
            store.markClaimed()
            return@withLock null
        }
        held = true
        Ticket()
    }

    inner class Ticket internal constructor() {
        @Volatile private var done = false

        /** The event went out: no later listen sends it. */
        fun sent() {
            store.markClaimed()
            done = true
        }

        /** The listen ended without sending it: a later listen may. */
        fun releaseIfUnsent() {
            if (!done) held = false
        }
    }

    companion object {
        /** See [FirstPlayInstallOrigin]. */
        fun noteInstallOrigin(store: FirstPlayStore, isUpdatedInstall: () -> Boolean) {
            if (store.updatedFromOlderBuild() == null) store.setUpdatedFromOlderBuild(isUpdatedInstall())
        }
    }
}

/** The app-start hook for [FirstPlayClaim]. */
object FirstPlayInstallOrigin {
    /**
     * Notes once, on this build's first launch, whether the install came from an older build, so
     * updating a restored phone before its first listen can't hide that listen. Call off the main thread.
     */
    fun note(context: Context) = FirstPlayClaim.noteInstallOrigin(PrefsFirstPlayStore(context)) { context.isUpdatedInstall() }
}

/** Where [FirstPlayClaim] keeps its flags: SharedPreferences in the app, a fake in tests. */
internal interface FirstPlayStore {
    fun isClaimed(): Boolean

    fun markClaimed()

    /** Null until the first launch of a build with [FirstPlayClaim] notes it. */
    fun updatedFromOlderBuild(): Boolean?

    fun setUpdatedFromOlderBuild(updated: Boolean)
}

/** [FirstPlayStore] in `boxlore_install_milestones.xml`, which the backup rules exclude. */
internal class PrefsFirstPlayStore(context: Context) : FirstPlayStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isClaimed(): Boolean = prefs.getBoolean(KEY_FIRST_PLAY, false)

    override fun markClaimed() {
        prefs.edit().putBoolean(KEY_FIRST_PLAY, true).apply()
    }

    override fun updatedFromOlderBuild(): Boolean? =
        if (prefs.contains(KEY_UPDATED_FROM_OLDER_BUILD)) prefs.getBoolean(KEY_UPDATED_FROM_OLDER_BUILD, false) else null

    override fun setUpdatedFromOlderBuild(updated: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATED_FROM_OLDER_BUILD, updated).apply()
    }

    private companion object {
        const val PREFS_NAME = "boxlore_install_milestones"
        const val KEY_FIRST_PLAY = "first_play_claimed"
        const val KEY_UPDATED_FROM_OLDER_BUILD = "updated_from_older_build"
    }
}

/** True when this install was updated after it was installed, so it may predate [FirstPlayClaim]. */
internal fun Context.isUpdatedInstall(): Boolean = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    info.lastUpdateTime > info.firstInstallTime
}.getOrDefault(false)
