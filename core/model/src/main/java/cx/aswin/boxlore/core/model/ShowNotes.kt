package cx.aswin.boxlore.core.model

/** Destination purpose, independent of UI icons, colours and translated action labels. */
enum class EpisodeLinkKind { WEBSITE, ARTICLE, VIDEO, SOCIAL, SUPPORT, PODCAST, COMMUNITY, EMAIL }

data class EpisodeLink(
    val url: String,
    val host: String,
    val kind: EpisodeLinkKind,
    val title: String? = null,
    val platform: String? = null,
    val handle: String? = null,
    val context: String = "",
)

data class ShowNotes(
    val plainText: String,
    val links: List<EpisodeLink>,
    val chapters: List<Chapter>,
    val html: String,
)
