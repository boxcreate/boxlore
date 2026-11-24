package cx.aswin.boxlore.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A ratchet on stock Material hosts that the shared boxlore hosts replace: sheets must go through
 * `BoxloreModalBottomSheet` (it keeps content when a sheet becomes a centred panel and stays off the
 * hinge), and new menus and snackbar hosts must not be added outside `:core:designsystem`. Today's
 * call sites are allowed below; an area that moves a screen to the shared host lowers its count,
 * and the test then asks for the entry to be removed. It reads source files only, never `build/`.
 */
class SharedHostUsageTest {
    private data class Host(
        val name: String,
        val replacement: String,
        val skipDesignSystem: Boolean,
        val allowed: Map<String, Int>,
        val skipFiles: Set<String> = emptySet(),
    ) {
        val call = Regex("""\b$name\s*\(""")
        val qualifiedCall = Regex("""androidx\.compose\.material3\.$name\s*\(""")
        val directImport = Regex("""^import androidx\.compose\.material3\.$name\s*$""", RegexOption.MULTILINE)
        val wildcardImport = Regex("""^import androidx\.compose\.material3\.\*\s*$""", RegexOption.MULTILINE)
        val alias = Regex("""^import \S+ as $name\s*$""", RegexOption.MULTILINE)

        /** Calls in [source] that reach Material's own [name]. */
        fun stockCalls(source: String): Int {
            val qualified = qualifiedCall.findAll(source).count()
            val resolvesToMaterial = !alias.containsMatchIn(source) &&
                (directImport.containsMatchIn(source) || wildcardImport.containsMatchIn(source))
            val unqualified = if (resolvesToMaterial) call.findAll(source).count() - qualified else 0
            return qualified + unqualified
        }
    }

    private val hosts = listOf(
        Host(
            name = "ModalBottomSheet",
            replacement = "BoxloreModalBottomSheet",
            skipDesignSystem = false,
            skipFiles = setOf("core/designsystem/src/main/java/cx/aswin/boxlore/core/designsystem/components/BoxloreModalBottomSheet.kt"),
            allowed = mapOf(
                "feature/home/src/main/java/cx/aswin/boxlore/feature/home/components/ReviewSheet.kt" to 1,
            ),
        ),
        Host(
            name = "DropdownMenu",
            replacement = "the shared menu in :core:designsystem",
            skipDesignSystem = true,
            allowed = mapOf(
                "feature/settings/src/main/java/cx/aswin/boxlore/feature/settings/pages/supportworlds/SupportWorldDebugMenu.kt" to 1,
            ),
        ),
        Host(
            name = "SnackbarHost",
            replacement = "the shared snackbar host in :core:designsystem",
            skipDesignSystem = true,
            allowed = emptyMap(),
        ),
    )

    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    /** Main Kotlin sources of `:app`, every `:core:*` and every `:feature:*` module. */
    private fun mainSources(): List<Pair<String, File>> {
        val moduleDirs = listOf(File(root, "app")) +
            listOf("core", "feature").flatMap { group -> File(root, group).listFiles { file -> file.isDirectory }.orEmpty().sorted() }
        return moduleDirs.flatMap { module ->
            val main = File(module, "src/main")
            if (!main.isDirectory) return@flatMap emptyList()
            main.walkTopDown()
                .onEnter { it.name != "build" }
                .filter { it.isFile && it.extension == "kt" }
                .map { it.relativeTo(root).invariantSeparatorsPath to it }
                .toList()
        }
    }

    @Test
    fun `stock sheets, menus and snackbar hosts stay at today's call sites or fewer`() {
        val sources = mainSources()
        assertTrue("No sources found under $root", sources.size > 100)
        val problems = hosts.flatMap { host ->
            val found = sources
                .filterNot { (path, _) -> path in host.skipFiles || (host.skipDesignSystem && path.startsWith("core/designsystem/")) }
                .associate { (path, file) -> path to host.stockCalls(file.readText()) }
                .filterValues { it > 0 }
            val added = found.filter { (path, count) -> count > (host.allowed[path] ?: 0) }
                .map { (path, count) -> "$path: $count stock ${host.name} call(s), ${host.allowed[path] ?: 0} allowed. Use ${host.replacement}." }
            val shrunk = host.allowed.filter { (path, count) -> (found[path] ?: 0) < count }
                .map { (path, count) -> "$path: ${found[path] ?: 0} stock ${host.name} call(s) left; lower its allowance from $count." }
            added + shrunk
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the scan recognises Material imports, wildcards and the boxlore alias`() {
        val sheet = hosts.first { it.name == "ModalBottomSheet" }
        val direct = "import androidx.compose.material3.ModalBottomSheet\nfun a() { ModalBottomSheet(onDismissRequest = {}) {} }"
        val wildcard = "import androidx.compose.material3.*\nfun a() { ModalBottomSheet({}) {}; rememberModalBottomSheetState() }"
        val aliased = "import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet\n" +
            "fun a() { ModalBottomSheet({}) {}; ModalBottomSheetProperties() }"
        val qualified = "fun a() { androidx.compose.material3.ModalBottomSheet({}) {} }"
        val shared = "import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet\nfun a() { BoxloreModalBottomSheet({}) {} }"
        assertEquals(1, sheet.stockCalls(direct))
        assertEquals(1, sheet.stockCalls(wildcard))
        assertEquals(0, sheet.stockCalls(aliased))
        assertEquals(1, sheet.stockCalls(qualified))
        assertEquals(0, sheet.stockCalls(shared))
        val menu = hosts.first { it.name == "DropdownMenu" }
        assertEquals(1, menu.stockCalls("import androidx.compose.material3.DropdownMenu\nfun a() { DropdownMenu(true, {}) { DropdownMenuItem() } }"))
    }
}
