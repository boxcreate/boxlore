package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.designsystem.echo.LocalEchoMotion
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.BriefingSource
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.briefing.BriefingScreen
import cx.aswin.boxlore.feature.briefing.BriefingUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Brief at every audit size (`-PtabletCaptures=audit`): the page at its cover and scrolled to the
 * stories, loading, stories still loading, the error page, an edition without sources, the edition
 * menu, and the preferences and sources sheets. The edition has six stories, four sources and, in
 * German captures, a German headline.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BriefingAuditCaptureTest(private val view: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        fixture.assumeAuditCapture()
        fixture.prepare()
        val german = fixture.configuration.locale == "de"
        val state = when (view) {
            "brief-loading" -> BriefingUiState.Loading("global")
            "brief-error" -> BriefingUiState.Error(ERROR_MESSAGE, "global")
            "brief-chapters-loading" -> edition(german).copy(chapters = emptyList(), chaptersLoading = true)
            "brief-no-sources" -> edition(german).copy(briefing = edition(german).briefing.copy(sources = emptyList()))
            else -> edition(german)
        }
        fixture.setContent(foldSafeViewport = true) {
            CompositionLocalProvider(LocalEchoMotion provides false) {
                BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                    BriefingScreen(state, {}, {}, { _, _ -> }, {}, {}, {}, defaultEdition = "global")
                }
            }
        }
        fixture.compose.mainClock.advanceTimeBy(1_000)
        fixture.compose.waitForIdle()
        when (view) {
            // The page's own scroller: the article, or on wide windows the story list beside the edition.
            "brief-page-scrolled" -> {
                val article = fixture.compose.onAllNodesWithTag("briefingArticle").fetchSemanticsNodes().isNotEmpty()
                fixture.compose.onNodeWithTag(if (article) "briefingArticle" else "briefing_story_list")
                    .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, fixture.configuration.width / 1.6f) }
            }
            "brief-edition-menu" -> tap(fixture.compose.onAllNodes(hasContentDescription(text(DesignR.string.briefing_choose_edition), substring = true)).onFirst())
            "brief-preferences" -> tap(fixture.compose.onNodeWithContentDescription(text(DesignR.string.briefing_preferences)))
            "brief-sources" -> tap(fixture.compose.onAllNodes(hasContentDescription(text(DesignR.string.briefing_sources))).onFirst())
        }
        if (view in ALL_WINDOW_VIEWS) {
            val roots = fixture.compose.onAllNodes(isRoot()).fetchSemanticsNodes().size
            check(roots > 1) { "$view opens its own window ($roots roots)" }
        }
        fixture.compose.onNodeWithTag("briefing_reading_frame").assertExists()
        fixture.capture(view, allWindows = view in ALL_WINDOW_VIEWS)
    }

    /** Clicks [node] through its semantics (popups open on the paused clock) and lets the result draw. */
    private fun tap(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.OnClick)
        repeat(5) {
            Snapshot.sendApplyNotifications()
            fixture.compose.mainClock.advanceTimeBy(100)
            fixture.compose.waitForIdle()
        }
    }

    private fun text(id: Int): String = fixture.compose.activity.resources.getString(id)

    private fun edition(german: Boolean): BriefingUiState.Success {
        val chapters = STORY_TITLES.mapIndexed { index, title ->
            Chapter(
                startTime = index * 75.0,
                title = title,
                relatedEpisodes = if (index == 0) listOf(RELATED_EPISODE, RELATED_EPISODE.copy(id = "related-2", title = "The week in energy")) else null,
            )
        }
        val script = STORY_TITLES.joinToString("\n\n") { title -> "$title. $STORY_BODY" }
        return BriefingUiState.Success(
            briefing = Briefing(
                date = "2026-10-30",
                region = "global",
                title = if (german) GERMAN_HEADLINE else HEADLINE,
                script = script,
                audioUrl = "https://example.invalid/brief.mp3",
                coverUrl = "",
                sources = (1..4).map { BriefingSource("Original reporting $it", "https://news$it.example.invalid/story") },
            ),
            selectedRegion = "global",
            currentPosition = 95_000,
            duration = 480_000,
            chapters = chapters,
        )
    }

    private companion object {
        const val HEADLINE = "Energy markets, digital policy and the week ahead"
        const val GERMAN_HEADLINE = "Energiemärkte, Digitalpolitik und Verbraucherschutzbestimmungen der kommenden Woche"
        const val ERROR_MESSAGE =
            "Couldn’t load this edition. Check your connection and try again. If it keeps happening, choose another edition from the menu below."
        const val STORY_BODY =
            "Reporters followed the story through the week, with officials, analysts and people affected describing what changed and what comes next."
        val STORY_TITLES = listOf(
            "Energy markets steady after a volatile week",
            "Digital policy: new rules for app stores",
            "Cyclone evacuations along the coast",
            "Airport IT failures delay thousands of flights",
            "Central banks hold rates and signal patience",
            "A record season for long-distance rail travel",
        )
        val RELATED_EPISODE = Episode(
            id = "related-1",
            title = "Inside the grid: how the winter of shortages changed the way a continent buys, stores and shares its power",
            description = "",
            audioUrl = "https://example.invalid/related.mp3",
            podcastTitle = "The Energy Hour with a Very Long Show Name for Testing Fit",
            podcastImageUrl = PhoneGoldenRule.ARTWORK_URL,
            imageUrl = PhoneGoldenRule.ARTWORK_URL,
        )
        val VIEWS = listOf(
            "brief-page", "brief-page-scrolled", "brief-loading", "brief-chapters-loading", "brief-error", "brief-no-sources",
            "brief-edition-menu", "brief-preferences", "brief-sources",
        )
        val ALL_WINDOW_VIEWS = setOf("brief-edition-menu", "brief-preferences", "brief-sources")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> {
            if (System.getProperty("boxlore.tabletCaptures") != "audit") return listOf(arrayOf("brief-page", PhoneGoldenConfiguration(412, 915, false)))
            val sizes = PhoneGoldenConfiguration.auditConfigurations(extra = listOf(PhoneGoldenConfiguration(360, 800, false)))
            return VIEWS.flatMap { view -> sizes.map { arrayOf<Any>(view, it) } }
        }
    }
}
