package cx.aswin.boxlore.feature.info.logic

/** The stand-in show name an episode page gets before its show is known. */
object EpisodeShowTitle {
    /**
     * The value callers pass and compare against while the show's name is unknown. It is a marker, not
     * display text: the page shows the translated stand-in instead ([displayName]).
     */
    const val PLACEHOLDER = "Podcast" // i18n-ignore

    /** [title], or [standIn] (the translated "Podcast") while the name is unknown. */
    fun displayName(title: String, standIn: String): String = if (title.isBlank() || title == PLACEHOLDER) standIn else title
}
