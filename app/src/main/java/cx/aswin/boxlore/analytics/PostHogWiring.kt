package cx.aswin.boxlore.analytics

import com.posthog.PostHogOnFeatureFlags
import cx.aswin.boxlore.util.InstallOrigin

/** PostHog wiring for `BoxLoreApplication.startPostHog`, kept here so JVM tests can check it. */
internal object PostHogWiring {
    /**
     * Tags every event carries. Debug and isolated-test builds are internal, so PostHog's test-account
     * filter leaves them out; release builds say so explicitly.
     */
    fun environment(debug: Boolean, isolatedTests: Boolean,): Map<String, Any> =
        if (debug || isolatedTests) {
            mapOf("is_internal" to true, "app_environment" to if (isolatedTests) "update_test" else "debug")
        } else {
            mapOf("is_internal" to false, "app_environment" to "production")
        }

    /**
     * Where this copy came from, also on every event:
     * - `build_channel`: `github` for the direct-update APK (GitHub, Obtainium, debug builds), `play` for the Play bundle.
     * - `install_source`: which app installed it, from [installSource].
     * - `installer_package`: the installer's package as Android reports it, or `none`.
     *
     * Play's install referrer can't tell these apart: it answers for GitHub installs too.
     */
    fun installTags(directUpdatesBuild: Boolean, origin: InstallOrigin): Map<String, Any> =
        mapOf(
            "build_channel" to if (directUpdatesBuild) "github" else "play",
            "install_source" to installSource(origin),
            "installer_package" to (origin.installer ?: "none"),
        )

    /**
     * Names the app that installed this copy. A downloaded file opened by hand and boxlore's own
     * updater both go through Android's package installer, so they share `package_installer`.
     */
    fun installSource(origin: InstallOrigin): String {
        val installer = origin.installer ?: return if (origin.initiator == SHELL_PACKAGE) "adb" else "unknown"
        return INSTALL_SOURCES.entries.firstOrNull { (_, packages) -> installer in packages }?.key ?: "other_store"
    }

    /**
     * The install channel to save with the referrer: Play's referrer only describes a Play install,
     * so any other install is saved as `not_play`, without the referrer.
     */
    fun installAttribution(playInstall: Boolean, channel: String, referrerRaw: String?): Pair<String, String?> =
        if (playInstall) channel to referrerRaw else "not_play" to null

    private const val SHELL_PACKAGE = "com.android.shell"

    private val INSTALL_SOURCES: Map<String, Set<String>> =
        mapOf(
            "play_store" to setOf("com.android.vending"),
            "package_installer" to
                setOf(
                    "com.google.android.packageinstaller",
                    "com.android.packageinstaller",
                    "com.miui.packageinstaller",
                    "com.samsung.android.packageinstaller",
                ),
            "boxlore_updater" to setOf("cx.aswin.boxlore"),
            "obtainium" to setOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid"),
            "aurora_store" to setOf("com.aurora.store", "com.aurora.store.nightly"),
            "galaxy_store" to setOf("com.sec.android.app.samsungapps"),
            "fdroid" to setOf("org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.machiav3lli.fdroid"),
        )

    /**
     * One flags-loaded callback that runs [steps] in order. A step that throws doesn't stop the rest;
     * the first failure is rethrown once all ran, with any later ones attached as suppressed.
     */
    fun onFlagsLoaded(vararg steps: () -> Unit): PostHogOnFeatureFlags =
        PostHogOnFeatureFlags {
            steps
                .mapNotNull { step -> runCatching(step).exceptionOrNull() }
                .reduceOrNull { first, next -> first.apply { addSuppressed(next) } }
                ?.let { failure -> throw failure }
        }
}
