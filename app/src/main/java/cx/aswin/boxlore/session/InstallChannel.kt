package cx.aswin.boxlore.session

/**
 * Which store this install updates from: `play` for the Play build or a Play install, `github` for
 * the direct-update APK. The same rule picks the update flow (`createAppUpdates`).
 */
internal object InstallChannel {
    const val PLAY = "play"
    const val GITHUB = "github"

    fun usesPlay(directUpdates: Boolean, installedFromPlay: () -> Boolean): Boolean = !directUpdates || installedFromPlay()

    fun of(directUpdates: Boolean, installedFromPlay: () -> Boolean): String = if (usesPlay(directUpdates, installedFromPlay)) PLAY else GITHUB
}
