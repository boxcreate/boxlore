package cx.aswin.boxlore.core.testing.i18n

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Listener-facing text belongs in resources so it can be translated. Text still written in Kotlin is
 * listed in `docs/i18n/hardcoded-text-baseline.txt`, which may only shrink: a new literal fails, and
 * so does a listed one that is gone (take it off the list). The current list is always written to
 * `core/testing/build/i18n/hardcoded-text-baseline.txt`; with `BOXLORE_I18N_BASELINE=write` set the
 * test rewrites the baseline itself. Only ever regenerate it after moving text out, never to let new
 * text in.
 */
class HardcodedTextGuardTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: generateSequence(File(".").canonicalFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    private val baselineFile get() = File(projectRoot, BASELINE)

    @Test
    fun `no new hard-coded listener text, and the baseline only lists what is still there`() {
        val current = I18nSources.moduleDirs(projectRoot).flatMap { HardcodedText.scan(it, projectRoot) }.map { it.line() }
        val rendered = render(current)
        File(projectRoot, "core/testing/build/i18n/hardcoded-text-baseline.txt").apply { parentFile.mkdirs() }.writeText(rendered)
        if (System.getenv("BOXLORE_I18N_BASELINE") == "write") baselineFile.writeText(rendered)

        val baseline = baselineFile.takeIf { it.isFile }?.readLines().orEmpty().filter { it.isNotBlank() && !it.startsWith("#") }
        val added = minus(current, baseline)
        val gone = minus(baseline, current)
        assertTrue(
            added.isEmpty(),
            "Move this text into string resources (stringResource / UiText), or end the line with " +
                "\"// i18n-ignore\" if it is not listener text (docs/i18n/TRANSLATING.md):\n" + added.joinToString("\n"),
        )
        assertTrue(
            gone.isEmpty(),
            "These are no longer hard-coded; regenerate $BASELINE (BOXLORE_I18N_BASELINE=write, or copy " +
                "core/testing/build/i18n/hardcoded-text-baseline.txt):\n" + gone.joinToString("\n"),
        )
    }

    @Test
    fun `the scan finds UI text and skips keys, templates and opt-outs`() {
        val source =
            """
            Text("Go Explore")
            Text(text = "Hide played episodes", maxLines = 1)
            Icon(Icons.Rounded.Close, contentDescription = "Close")
            SettingsRow(title = "Theme & colors", label = "Daily mix")
            snackbarHostState.showSnackbar("Couldn't send your reply.")
            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT)
            builder.setContentTitle("New episodes").setContentText("${'$'}count new from ${'$'}show")
            NotificationChannel(CHANNEL_ID, "Support replies", NotificationManager.IMPORTANCE_DEFAULT)
            AnimatedContent(targetState = step, label = "onboarding_step")
            animateFloatAsState(1f, label = "loreSelection")
            animateColorAsState(color, label = "LoreHaloDrift")
            Text("Back")
            TrackScreenView(name = "appearance_language")
            Text(text = "${'$'}{count}")
            Text("https://boxlore.in/share")
            // Text("Commented out")
            Text("ID3") // i18n-ignore
            """.trimIndent()
        val found = HardcodedText.findings(source, "m/File.kt").map { it.literal }
        assertEquals(
            listOf(
                "Go Explore",
                "Hide played episodes",
                "Close",
                "Theme & colors",
                "Daily mix",
                "Couldn't send your reply.",
                "Copied",
                "New episodes",
                "${'$'}count new from ${'$'}show",
                "Support replies",
                "Back",
            ),
            found,
        )
        assertTrue(HardcodedText.findings("// i18n-ignore-file: debug screen\nText(\"Trigger now\")", "m/Debug.kt").isEmpty())
        assertFalse(HardcodedText.isText("${'$'}{a}${'$'}b"))
        assertTrue(HardcodedText.isText("${'$'}{a} min"))
    }

    @Test
    fun `the baseline counts repeats`() {
        assertEquals(listOf("a", "a"), minus(listOf("a", "a", "a", "b"), listOf("a", "b")))
        assertEquals(emptyList<String>(), minus(listOf("a"), listOf("a", "a")))
    }

    /** What [from] has beyond [other], counting repeated lines. */
    private fun minus(from: List<String>, other: List<String>): List<String> {
        val left = other.groupingBy { it }.eachCount().toMutableMap()
        return from.filter { line ->
            val count = left[line] ?: 0
            if (count > 0) {
                left[line] = count - 1
                false
            } else {
                true
            }
        }
    }

    private fun render(lines: List<String>): String {
        val perModule = lines.groupingBy { it.substringBefore("/src/") }.eachCount().toSortedMap()
        val header = buildString {
            appendLine("# Listener text still hard-coded in Kotlin (HardcodedTextGuardTest). This list may only shrink.")
            appendLine("# Regenerate after moving text into resources: docs/i18n/TRANSLATING.md. Format: file<TAB>literal.")
            appendLine("# Total ${lines.size}. Per module: " + perModule.entries.joinToString(", ") { "${it.key} ${it.value}" })
        }
        return header + lines.sorted().joinToString("\n", postfix = if (lines.isEmpty()) "" else "\n")
    }

    private companion object {
        const val BASELINE = "docs/i18n/hardcoded-text-baseline.txt"
    }
}
