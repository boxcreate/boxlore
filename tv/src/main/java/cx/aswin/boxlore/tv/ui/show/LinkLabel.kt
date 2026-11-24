package cx.aswin.boxlore.tv.ui.show

/** A link from the show notes as its button names it: no scheme or "www.", and cut short, so every link fits on screen. */
object LinkLabel {
    const val MAX_LENGTH = 48

    fun of(url: String): String {
        val bare = url.substringAfter("://").removePrefix("www.").trimEnd('/')
        return if (bare.length > MAX_LENGTH) bare.take(MAX_LENGTH - 1).trimEnd() + "…" else bare
    }
}
