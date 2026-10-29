package cx.aswin.boxlore.core.model

/**
 * Chart storefront countries + content-language helpers for discovery APIs.
 * Keep aligned with proxy `contentLanguages.ts`.
 */
data class ContentRegion(val code: String, val label: String, val aliases: Set<String>, val recommendedLanguages: List<String>,)

object ContentRegions {
    const val MAX_LANGUAGES = 4

    val LANGUAGE_ALLOWLIST: Set<String> =
        setOf("en", "hi", "fr", "de", "nl", "es", "pt", "ru", "id", "zh")

    /** Stable chip order for settings UI. */
    val LANGUAGE_OPTIONS: List<String> =
        listOf("en", "hi", "fr", "de", "nl", "es", "pt", "ru", "id", "zh")

    val LANGUAGE_LABELS: Map<String, String> =
        mapOf(
            "en" to "English",
            "hi" to "Hindi",
            "fr" to "French",
            "de" to "German",
            "nl" to "Dutch",
            "es" to "Spanish",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "id" to "Indonesian",
            "zh" to "Chinese",
        )

    val all: List<ContentRegion> =
        listOf(
            ContentRegion("us", "USA", setOf("us", "usa"), listOf("en")),
            ContentRegion("in", "India", setOf("in", "ind"), listOf("en", "hi")),
            ContentRegion("gb", "UK", setOf("gb", "uk"), listOf("en")),
            ContentRegion("fr", "France", setOf("fr"), listOf("en", "fr")),
            ContentRegion("de", "Germany", setOf("de"), listOf("en", "de")),
            ContentRegion("nl", "Netherlands", setOf("nl"), listOf("en", "nl")),
            ContentRegion("sg", "Singapore", setOf("sg"), listOf("en", "zh")),
            ContentRegion("es", "Spain", setOf("es"), listOf("en", "es")),
            ContentRegion("br", "Brazil", setOf("br"), listOf("en", "pt")),
            ContentRegion("ru", "Russia", setOf("ru"), listOf("en", "ru")),
            ContentRegion("id", "Indonesia", setOf("id"), listOf("en", "id")),
        )

    private val byAlias: Map<String, ContentRegion> =
        buildMap {
            for (region in all) {
                for (alias in region.aliases) {
                    put(alias, region)
                }
            }
        }

    fun find(code: String): ContentRegion? = byAlias[code.trim().lowercase()]

    /** Known alias → code, or null if not a content region. */
    fun canonicalizeOrNull(code: String): String? = find(code)?.code

    /** Unknown / empty → us. */
    fun canonicalize(code: String): String = canonicalizeOrNull(code) ?: "us"

    fun displayLabel(code: String): String = find(code)?.label ?: "USA"

    fun recommendedLanguages(country: String): List<String> = find(country)?.recommendedLanguages ?: listOf("en")

    /**
     * Language chips for UI: recommended-for-country first (includes `en`), then the rest.
     */
    fun languageGroupsForCountry(country: String): LanguageGroups {
        val recommended = recommendedLanguages(country)
        val more = LANGUAGE_OPTIONS.filter { it !in recommended }
        return LanguageGroups(recommended = recommended, more = more)
    }

    data class LanguageGroups(val recommended: List<String>, val more: List<String>,)

    /**
     * Languages whose catalog pool is deep enough to stand without English: English can be
     * switched off only when every picked language is one of these. Measured on the live catalog
     * on 29 Oct 2026 (at least 3,600 shows and 39,000 episodes each); Chinese, Indonesian and
     * Hindi are too small. Keep aligned with proxy `ENGLISH_OFF_LANGUAGES`.
     */
    val ENGLISH_OFF_LANGUAGES: Set<String> = setOf("fr", "de", "es", "pt", "ru", "nl")

    /** True when [languages] (codes, with or without `en`) allow English to be switched off. */
    fun canSwitchEnglishOff(languages: List<String>): Boolean {
        val picks = languages.map { it.trim().lowercase().substringBefore('-') }.filter { it.isNotEmpty() && it != "en" }
        return picks.isNotEmpty() && picks.all { it in ENGLISH_OFF_LANGUAGES }
    }

    /**
     * Allowlist, max [MAX_LANGUAGES], English first, unless [englishOff] is set and
     * [canSwitchEnglishOff] allows it: then the picks alone (the server then adds English only
     * where they run thin). Empty / all-invalid → country recommended set.
     */
    fun normalizeLanguages(input: List<String>, country: String, englishOff: Boolean = false,): List<String> {
        val recommended = recommendedLanguages(country)
        val cleaned =
            input
                .asSequence()
                .map { it.trim().lowercase().substringBefore('-') }
                .filter { it in LANGUAGE_ALLOWLIST }
                .distinct()
                .toList()

        if (cleaned.isEmpty()) return recommended.take(MAX_LANGUAGES)
        if (englishOff && canSwitchEnglishOff(cleaned)) return cleaned.filter { it != "en" }.take(MAX_LANGUAGES)

        val ordered = LinkedHashSet<String>()
        ordered.add("en")
        for (code in cleaned) {
            if (ordered.size >= MAX_LANGUAGES) break
            ordered.add(code)
        }
        return ordered.toList()
    }

    /** Indonesian chip `id` also matches PI tag `in`. */
    fun expandLanguagesForQuery(languages: List<String>): List<String> {
        val out = LinkedHashSet<String>()
        for (lang in languages) {
            val code = lang.trim().lowercase().substringBefore('-')
            if (code.isEmpty()) continue
            out.add(code)
            if (code == "id") out.add("in")
        }
        return if (out.isEmpty()) listOf("en") else out.toList()
    }

    fun isOffMarketLanguage(language: String, country: String,): Boolean {
        val code = language.trim().lowercase().substringBefore('-')
        if (code !in LANGUAGE_ALLOWLIST) return true
        return code !in recommendedLanguages(country)
    }

    fun encodeLanguages(languages: List<String>): String = languages.joinToString(",")

    fun decodeLanguages(raw: String?): List<String> = raw
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    /** Locale country → initial content region (extended 11-market set). */
    fun localeDefaultRegion(localeCountry: String): String {
        val normalized = localeCountry.trim().lowercase()
        return when (normalized) {
            "uk" -> "gb"
            else -> canonicalizeOrNull(normalized) ?: "us"
        }
    }

    fun briefingMarket(region: String): String {
        val normalized = region.trim().lowercase()
        if (normalized == "global") return "global"

        return when (canonicalize(normalized)) {
            "us" -> "us"
            "in" -> "in"
            "gb" -> "gb"
            else -> "global"
        }
    }
}
