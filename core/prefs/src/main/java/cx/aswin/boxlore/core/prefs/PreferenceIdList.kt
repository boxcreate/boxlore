package cx.aswin.boxlore.core.prefs

/**
 * Encodes ordered podcast ids for DataStore string prefs.
 * Uses the unit separator so ids (including `rss:` URLs) can contain commas.
 */
object PreferenceIdList {
    private const val SEPARATOR = '\u001f'

    fun encode(ids: List<String>): String {
        return ids
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(SEPARATOR.toString())
    }

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
    }
}
