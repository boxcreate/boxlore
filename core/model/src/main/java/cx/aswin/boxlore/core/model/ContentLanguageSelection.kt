package cx.aswin.boxlore.core.model

/** Pure language-chip toggle rules for discovery preferences UI. */
object ContentLanguageSelection {
    /**
     * Returns the updated language list after toggling [languageCode], or `null` when the toggle
     * is a no-op (English, which has its own switch; at capacity without deselect; invalid code).
     * With English switched off ([englishOff], when the picks allow it) its slot is free for a pick.
     */
    fun applyToggle(selectedLanguages: List<String>, languageCode: String, country: String, englishOff: Boolean = false,): List<String>? {
        val code = languageCode.trim().lowercase().substringBefore('-')
        if (code == "en" || code !in ContentRegions.LANGUAGE_ALLOWLIST) return null

        val resolvedCountry = ContentRegions.canonicalize(country)
        val normalized = ContentRegions.normalizeLanguages(selectedLanguages, resolvedCountry, englishOff)
        val isSelected = code in normalized

        val next =
            when {
                isSelected -> normalized.filter { it != code }
                normalized.size >= ContentRegions.MAX_LANGUAGES -> normalized
                else -> normalized + code
            }
        if (next == normalized) return null
        return ContentRegions.normalizeLanguages(next, resolvedCountry, englishOff)
    }

    /**
     * The "Include English" switch for [selectedLanguages] (as stored: English included unless it
     * was switched off) and the listener's [englishOff] choice: [EnglishSwitch.checked] when English
     * is in effect, [EnglishSwitch.enabled] when the picks allow switching it off, and
     * [EnglishSwitch.keptOnBy] the picks that keep it on (empty when none, or only English, is picked).
     */
    fun englishSwitch(selectedLanguages: List<String>, englishOff: Boolean): EnglishSwitch {
        val picks = selectedLanguages.map { it.trim().lowercase().substringBefore('-') }.filter { it.isNotEmpty() && it != "en" }.distinct()
        val allowed = ContentRegions.canSwitchEnglishOff(picks)
        return EnglishSwitch(
            checked = !(englishOff && allowed),
            enabled = allowed,
            keptOnBy = picks.filter { it !in ContentRegions.ENGLISH_OFF_LANGUAGES },
        )
    }

    data class EnglishSwitch(val checked: Boolean, val enabled: Boolean, val keptOnBy: List<String>,)
}
