package cx.aswin.boxlore.core.catalog.shownotes

import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import java.util.Locale
import okhttp3.HttpUrl

internal object EpisodeLinkClassifier {
    private const val TIKTOK_HOST = "tiktok.com"
    private val profileRoutes = setOf("watch", "shorts", "playlist", "embed", "p", "reel", "reels", "explore", "search", "intent", "share", "sharer", "sharer.php", "login", "home", "settings", "privacy", "about", "accounts", "stories", "groups", "events", "pages", "profile.php", "hashtag", "i", "feed", "posts", "videos", "video", "clip")
    private val socialDomains = mapOf(
        "instagram.com" to "Instagram", "x.com" to "X", "twitter.com" to "X",
        "threads.net" to "Threads", "threads.com" to "Threads", "facebook.com" to "Facebook",
        "fb.com" to "Facebook", "linkedin.com" to "LinkedIn", "bsky.app" to "Bluesky",
    )
    private val supportDomains = mapOf("patreon.com" to "Patreon", "ko-fi.com" to "Ko-fi", "buymeacoffee.com" to "Buy Me a Coffee")
    private data class Source(val kind: EpisodeLinkKind, val platform: String? = null, val handle: String? = null)

    fun matchesHost(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")
    private fun matchesAny(host: String, vararg domains: String): Boolean = domains.any { matchesHost(host, it) }

    fun classify(url: HttpUrl, title: String?, context: String): EpisodeLink {
        val host = url.host.removePrefix("www.")
        val segments = url.pathSegments.filter(String::isNotBlank)
        val source = sourceFor(host, segments)
        return EpisodeLink(url.toString(), host, source.kind, EpisodeLinkTitles.destinationTitle(title, source.platform), source.platform, source.handle, context)
    }

    private fun sourceFor(host: String, segments: List<String>): Source {
        val social = socialDomains.entries.firstOrNull { matchesHost(host, it.key) }?.value
        val support = supportDomains.entries.firstOrNull { matchesHost(host, it.key) }?.value
        return when {
            matchesAny(host, "youtube.com", "youtu.be") -> youtube(segments)
            matchesHost(host, "podcasts.apple.com") -> Source(EpisodeLinkKind.PODCAST, "Apple Podcasts")
            matchesHost(host, "open.spotify.com") -> spotify(segments)
            support != null -> Source(EpisodeLinkKind.SUPPORT, support, supportHandle(segments))
            matchesHost(host, "reddit.com") -> reddit(segments)
            matchesAny(host, "discord.com", "discord.gg") -> Source(EpisodeLinkKind.COMMUNITY, "Discord")
            matchesAny(host, TIKTOK_HOST, "twitch.tv") -> streaming(host, segments)
            social != null -> Source(EpisodeLinkKind.SOCIAL, social, socialHandle(segments, social))
            else -> website(segments)
        }
    }

    private fun youtube(segments: List<String>): Source {
        val first = segments.firstOrNull().orEmpty()
        val handle = when {
            first.startsWith("@") -> first
            first in setOf("c", "user") -> segments.getOrNull(1)?.let { "@$it" }
            else -> null
        }
        val kind = if (handle != null || first == "channel") EpisodeLinkKind.SOCIAL else EpisodeLinkKind.VIDEO
        return Source(kind, "YouTube", handle)
    }

    private fun spotify(segments: List<String>): Source = Source(
        if (segments.firstOrNull() in setOf("show", "episode")) EpisodeLinkKind.PODCAST else EpisodeLinkKind.WEBSITE,
        "Spotify",
    )

    private fun supportHandle(segments: List<String>): String? = segments.firstOrNull()?.takeUnless { it in setOf("home", "join", "posts") }

    private fun reddit(segments: List<String>): Source {
        val first = segments.firstOrNull()
        val handle = if (first in setOf("r", "u", "user")) segments.getOrNull(1)?.let { "$first/$it" } else null
        return Source(EpisodeLinkKind.COMMUNITY, "Reddit", handle)
    }

    private fun streaming(host: String, segments: List<String>): Source = Source(
        if (segments.any { it in setOf("video", "videos", "clip") }) EpisodeLinkKind.VIDEO else EpisodeLinkKind.SOCIAL,
        if (matchesHost(host, TIKTOK_HOST)) "TikTok" else "Twitch",
        socialHandle(segments, if (matchesHost(host, TIKTOK_HOST)) "TikTok" else "Twitch"),
    )

    private fun socialHandle(segments: List<String>, platform: String): String? {
        val first = segments.firstOrNull().orEmpty()
        val route = first.lowercase(Locale.ROOT)
        return when {
            platform == "Bluesky" -> segments.getOrNull(1)?.takeIf { route == "profile" }
            platform == "LinkedIn" -> segments.getOrNull(1)?.takeIf { route in setOf("in", "company", "school") }
            platform in setOf("Threads", "TikTok") -> first.takeIf { it.startsWith("@") }
            first.isNotEmpty() && route !in profileRoutes -> first
            else -> null
        }?.takeIf { it.matches(Regex("@?[\\p{L}\\p{N}_.-]+")) }?.let { if (it.startsWith("@")) it else "@$it" }
    }

    private fun website(segments: List<String>): Source {
        val routes = segments.map { it.lowercase(Locale.ROOT) }
        return Source(
            when {
            routes.any { it in setOf("donate", "support", "funding", "membership") } -> EpisodeLinkKind.SUPPORT
            routes.any { it in setOf("article", "articles", "story", "stories", "blog", "news", "posts") } -> EpisodeLinkKind.ARTICLE
            else -> EpisodeLinkKind.WEBSITE
        }
        )
    }
}
