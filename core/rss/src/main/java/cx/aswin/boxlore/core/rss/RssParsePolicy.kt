package cx.aswin.boxlore.core.rss

/** Chooses which RSS parsers run for a downloaded feed. */
internal object RssParsePolicy {
    /** Above this size only the streaming pull parser runs; the library copies the feed into a String. */
    const val LIBRARY_PARSER_MAX_BYTES = 4 * 1024 * 1024

    fun useLibraryParser(byteCount: Int): Boolean = byteCount <= LIBRARY_PARSER_MAX_BYTES
}
