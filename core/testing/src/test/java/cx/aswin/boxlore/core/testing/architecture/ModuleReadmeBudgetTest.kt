package cx.aswin.boxlore.core.testing.architecture

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Module READMEs describe the module as it is now and stay short (`docs/MODULE_README_TEMPLATE.md`):
 * at most [WORD_BUDGET] words, no history markers (dates, plan or decision labels) and nothing but
 * list items after "See also".
 *
 * READMEs written before this check are listed in `module-readme-budget.txt` with their size at the
 * time. They may shrink but never grow past those numbers, so a change rewrites a README instead of
 * appending to it.
 */
class ModuleReadmeBudgetTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile

    @Test
    fun `module READMEs stay within their budget and hold no history markers`() {
        val allowances = allowances()
        val problems =
            moduleFolders().flatMap { folder ->
                val measured = ReadmeMeasure.of(File(projectRoot, "$folder/README.md").readText())
                val allowed = allowances[folder] ?: ReadmeMeasure(WORD_BUDGET, 0, 0)
                buildList {
                    val wordLimit = maxOf(allowed.words, WORD_BUDGET)
                    if (measured.words > wordLimit) {
                        add("$folder/README.md has ${measured.words} words (limit $wordLimit): rewrite lines instead of adding them")
                    }
                    if (measured.historyMarkers > allowed.historyMarkers) {
                        add("$folder/README.md has ${measured.historyMarkers} dates or plan labels (limit ${allowed.historyMarkers}): history belongs in the PR")
                    }
                    if (measured.linesAfterSeeAlso > allowed.linesAfterSeeAlso) {
                        add("$folder/README.md has ${measured.linesAfterSeeAlso} non-list lines after \"See also\" (limit ${allowed.linesAfterSeeAlso})")
                    }
                }
            }
        assertTrue(
            problems.isEmpty(),
            "Module READMEs must follow docs/MODULE_README_TEMPLATE.md:\n" + problems.joinToString("\n"),
        )
    }

    @Test
    fun `the allowance list names only real modules`() {
        val stale = allowances().keys - moduleFolders().toSet()
        assertTrue(stale.isEmpty(), "module-readme-budget.txt lists folders that are not modules: $stale")
    }

    @Test
    fun `measuring counts words, history markers and lines after See also`() {
        val text =
            """
            # `:core:sample`

            Redesigned 2026-10-17, then again on 30 Oct (P4 PR 16, decision D8; see (D9, below)).

            ## See also

            - [ARCHITECTURE.md](../ARCHITECTURE.md)
            A stray paragraph.
            ## Extra notes
            """.trimIndent()

        val measured = ReadmeMeasure.of(text)

        assertEquals(text.split(Regex("""[ \t\n\u000B\f\r]+""")).count { it.isNotEmpty() }, measured.words)
        assertEquals(5, measured.historyMarkers)
        assertEquals(2, measured.linesAfterSeeAlso)
        assertEquals(ReadmeMeasure(3, 0, 0), ReadmeMeasure.of("Plain current description."))
    }

    private fun moduleFolders(): List<String> {
        val settings = File(projectRoot, "settings.gradle.kts").readText()
        return Regex("""include\("(:[^"]+)"\)""")
            .findAll(settings)
            .map { it.groupValues[1] }
            .filter { it == ":app" || it.startsWith(":core:") || it.startsWith(":feature:") }
            .map { it.removePrefix(":").replace(':', '/') }
            .filter { File(projectRoot, "$it/README.md").isFile }
            .toList()
    }

    private fun allowances(): Map<String, ReadmeMeasure> = File(projectRoot, ALLOWANCES)
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val parts = line.split(Regex("""\s+"""))
            require(parts.size == 4) { "$ALLOWANCES: expected 'folder words markers lines', got '$line'" }
            parts[0] to ReadmeMeasure(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
        }

    private data class ReadmeMeasure(
        val words: Int,
        val historyMarkers: Int,
        val linesAfterSeeAlso: Int,
    ) {
        companion object {
            private val whitespace = Regex("""[ \t\n\u000B\f\r]+""")
            private val markers =
                listOf(
                    Regex("""\b20\d{2}-\d{2}-\d{2}\b"""),
                    Regex("""\b\d{1,2} (?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\b"""),
                    Regex("""\bP\d+[a-z]? PR \d+\b"""),
                    Regex("""\bdecision D\d+\b"""),
                    Regex("""\(D\d+[,)]"""),
                )
            private val seeAlso = Regex("""^##\s+See also\s*$""")

            fun of(text: String): ReadmeMeasure {
                val lines = text.split('\n')
                val seeAlsoAt = lines.indexOfFirst { seeAlso.matches(it) }
                val after =
                    if (seeAlsoAt == -1) {
                        0
                    } else {
                        lines.drop(seeAlsoAt + 1).count { line ->
                            val trimmed = line.trim()
                            trimmed.isNotEmpty() && !trimmed.startsWith("- ") && !trimmed.startsWith("* ")
                        }
                    }
                return ReadmeMeasure(
                    words = text.split(whitespace).count { it.isNotEmpty() },
                    historyMarkers = markers.sumOf { it.findAll(text).count() },
                    linesAfterSeeAlso = after,
                )
            }
        }
    }

    private companion object {
        const val WORD_BUDGET = 2_000
        const val ALLOWANCES = "core/testing/src/test/resources/module-readme-budget.txt"
    }
}
