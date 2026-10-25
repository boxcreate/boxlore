package cx.aswin.boxlore.navigation

import android.net.Uri
import androidx.navigation.NavDeepLink
import androidx.navigation.navDeepLink

/**
 * HTTPS links boxlore opens as verified App Links. boxlore.in is current; the aswin.cx
 * addresses stay because links shared by older builds keep circulating.
 *
 * Must match the `autoVerify` intent filters in AndroidManifest.xml (WebAppLinksManifestTest checks it).
 */
internal object WebAppLinks {
    /** Path prefixes claimed per host. */
    val claimedPaths: Map<String, List<String>> = mapOf(
        "boxlore.in" to listOf("/share", "/auth", EMAIL_SIGN_IN_LINK_PATH),
        "aswin.cx" to listOf("/boxlore/share", "/boxcast/share", "/boxlore/auth"),
    )

    /** Where Firebase serves emailed sign-in links on its link domain. */
    const val EMAIL_SIGN_IN_LINK_PATH = "/__/auth/links"

    /** Share page addresses, current first. */
    val shareBases: List<String> = listOf(
        "https://boxlore.in/share",
        "https://aswin.cx/boxlore/share",
        "https://aswin.cx/boxcast/share",
    )

    fun isClaimed(uri: Uri): Boolean =
        claimedPaths[uri.host]?.any { prefix -> uri.path.orEmpty().startsWith(prefix) } == true

    fun podcastShareDeepLinks(): List<NavDeepLink> =
        shareBases.map { base -> navDeepLink { uriPattern = "$base?type=podcast&id={podcastId}" } }

    /**
     * Episode share links on every share address. Links with `&show=` (newer builds' shares) put the
     * show in the route's `podcastId` argument ([EpisodeShowLinks]); the older patterns stay unchanged.
     */
    fun episodeShareDeepLinks(): List<NavDeepLink> =
        shareBases.flatMap { base ->
            (
                listOf(
                    "$base?type=episode&id={episodeId}&t={t}&start={start}&end={end}&autoplay={autoplay}",
                    "$base?type=episode&id={episodeId}&t={t}&autoplay={autoplay}",
                    "$base?type=episode&id={episodeId}&autoplay={autoplay}",
                    "$base?type=episode&id={episodeId}",
                ) + EpisodeShowLinks.sharePatterns(base)
                ).map { pattern -> navDeepLink { uriPattern = pattern } }
        }
}
