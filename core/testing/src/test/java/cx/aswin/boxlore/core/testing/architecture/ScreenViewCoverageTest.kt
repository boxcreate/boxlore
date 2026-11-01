package cx.aswin.boxlore.core.testing.architecture

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every composable screen, page, sheet, dialog and tab in the features and the app sends a page view:
 * its body calls `TrackScreenView` (`:core:designsystem`), the one page-view helper. A composable
 * named `…Screen`, `…Page`, `…Sheet`, `…Dialog` or `…Tab` without it fails, unless [EXEMPT] says why
 * it needs none. Inline dialogs and menus aren't named, so they're tracked by hand where they matter.
 */
class ScreenViewCoverageTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: generateSequence(File(".").canonicalFile) { it.parentFile }.first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `every screen, page, sheet, dialog and tab sends a page view`() {
        val missing = untracked().keys - EXEMPT.keys
        assertTrue(
            missing.isEmpty(),
            "Add TrackScreenView(\"<screen_name>\") to these, or list them in ScreenViewCoverageTest.EXEMPT with a reason: $missing",
        )
    }

    @Test
    fun `the exempt list only names screens that exist and still send nothing`() {
        val stale = EXEMPT.keys - untracked().keys
        assertEquals(emptySet<String>(), stale, "These now send a page view or are gone; take them off EXEMPT")
    }

    /** `<module>:<path under cx/aswin/boxlore/>#<function>` of each named destination without `TrackScreenView`. */
    private fun untracked(): Map<String, String> =
        sourceRoots()
            .flatMap { (module, root) -> root.walkTopDown().filter { it.extension == "kt" }.map { module to it } }
            .flatMap { (module, file) ->
                val text = file.readText()
                val relative = file.relativeTo(File(file.path.substringBefore(PACKAGE_DIR) + PACKAGE_DIR)).path
                DESTINATION
                    .findAll(text)
                    .filter { isComposable(text, it.range.first) }
                    .mapNotNull { match -> body(text, match.range.last + 1)?.let { match.groupValues[1] to it } }
                    .filterNot { (_, body) -> "TrackScreenView(" in body }
                    .map { (name, _) -> "$module:$relative#$name" to name }
            }.toMap()

    private fun sourceRoots(): List<Pair<String, File>> {
        val features =
            File(projectRoot, "feature").listFiles().orEmpty().filter { it.isDirectory }.map { module ->
                module.name to File(module, "src/main/java")
            }
        return (features + ("app" to File(projectRoot, "app/src/main/java"))).filter { it.second.isDirectory }
    }

    /** The annotations right above a declaration include `@Composable`. */
    private fun isComposable(text: String, declarationStart: Int): Boolean {
        val before = text.substring(maxOf(0, declarationStart - ANNOTATION_LOOKBACK), declarationStart)
        val annotations = before.split(Regex("""\n\s*\n|\}\s*\n""")).last()
        return "@Composable" in annotations
    }

    /** The function body after the parameter list starting at [paramsStart], or null for an expression body. */
    private fun body(text: String, paramsStart: Int): String? {
        var index = paramsStart
        var depth = 1
        while (depth > 0 && index < text.length) {
            when (text[index]) {
                '(' -> depth++
                ')' -> depth--
            }
            index++
        }
        val open = text.indexOf('{', index).takeIf { it >= 0 } ?: return null
        if ('=' in text.substring(index, open)) return null
        var close = open + 1
        depth = 1
        while (depth > 0 && close < text.length) {
            when (text[close]) {
                '{' -> depth++
                '}' -> depth--
            }
            close++
        }
        return text.substring(open, close)
    }

    private companion object {
        const val PACKAGE_DIR = "/cx/aswin/boxlore/"
        const val ANNOTATION_LOOKBACK = 400
        val DESTINATION = Regex("""fun\s+([A-Z]\w*(?:Screen|Page|Sheet|Dialog|Tab))\s*\(""")

        private const val DEBUG = "debug and preview screens; release builds don't show them"
        private const val UNUSED = "not shown anywhere yet"
        private const val WRAPPER = "a wrapper or frame; the screen it shows sends the page view"
        private const val ONBOARDING_STEP = "an onboarding step; OnboardingScreen sends its onboarding_step_viewed"
        private const val SHEET_CONTENT = "the content of a sheet, page or tab that sends the page view itself, empty or not"

        /** Destinations that need no page view of their own, and why. Keep it short. */
        val EXEMPT: Map<String, String> =
            mapOf(
                "home:feature/home/DebugScreen.kt#DebugScreen" to DEBUG,
                "home:feature/home/DebugScreenContent.kt#DebugScreenPagerPage" to DEBUG,
                "home:feature/home/components/MarqueePreview.kt#MarqueePreviewScreen" to DEBUG,
                "info:feature/info/components/EpisodeOptionsSheet.kt#EpisodeOptionsSheet" to UNUSED,
                "onboarding:feature/onboarding/AiChatOnboardingScreen.kt#AiChatOnboardingScreen" to UNUSED,
                "onboarding:feature/onboarding/ImportOnboardingScreen.kt#ImportOnboardingScreen" to UNUSED,
                "info:feature/info/branded/conspiracy/ConspiracyHeader.kt#PaperTab" to "a Showcase page decoration, not a destination",
                "library:feature/library/DownloadedEpisodesScreen.kt#DownloadedEpisodesScreen" to WRAPPER,
                "library:feature/library/DownloadedEpisodesScreen.kt#DownloadedShowEpisodesScreen" to WRAPPER,
                "library:feature/library/subscriptions/SubscriptionFolderPages.kt#OpenFolderPage" to WRAPPER,
                "library:feature/library/subscriptions/SubscriptionFolderPages.kt#FolderEditorPage" to WRAPPER,
                "library:feature/library/subscriptions/SubscriptionFolderPages.kt#EditorDialog" to WRAPPER,
                "app:ui/libraryimport/OpmlImportDialog.kt#OpmlImportDialog" to WRAPPER,
                "app:ui/libraryimport/ImportPage.kt#ImportPage" to WRAPPER,
                "onboarding:feature/onboarding/WelcomeScreen.kt#WelcomeScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/GenreOnboardingScreen.kt#GenrePickerScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/GenreOnboardingScreen.kt#SubGenrePickerScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/GenreOnboardingScreen.kt#ActivityPickerScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/GenreOnboardingScreen.kt#LengthPickerScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/SearchOnboardingScreen.kt#OnboardingSearchScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/AiOnboardingScreen.kt#AiOnboardingScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/EchoOnboardingScreen.kt#EchoOnboardingScreen" to ONBOARDING_STEP,
                "onboarding:feature/onboarding/AiSuggestionsScreen.kt#AiSuggestionsScreen" to ONBOARDING_STEP,
                "player:feature/player/v2/ControlDeck.kt#SpeedSheet" to SHEET_CONTENT,
                "player:feature/player/v2/ControlDeck.kt#SleepSheet" to SHEET_CONTENT,
                "settings:feature/settings/pages/SupportDevelopmentPage.kt#SupportPageScreen" to SHEET_CONTENT,
                "history:feature/history/timeline/TimelineTab.kt#TimelineTab" to SHEET_CONTENT,
            )
    }
}
