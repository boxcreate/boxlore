package cx.aswin.boxlore.core.testing.architecture

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/**
 * Listener-facing text always spells the product name "boxlore", all lowercase, and never
 * uses the retired BoxCast name (AGENTS.md). Word boundaries skip code identifiers
 * (`BoxLorePlaybackService`), log tags (`BoxCastPlayer`) and constants (`BOXLORE_API_BASE_URL`);
 * lowercase `boxcast` deep links and legacy migration names are allowed in Kotlin.
 */
class BrandNameGuardTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile

    private val misCasedBrand = Regex("""\b(?:BoxLore|Boxlore|BOXLORE|BoxCast|Boxcast|BOXCAST)\b""")

    // Resources hold only listener-facing text, so the retired name is wrong in any casing.
    private val resourceBrand = Regex("""\b(?:BoxLore|Boxlore|BOXLORE|BoxCast|Boxcast|BOXCAST|boxcast)\b""")

    private val stringLiteral = Regex(""""((?:[^"\\]|\\.)*)"""")

    // HTTP User-Agent product tokens: podcast hosts and stats services identify the app by them.
    private val allowedLiterals =
        setOf(
            "core/rss/src/main/java/cx/aswin/boxlore/core/rss/RssFeedClient.kt" to
                "BoxLore/1.0 (Android; RSS reader)",
            "core/playback/src/main/java/cx/aswin/boxlore/core/playback/service/PlaybackServicePlayerFactory.kt" to
                "BoxLore",
            "core/downloads/src/main/java/cx/aswin/boxlore/core/downloads/DownloadRepository.kt" to "BoxLore",
            "feature/onboarding/src/main/java/cx/aswin/boxlore/feature/onboarding/EchoShowPreview.kt" to "BoxLore",
        )

    @Test
    fun `string resources and manifest labels spell boxlore in lowercase`() {
        val resourceFiles =
            mainSourceFiles { it.extension == "xml" && it.parentFile?.name.orEmpty().startsWith("values") }
                .filter { it.invariantSeparatorsPath.contains("/src/main/res/") }
        require(resourceFiles.isNotEmpty()) { "No value resources found under $projectRoot" }

        val violations = mutableListOf<String>()
        for (file in resourceFiles) {
            resourceTexts(file)
                .filter { resourceBrand.containsMatchIn(it) }
                .forEach { violations += "${file.relativeTo(projectRoot)}: \"$it\"" }
        }
        mainSourceFiles { it.name == "AndroidManifest.xml" }.forEach { manifest ->
            Regex("""android:label="([^"@][^"]*)"""")
                .findAll(manifest.readText())
                .map { it.groupValues[1] }
                .filter { resourceBrand.containsMatchIn(it) }
                .forEach { violations += "${manifest.relativeTo(projectRoot)}: android:label=\"$it\"" }
        }

        assertTrue(
            violations.isEmpty(),
            "Spell the product name \"boxlore\" in listener-facing resources:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `kotlin string literals spell boxlore in lowercase`() {
        val sourceFiles = mainSourceFiles { it.extension == "kt" }
        require(sourceFiles.isNotEmpty()) { "No Kotlin sources found under $projectRoot" }

        val violations = mutableListOf<String>()
        for (file in sourceFiles) {
            val path = file.relativeTo(projectRoot).invariantSeparatorsPath
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                stringLiteral
                    .findAll(line)
                    .map { it.groupValues[1] }
                    .filter { misCasedBrand.containsMatchIn(it) && (path to it) !in allowedLiterals }
                    .forEach { violations += "$path:${index + 1}: \"$it\"" }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Spell the product name \"boxlore\" in listener-facing text, or move protocol identities " +
                "into the allowlist with a reason:\n" + violations.joinToString("\n"),
        )
    }

    /** `<string>` and plural/array `<item>` text; style items are attribute values, not copy. */
    private fun resourceTexts(file: File): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return listOf("string", "item").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length)
                .map { nodes.item(it) as Element }
                .filter { it.parentNode.nodeName != "style" }
                .map { it.textContent }
        }
    }

    private fun mainSourceFiles(predicate: (File) -> Boolean): List<File> = listOf("app", "core", "feature")
        .map { File(projectRoot, it) }
        .filter { it.isDirectory }
        .flatMap { root ->
            root
                .walkTopDown()
                .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" }
                .filter { it.isFile && it.invariantSeparatorsPath.contains("/src/main/") && predicate(it) }
                .toList()
        }
}
