package cx.aswin.boxlore.core.testing.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Reads a module's `res/values*` folders and checks every translation (`values-es`, `values-pt-rBR`,
 * …) against the English text in the unqualified folders. Used by [TranslationResourcesGuardTest]
 * on every module and on small fixtures. Rules: docs/i18n/TRANSLATING.md.
 */
internal object TranslationResources {
    /** One `<string>`, `<plurals>` or `<string-array>` with its text per item (quantity or index). */
    data class Entry(val kind: String, val name: String, val items: Map<String, String>, val translatable: Boolean)

    /** A values folder's language, such as `es`, `pt-rBR` or `b+es+419`; null for the default (English) folders. */
    fun localeOf(folder: String): String? {
        if (!folder.startsWith("values")) return null
        val parts = folder.removePrefix("values").split('-').filter { it.isNotEmpty() }
        val index = parts.indexOfFirst { it.startsWith("b+") || (LANGUAGE.matches(it) && it !in NOT_LANGUAGES) }
        if (index < 0) return null
        val language = parts[index]
        val region = parts.getOrNull(index + 1)?.takeIf { REGION.matches(it) }
        return if (region != null) "$language-$region" else language
    }

    /** The language part only (`pt` for `pt-rBR`, `es` for `b+es+419`), which decides plural forms. */
    fun languageOf(locale: String): String = locale.removePrefix("b+").split('-', '+').first()

    fun entries(file: File): List<Entry> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        val children = root.childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }.mapNotNull { element ->
            val translatable = element.getAttribute("translatable") != "false"
            when (element.tagName) {
                "string" -> Entry("string", element.getAttribute("name"), mapOf("" to element.textContent), translatable)
                "plurals" -> Entry("plurals", element.getAttribute("name"), items(element) { it.getAttribute("quantity") }, translatable)
                "string-array" -> Entry("string-array", element.getAttribute("name"), items(element, indexed = true), translatable)
                else -> null
            }
        }
    }

    private fun items(parent: Element, indexed: Boolean = false, key: (Element) -> String = { "" }): Map<String, String> {
        val nodes = parent.getElementsByTagName("item")
        return (0 until nodes.length).associate { index ->
            val item = nodes.item(index) as Element
            (if (indexed) index.toString() else key(item)) to item.textContent
        }
    }

    /** `%1$s`, `%d`, `%.1f` and `{month}` style tokens, in order; `%%` is a literal percent sign. */
    fun placeholders(text: String): List<String> =
        (FORMAT.findAll(text).map { it.value }.filter { it != "%%" } + BRACE.findAll(text).map { it.value }).sorted().toList()

    /**
     * Everything wrong with one module's translations, as readable lines. [resDir] is a module's
     * `src/main/res`; [label] prefixes each line.
     */
    fun problems(resDir: File, label: String): List<String> {
        val folders = resDir.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }
        val english = folders.filter { localeOf(it.name) == null }.flatMap(::xmlEntries).associateBy { it.kind to it.name }
        val untranslatable = english.values.filter { !it.translatable }.map { it.kind to it.name }.toSet()
        return folders.mapNotNull { folder -> localeOf(folder.name)?.let { folder to it } }.flatMap { (folder, locale) ->
            xmlEntries(folder).flatMap { entry ->
                val where = "$label/${folder.name} ${entry.kind} ${entry.name}"
                val source = english[entry.kind to entry.name]
                when {
                    (entry.kind to entry.name) in untranslatable -> listOf("$where: marked translatable=\"false\" in English; delete the translation")
                    source == null -> listOf("$where: no English string with this name; delete it or add the English first")
                    else -> compare(where, source, entry, languageOf(locale))
                }
            }
        }
    }

    private fun xmlEntries(folder: File): List<Entry> = folder.listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.flatMap(::entries)

    private fun compare(where: String, english: Entry, translated: Entry, language: String): List<String> = when (english.kind) {
        "plurals" -> comparePlurals(where, english, translated, language)
        else -> compareItems(where, english, translated)
    } + compareBrand(where, english, translated)

    private fun comparePlurals(where: String, english: Entry, translated: Entry, language: String): List<String> = buildList {
        val required = REQUIRED_PLURALS[language] ?: setOf("other")
        val allowed = ALLOWED_PLURALS[language] ?: CLDR_CATEGORIES
        val quantities = translated.items.keys
        (required - quantities).forEach { add("$where: missing quantity \"$it\" ($language needs ${required.sorted()})") }
        (quantities - allowed).forEach { add("$where: quantity \"$it\" isn't used in $language (allowed ${allowed.sorted()})") }
        val englishTokens = english.items.values.flatMap(::placeholders).toSet()
        translated.items.forEach { (quantity, text) ->
            val extra = placeholders(text).toSet() - englishTokens
            if (extra.isNotEmpty()) add("$where[$quantity]: placeholders $extra aren't in the English")
        }
        val englishOther = english.items["other"]?.let(::placeholders)
        val translatedOther = translated.items["other"]?.let(::placeholders)
        if (englishOther != null && translatedOther != null && englishOther != translatedOther) {
            add("$where[other]: placeholders $translatedOther differ from the English $englishOther")
        }
    }

    /** Strings and string arrays: the same placeholders item by item, and arrays the same length. */
    private fun compareItems(where: String, english: Entry, translated: Entry): List<String> = buildList {
        if (english.kind == "string-array" && english.items.size != translated.items.size) {
            add("$where: ${translated.items.size} items, the English has ${english.items.size}")
        }
        translated.items.forEach { (key, text) ->
            val source = english.items[key] ?: return@forEach
            if (placeholders(source) != placeholders(text)) {
                add("$where${suffix(key)}: placeholders ${placeholders(text)} differ from the English ${placeholders(source)}")
            }
        }
    }

    private fun compareBrand(where: String, english: Entry, translated: Entry): List<String> = translated.items.mapNotNull { (key, text) ->
        val source = english.items[key] ?: english.items["other"] ?: return@mapNotNull null
        if (BRAND in source && BRAND !in text) "$where${suffix(key)}: the English says \"$BRAND\"; keep it, lowercase and untranslated" else null
    }

    private fun suffix(key: String): String = if (key.isEmpty()) "" else "[$key]"

    /** CLDR plural categories per language (cardinals) that translations must and may use. */
    private val REQUIRED_PLURALS = mapOf(
        "es" to setOf("one", "other"),
        "pt" to setOf("one", "other"),
        "fr" to setOf("one", "other"),
        "de" to setOf("one", "other"),
        "en" to setOf("one", "other"),
    )
    private val ALLOWED_PLURALS = mapOf(
        "es" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "de" to setOf("one", "other"),
        "en" to setOf("one", "other"),
    )
    private val CLDR_CATEGORIES = setOf("zero", "one", "two", "few", "many", "other")

    private const val BRAND = "boxlore"
    private val LANGUAGE = Regex("^[a-z]{2,3}$")
    private val REGION = Regex("^r[A-Z]{2}$")

    // Three-letter qualifiers that are not languages (UI mode "car").
    private val NOT_LANGUAGES = setOf("car")
    private val FORMAT = Regex("""%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[sdfxXcbeEgGoaAhH%]""")
    private val BRACE = Regex("""\{[A-Za-z_][A-Za-z0-9_]*\}""")
}
