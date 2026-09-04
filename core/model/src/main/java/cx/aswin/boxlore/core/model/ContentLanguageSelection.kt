package cx.aswin.boxlore.core.model

/** Pure language-chip toggle rules for discovery preferences UI. */
object ContentLanguageSelection {
    /**
     * Returns the updated language list after toggling [languageCode], or `null` when the toggle
     * is a no-op (English lock, at capacity without deselect, invalid code).
     */
    fun applyToggle(selectedLanguages: List<String>, languageCode: String, country: String,): List<String>? {
        val code = languageCode.trim().lowercase().substringBefore('-')
        if (code == "en" || code !in ContentRegions.LANGUAGE_ALLOWLIST) return null

        val resolvedCountry = ContentRegions.canonicalize(country)
        val normalized = ContentRegions.normalizeLanguages(selectedLanguages, resolvedCountry)
        val isSelected = code in normalized

        val next =
            when {
                isSelected -> normalized.filter { it != code }
                normalized.size >= ContentRegions.MAX_LANGUAGES -> normalized
                else -> normalized + code
            }
        if (next == normalized) return null
        return ContentRegions.normalizeLanguages(next, resolvedCountry)
    }
}
