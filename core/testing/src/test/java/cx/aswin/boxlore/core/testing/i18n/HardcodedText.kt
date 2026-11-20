package cx.aswin.boxlore.core.testing.i18n

import java.io.File

/** The modules whose sources and resources the i18n guards read: `:app`, `:wear`, every core and feature module. */
internal object I18nSources {
    fun moduleDirs(projectRoot: File): List<File> {
        val nested = listOf("core", "feature").flatMap { group ->
            File(projectRoot, group).listFiles().orEmpty().filter { File(it, "build.gradle.kts").isFile }
        }
        return (listOf(File(projectRoot, "app"), File(projectRoot, "wear")) + nested).filter { it.isDirectory }.sortedBy { it.path }
    }
}

/**
 * Finds listener-facing text written straight into Kotlin instead of resources: literals passed to
 * `Text(…)`, to UI parameters (`text`, `contentDescription`, `title`, `label`, …), to snackbars and
 * toasts, and to notification builders and channels. Identifier-like literals (`"home"`,
 * `"onboarding_step"`, URLs) are keys, not text, and are skipped.
 *
 * Opt-outs, each with a reason in the comment: a line ending in `// i18n-ignore` (a protocol value
 * that looks like text), or a file containing `i18n-ignore-file` (debug-only screens).
 */
internal object HardcodedText {
    /** A finding: the module-relative file and the literal as written. */
    data class Finding(val path: String, val literal: String) {
        fun line(): String = "$path\t${literal.replace("\t", "\\t")}"
    }

    private const val LITERAL = """"((?:[^"\\]|\\.)*)""""
    private val parameters =
        listOf(
            "text", "contentDescription", "title", "label", "subtitle", "supportingText", "placeholder", "message",
            "actionLabel", "description", "confirmLabel", "dismissLabel", "headline", "body",
        ).joinToString("|")
    private val patterns =
        listOf(
            Regex("""\bText\(\s*$LITERAL"""),
            Regex("""\b(?:$parameters)\s*=\s*$LITERAL"""),
            Regex("""\bshowSnackbar\(\s*(?:message\s*=\s*)?$LITERAL"""),
            Regex("""\bToast\.makeText\([^,]+,\s*$LITERAL"""),
            Regex("""\.(?:setContentTitle|setContentText|setSubText|setTicker|setBigContentTitle|setSummaryText|bigText|setName|setDescription)\(\s*$LITERAL"""),
            Regex("""\baddAction\([^,]*,\s*$LITERAL"""),
            Regex("""\bNotificationChannel(?:Compat)?\(\s*[^,]+,\s*$LITERAL"""),
        )
    private val keyLike = Regex("""^[a-z0-9_.:/#?=&%+\-]*$""")

    // Animation and test labels written as one camelCase or PascalCase word ("loreSelection", "LoreHaloDrift").
    private val codeName = Regex("""^(?:[a-z]+[A-Z][A-Za-z0-9]*|[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+)$""")
    private val template = Regex("""\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*""")

    fun scan(moduleDir: File, projectRoot: File): List<Finding> = listOf("src/main/java", "src/main/kotlin")
        .map { File(moduleDir, it) }
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        .sortedBy { it.path }
        .flatMap { file -> findings(file.readText(), file.relativeTo(projectRoot).invariantSeparatorsPath) }

    fun findings(source: String, path: String): List<Finding> {
        if ("i18n-ignore-file" in source) return emptyList()
        return source.lines().flatMap { raw ->
            val line = raw.trim()
            if (isCommentOrIgnored(line)) {
                emptyList()
            } else {
                patterns.flatMap { pattern -> pattern.findAll(line).map { it.groupValues[1] } }
                    .filter(::isText)
                    .map { Finding(path, it) }
            }
        }
    }

    private fun isCommentOrIgnored(line: String): Boolean =
        listOf("//", "*", "/*").any(line::startsWith) || line.endsWith("// i18n-ignore")

    /** Words a listener reads: some letters left after removing `$templates`, and not a key, ID, URL or code name. */
    internal fun isText(literal: String): Boolean {
        if (literal.isBlank() || keyLike.matches(literal) || codeName.matches(literal)) return false
        return template.replace(literal, "").any { it.isLetter() }
    }
}
