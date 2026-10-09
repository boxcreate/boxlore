package cx.aswin.boxlore.core.catalog.shownotes

import java.util.Locale

/** A platform label is not the title of the profile, programme, or community it links to. */
object EpisodeLinkTitles {
    private val actionPrefix = Regex("""^(?:visit|follow|view|open|watch|listen|support|join|subscribe)(?:\s+us)?(?:\s+(?:on|in|to))?\s+""")

    fun destinationTitle(title: String?, platform: String?): String? {
        val text = title?.trim()?.takeIf(String::isNotBlank) ?: return null
        val platformName = platform?.lowercase(Locale.ROOT) ?: return text
        val aliases = when (platformName) {
            "x" -> setOf("x", "twitter", "x (twitter)", "twitter (x)")
            "facebook" -> setOf("facebook", "fb")
            "ko-fi" -> setOf("ko-fi", "ko fi", "kofi")
            "buy me a coffee" -> setOf("buy me a coffee", "buymeacoffee")
            else -> setOf(platformName)
        }
        val label = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trimEnd('.', '!', ':')
        return text.takeUnless { label in aliases || label.replaceFirst(actionPrefix, "") in aliases }
    }
}
