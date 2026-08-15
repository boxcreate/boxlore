package cx.aswin.boxlore.core.rss

/** HEAD / conditional-GET decision for publisher-feed freshness. */
object RssUnchangedLogic {
    const val HTTP_NOT_MODIFIED = 304
    const val HTTP_METHOD_NOT_ALLOWED = 405
    const val HTTP_NOT_IMPLEMENTED = 501

    fun headMeansUnchanged(code: Int): Boolean = code == HTTP_NOT_MODIFIED

    /** HEAD 405/501 (or a failed HEAD) must try conditional GET, not "changed". */
    fun headMeansTryConditionalGet(code: Int?): Boolean =
        code == null ||
            code == HTTP_METHOD_NOT_ALLOWED ||
            code == HTTP_NOT_IMPLEMENTED
}
