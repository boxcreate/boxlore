package cx.aswin.boxlore.core.rss

/**
 * `feed_identity.xml` and the catalog rows the parser and persist produced for it before episode
 * identity followed feed document order (recorded from that code on the JVM, show 4242).
 *
 * [SMALL_ROWS] is what a device stored when both parsers read the feed; [LARGE_ROWS] when only the
 * streaming parser did (feeds over 4 MiB). Each pair is (episode ID, stored catalog key).
 */
internal object IdentityFixtureFeed {
    const val FEED_URL = "https://example.com/identity.xml"
    const val SHOW_ID = "4242"
    val namespace: String = RssIdGenerator.podcastId(FEED_URL)

    const val EP1_PADDED_GUID = " ep-1᠎"

    val SMALL_ROWS: List<Pair<String, String>> =
        listOf(
            "-4473233887734360468" to "ep-1",
            "-1620196808107782941" to "ep-5",
            "-4092870849651754619" to "ep-4",
            "-230289529853465550" to "https://cdn.example.com/ep3.mp3",
            "-1745259125871161974" to "https://cdn.example.com/ep2.mp3",
            "-5426929428811007990" to "https://cdn.example.com/ep2-hq.m4a",
            "-171063312026563321" to "https://cdn.example.com/ep0-a.mp3",
            "-5875828860057459777" to "https://cdn.example.com/ep0-b.mp3",
        )

    val LARGE_ROWS: List<Pair<String, String>> =
        listOf(
            "-4473233887734360468" to "ep-1",
            "-1620196808107782941" to "ep-5",
            "-4092870849651754619" to "ep-4",
            "-230289529853465550" to "https://cdn.example.com/ep3.mp3",
            "-5426929428811007990" to "https://cdn.example.com/ep2-hq.m4a",
            "-5875828860057459777" to "https://cdn.example.com/ep0-b.mp3",
        )

    /** The feed as served: under 4 MiB, so both parsers read it. */
    fun small(): ByteArray = requireNotNull(javaClass.getResourceAsStream("/feed_identity.xml")).readBytes()

    /** The same feed padded past 4 MiB, so only the streaming parser reads it. */
    fun large(): ByteArray {
        val padding = "<!--" + "x".repeat(RssParsePolicy.LIBRARY_PARSER_MAX_BYTES) + "-->"
        return small().decodeToString().replaceFirst("<channel>", "<channel>$padding").encodeToByteArray()
    }

    suspend fun parse(bytes: ByteArray): ParsedRssFeed = RssFeedClient().parse(FEED_URL, bytes, namespace)
}
