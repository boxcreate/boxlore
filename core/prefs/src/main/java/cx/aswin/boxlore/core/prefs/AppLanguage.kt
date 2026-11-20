package cx.aswin.boxlore.core.prefs

import java.util.Locale

/**
 * A language boxlore's own text can be shown in (Settings › Appearance › App language). [tag] is the
 * BCP 47 tag stored and handed to Android; [SYSTEM]'s empty tag follows the phone's language.
 * [nativeName] is the language's name in that language and is never translated; [SYSTEM] has none
 * and shows its translated label instead.
 *
 * Server text (Echo's replies, briefings, announcements) and show and episode names stay as sent.
 */
enum class AppLanguage(val tag: String, val nativeName: String?) {
    SYSTEM("", null),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    ;

    companion object {
        /** The languages boxlore is translated into, beyond English. */
        val translated: List<AppLanguage> = listOf(SPANISH, PORTUGUESE_BRAZIL, GERMAN, FRENCH)

        /**
         * The choice a stored or system tag stands for: an exact match, else the same language
         * (`es-MX` is Spanish, `pt-BR`/`pt` are Brazilian Portuguese, `de-AT` is German), else [SYSTEM].
         */
        fun fromTag(tag: String?): AppLanguage {
            if (tag.isNullOrBlank()) return SYSTEM
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }?.let { return it }
            val locale = Locale.forLanguageTag(tag)
            return AppLanguageMatcher.forLocale(locale) ?: if (locale.language == ENGLISH.tag) ENGLISH else SYSTEM
        }
    }
}

/**
 * Whether the phone suggests one of boxlore's translations, for onboarding's first question.
 *
 * 1. The first locale in the phone's language list whose language boxlore has: Spanish, German and
 *    French in any region; Portuguese only as Brazilian (`pt-BR` or plain `pt`).
 * 2. Otherwise the phone's region (the first locale's country): Spain and the Spanish-speaking
 *    Americas, Brazil, Germany/Austria/Liechtenstein, France/Monaco. Regions with several languages
 *    (Switzerland, Belgium, Canada, Luxembourg) suggest one only through the language list.
 * 3. Otherwise none, and onboarding asks nothing.
 */
object AppLanguageMatcher {
    private val spanishRegions = setOf(
        "ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PR", "PA", "UY",
    )
    private val germanRegions = setOf("DE", "AT", "LI")
    private val frenchRegions = setOf("FR", "MC")

    fun suggest(deviceLocales: List<Locale>): AppLanguage? {
        deviceLocales.firstNotNullOfOrNull(::forLocale)?.let { return it }
        return forRegion(deviceLocales.firstOrNull()?.country.orEmpty())
    }

    /**
     * The translation boxlore's screens are in for [choice], or null for English. With [AppLanguage.SYSTEM]
     * it is the first phone language boxlore has, English included, as Android picks resources.
     */
    fun shown(choice: AppLanguage, systemLocales: List<Locale>): AppLanguage? = when (choice) {
        AppLanguage.SYSTEM ->
            systemLocales
                .firstNotNullOfOrNull { if (it.language == AppLanguage.ENGLISH.tag) AppLanguage.ENGLISH else forLocale(it) }
                ?.takeIf { it != AppLanguage.ENGLISH }
        AppLanguage.ENGLISH -> null
        else -> choice
    }

    /** The translation for this locale's language, or null (English, Portuguese outside Brazil, others). */
    internal fun forLocale(locale: Locale): AppLanguage? = when (locale.language) {
        "es" -> AppLanguage.SPANISH
        "de" -> AppLanguage.GERMAN
        "fr" -> AppLanguage.FRENCH
        "pt" -> AppLanguage.PORTUGUESE_BRAZIL.takeIf { locale.country.isEmpty() || locale.country == "BR" }
        else -> null
    }

    /** The translation spoken in this region (ISO 3166 code, any case), or null. */
    fun forRegion(region: String): AppLanguage? = when (region.uppercase(Locale.ROOT)) {
        in spanishRegions -> AppLanguage.SPANISH
        "BR" -> AppLanguage.PORTUGUESE_BRAZIL
        in germanRegions -> AppLanguage.GERMAN
        in frenchRegions -> AppLanguage.FRENCH
        else -> null
    }
}
