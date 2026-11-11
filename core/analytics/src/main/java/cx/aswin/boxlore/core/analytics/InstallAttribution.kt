package cx.aswin.boxlore.core.analytics

import android.content.Context
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator

/**
 * Saves `install_channel` once `:app` knows whether the Play Store installed this copy, without
 * relying on Play's referrer service answering at the right moment:
 * - Play's referrer also answers for installs from elsewhere, so it is saved only for a Play install.
 * - When the referrer answers first and Android can't yet say who installed the copy, the answer is
 *   kept ([keepPending]) and saved at a later launch ([onInstallerKnown]).
 * - A copy the Play Store didn't install is saved as [CHANNEL_NOT_PLAY], once.
 *
 * Every value is set once on the person, so the first channel saved stays, and sending it again after
 * an interrupted launch is harmless.
 */
object InstallAttribution {
    /** `install_channel` for any install the Play Store didn't make. */
    const val CHANNEL_NOT_PLAY = "not_play"

    private const val KEY_NOT_PLAY_SAVED = "not_play_attributed"
    private const val KEY_PENDING_CHANNEL = "pending_install_channel"
    private const val KEY_PENDING_REFERRER = "pending_referrer_raw"

    /** What attribution keeps between launches: the analytics preferences in the app, a fake in tests. */
    internal interface Store {
        fun notPlaySaved(): Boolean

        fun markNotPlaySaved()

        fun pending(): Pair<String, String?>?

        fun keep(channel: String, referrerRaw: String?)

        fun clearPending()
    }

    /** Android said whether the Play Store installed this copy: saves what that settles. */
    fun onInstallerKnown(context: Context, fromPlayStore: Boolean) = onInstallerKnown(prefsStore(context), fromPlayStore)

    /** Play's referrer answered but Android couldn't say who installed this copy: keeps the answer for [onInstallerKnown]. */
    fun keepPending(context: Context, channel: String, referrerRaw: String?) = prefsStore(context).keep(channel, referrerRaw)

    /**
     * [afterHandOff] runs a step once the capture queued before it has reached PostHog, in queue order.
     * The store changes only then, so a launch that ends before setup finishes tries again next time.
     */
    internal fun onInstallerKnown(
        store: Store,
        fromPlayStore: Boolean,
        afterHandOff: (() -> Unit) -> Unit = PostHogStartup::runWhenReady,
    ) {
        store.pending()?.let { (channel, referrerRaw) ->
            if (fromPlayStore) {
                GrowthSessionAnalyticsTracks.trackInstallChannelAttributed(channel, referrerRaw)
                afterHandOff(store::clearPending)
            } else {
                store.clearPending()
            }
        }
        if (!fromPlayStore && !store.notPlaySaved()) {
            GrowthSessionAnalyticsTracks.trackInstallChannelAttributed(CHANNEL_NOT_PLAY)
            afterHandOff(store::markNotPlaySaved)
        }
    }

    private fun prefsStore(context: Context): Store {
        val prefs = PrefsFileMigrator.open(context, newName = PrefsFileMigrator.Files.ANALYTICS, oldName = PrefsFileMigrator.LegacyFiles.ANALYTICS)
        return object : Store {
            override fun notPlaySaved() = prefs.getBoolean(KEY_NOT_PLAY_SAVED, false)

            override fun markNotPlaySaved() = prefs.edit().putBoolean(KEY_NOT_PLAY_SAVED, true).apply()

            override fun pending(): Pair<String, String?>? =
                prefs.getString(KEY_PENDING_CHANNEL, null)?.let { it to prefs.getString(KEY_PENDING_REFERRER, null) }

            override fun keep(channel: String, referrerRaw: String?) =
                prefs.edit().putString(KEY_PENDING_CHANNEL, channel).putString(KEY_PENDING_REFERRER, referrerRaw).apply()

            override fun clearPending() = prefs.edit().remove(KEY_PENDING_CHANNEL).remove(KEY_PENDING_REFERRER).apply()
        }
    }
}
