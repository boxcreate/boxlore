package cx.aswin.boxlore.feature.info

import java.util.Locale

private val episodeLinkBrands = mapOf(
    "instagram.com" to R.drawable.ic_link_instagram,
    "x.com" to R.drawable.ic_link_x,
    "twitter.com" to R.drawable.ic_link_x,
    "threads.net" to R.drawable.ic_link_threads,
    "threads.com" to R.drawable.ic_link_threads,
    "facebook.com" to R.drawable.ic_link_facebook,
    "fb.com" to R.drawable.ic_link_facebook,
    "linkedin.com" to R.drawable.ic_link_linkedin,
    "bsky.app" to R.drawable.ic_link_bluesky,
    "youtube.com" to R.drawable.ic_link_youtube,
    "youtu.be" to R.drawable.ic_link_youtube,
    "tiktok.com" to R.drawable.ic_link_tiktok,
    "twitch.tv" to R.drawable.ic_link_twitch,
    "reddit.com" to R.drawable.ic_link_reddit,
    "discord.com" to R.drawable.ic_link_discord,
    "discord.gg" to R.drawable.ic_link_discord,
    "open.spotify.com" to R.drawable.ic_link_spotify,
    "podcasts.apple.com" to R.drawable.ic_link_applepodcasts,
    "patreon.com" to R.drawable.ic_link_patreon,
    "ko-fi.com" to R.drawable.ic_link_kofi,
    "buymeacoffee.com" to R.drawable.ic_link_buymeacoffee,
)

internal fun episodeLinkBrandIcon(host: String): Int? {
    val normalized = host.lowercase(Locale.ROOT).trimEnd('.')
    return episodeLinkBrands.entries.firstOrNull { (domain, _) ->
        normalized == domain || normalized.endsWith(".$domain")
    }?.value
}
