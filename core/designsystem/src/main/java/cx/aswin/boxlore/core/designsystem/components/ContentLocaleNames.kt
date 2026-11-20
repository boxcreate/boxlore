package cx.aswin.boxlore.core.designsystem.components

import cx.aswin.boxlore.core.model.ContentRegions
import java.util.Locale

/**
 * A content language's name in [locale], as it reads inside a sentence: boxlore's own English names
 * in English, the system's name elsewhere ("German", "allemand", "Deutsch"). Chips capitalise it with
 * [capitalizedIn]. Unknown codes fall back to the English name, then the code.
 */
fun contentLanguageName(code: String, locale: Locale): String {
    val english = ContentRegions.LANGUAGE_LABELS[code] ?: code
    if (locale.language == Locale.ENGLISH.language) return english
    val name = Locale.forLanguageTag(code).getDisplayLanguage(locale)
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: english
}

/**
 * A content region's name in [locale]: boxlore's own English labels ("USA", "UK") in English, the
 * system's country name elsewhere. Unknown codes read as the default region, like
 * [ContentRegions.displayLabel].
 */
fun contentRegionName(code: String, locale: Locale): String {
    val english = ContentRegions.displayLabel(code)
    if (locale.language == Locale.ENGLISH.language) return english
    val region = ContentRegions.canonicalize(code)
    val name = Locale.Builder().setRegion(region.uppercase(Locale.ROOT)).build().getDisplayCountry(locale)
    return name.takeIf { it.isNotBlank() } ?: english
}

/** [text] with its first letter capitalised the way [locale] does it, for chips and list rows. */
internal fun capitalizedIn(text: String, locale: Locale): String = text.replaceFirstChar { it.titlecase(locale) }
