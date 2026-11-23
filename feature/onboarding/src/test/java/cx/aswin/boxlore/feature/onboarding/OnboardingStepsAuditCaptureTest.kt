package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.SystemInsets
import cx.aswin.boxlore.core.testing.applyFakeSystemInsets
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review captures of the onboarding steps after Welcome (run with `-PtabletCaptures=audit`; skipped
 * otherwise): Echo writing with the keyboard up, a show card with a long name, Echo's error, the
 * About Echo sheet, onboarding search (no picks, eight picks, typed results), Shows for you and a
 * show's details sheet, the similar-shows choice, and the genre and listening-moment pickers.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingStepsAuditCaptureTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)

    private val callbacks = EchoCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val choices = listOf("Unsolved mysteries", "Big ideas in science", "Stories from history", "Comedians riffing")
    private val later = OnboardingUiState(
        currentStep = OnboardingStep.AI_ONBOARDING,
        aiCurrentTurn = 2,
        aiHistory = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("True crime")))),
        aiOptions = choices,
        aiGuidedTurn = GuidedOnboardingTurn(1, "question", "Cold cases. Respect.", "What would you happily hear about on a long drive?", choices, intent = "preferences"),
    )

    private object SilentPreview : EchoShowPreview {
        override val status: State<EchoPreviewStatus> = mutableStateOf(EchoPreviewStatus())
        override fun load(clip: EchoClip?) = Unit
        override fun toggle() = Unit
        override fun pause() = Unit
        override fun tick() = Unit
        override fun release() = Unit
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(id: Int, title: String = "Show $id") =
        Podcast(id = "$id", title = title, artist = "Host $id", imageUrl = "", genre = if (id % 2 == 0) "True Crime" else "Society & Culture")

    private val shows = listOf(
        "The Daily", "Serial", "Radiolab", "Hidden Brain", "Darknet Diaries", "The Rest Is History", "Science Vs", "Criminal",
        "99% Invisible", "Freakonomics Radio", "Revisionist History", "Stuff You Should Know",
    ).mapIndexed { index, title -> show(index + 1, title) }

    /** Something else open with a draft, and a 300dp keyboard up (no bars posture: the rule would drop the keyboard). */
    @Test fun echoWritingIme() {
        assumeTrue("The keyboard is recorded on a phone and a sideways phone", golden.configuration.auditKey in setOf("412x915", "915x412"))
        val keyboard = if (golden.configuration.width < golden.configuration.height) SystemInsets.PHONE_BARS else SystemInsets.SIDEWAYS_3_BUTTON
        render("echo-writing-ime", insets = keyboard.copy(imeBottom = ImeHeight)) {
            EchoOnboardingScreen(later.copy(aiCustomInputText = "Long drives and old mysteries"), callbacks)
        }
    }

    /** A show card with a very long name and pitch. */
    @Test fun echoDeck() = render("echo-deck") {
        EchoOnboardingScreen(
            later.copy(
                aiSelectedOptions = setOf(choices.first()),
                aiDeck = EchoDeck(
                    EchoDeckStatus.Ready,
                    listOf(
                        OnboardingProbeCard(
                            9,
                            "The Dry Bar Comedy Podcast: Stand-Up Stories, Backstage Chats and Much More",
                            "Dry Bar Comedy",
                            role = "related",
                            hook = "Stand-up comedians swap stories from the road and the stage, with plenty of tangents along the way.",
                            question = "Clean stand-up, maybe?",
                        ),
                        OnboardingProbeCard(7, "Criminal", "Radiotopia", hook = "Short stories of people who did wrong.", question = "Shorter cases, maybe?"),
                    ),
                    showing = true,
                ),
            ),
            callbacks,
        )
    }

    @Test fun echoError() = render("echo-error") {
        EchoOnboardingScreen(later.copy(onboardingError = "offline"), callbacks)
    }

    @Test fun echoAboutSheet() = render("echo-about-sheet", allWindows = true, open = {
        golden.compose.onNodeWithTag("echo_more").performSemanticsAction(SemanticsActions.OnClick)
        golden.compose.mainClock.advanceTimeBy(600)
        golden.compose.waitForIdle()
        val about = golden.compose.onAllNodesWithText(context.getString(R.string.echo_about))
        // Robolectric doesn't open the menu's popup on some wide, short windows; those sizes are skipped.
        assumeTrue("The More menu opened", about.fetchSemanticsNodes().isNotEmpty())
        about.onFirst().performSemanticsAction(SemanticsActions.OnClick)
    }) {
        EchoOnboardingScreen(OnboardingUiState(currentStep = OnboardingStep.AI_ONBOARDING), callbacks)
    }

    @Test fun searchGrid() = render("search-grid") { Search(picks = emptyList()) }

    @Test fun searchPicks() = render("search-picks") { Search(picks = shows.take(8)) }

    @Test fun searchResults() = render("search-results") { Search(picks = shows.take(1), query = "true crime") }

    @Test fun suggestions() = render("suggestions") { Suggestions(suggestionsState()) }

    @Test fun suggestionDetailsSheet() = render("suggestion-details-sheet", allWindows = true, open = {
        golden.compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState) and hasText("Serial", substring = true))
            .performSemanticsAction(SemanticsActions.OnLongClick)
    }) { Suggestions(suggestionsState()) }

    @Test fun similarChoice() = render("similar-choice") {
        Suggestions(
            OnboardingUiState(
                currentStep = OnboardingStep.AI_SUGGESTIONS,
                reachedSuggestionsViaSearchFlow = true,
                similarChoicePending = true,
                suggestionSeedCount = 3,
                selectedPodcasts = shows.take(3).associateBy { it.id },
                subscribedPodcastIds = shows.take(3).map { it.id }.toSet(),
            ),
        )
    }

    @Test fun topicsGenres() = render("topics-genres") {
        GenrePickerScreen(selectedGenres = setOf("True Crime", "Science"), onToggleGenre = {}, onContinue = {}, onBack = {})
    }

    @Test fun topicsActivities() = render("topics-activities") {
        ActivityPickerScreen(
            selectedActivities = setOf("commuting", "working"),
            activityGenreMap = mapOf("commuting" to setOf("True Crime"), "working" to setOf("Science")),
            allSelectedGenres = setOf("True Crime", "Science", "History"),
            onToggleActivity = {},
            onSetGenresForActivity = { _, _ -> },
            onBack = {},
            onContinue = {},
        )
    }

    @Composable
    private fun Search(picks: List<Podcast>, query: String = "") {
        OnboardingSearchScreen(
            query = query,
            results = if (query.isEmpty()) emptyList() else shows.drop(4),
            isSearching = false,
            subscribedIds = picks.map { it.id }.toSet(),
            selectedPodcasts = picks.associateBy { it.id },
            onQueryChange = {},
            onSubscribe = {},
            onBack = {},
            onDone = {},
            popularPodcasts = shows,
            isPopularLoading = false,
            selectedSearchGenre = null,
            onGenreSelect = {},
        )
    }

    private fun suggestionsState() = OnboardingUiState(
        currentStep = OnboardingStep.AI_SUGGESTIONS,
        reachedSuggestionsViaAiFlow = true,
        selectedGenres = setOf("True Crime", "Science"),
        aiPicks = shows.take(2),
        selectedPodcasts = shows.take(2).associateBy { it.id },
        subscribedPodcastIds = shows.take(2).map { it.id }.toSet(),
        aiCurriculumRows = listOf(
            OnboardingCurriculumRowDto(
                "Cold cases worth the drive",
                shows.drop(2).take(6).map { OnboardingCurriculumPodcastDto(id = it.id.toLong(), title = it.title, author = it.artist) },
            ),
        ),
        genreChartsPodcasts = shows.drop(4),
    )

    @Composable
    private fun Suggestions(state: OnboardingUiState) {
        AiSuggestionsScreen(
            uiState = state,
            callbacks = SuggestionsCallbacks(onBack = {}, onToggle = {}, onSetShowsSelected = { _, _ -> }, onRegionChange = {}, onRetry = {}, onFinish = {}),
        )
    }

    private fun render(
        name: String,
        allWindows: Boolean = false,
        insets: SystemInsets? = null,
        open: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        golden.assumeAuditCapture()
        assumeTrue("Steps after Welcome are recorded in light", !golden.configuration.dark)
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                CompositionLocalProvider(LocalEchoShowPreviewFactory provides { _ -> SilentPreview }) {
                    Box(Modifier.fillMaxSize().testTag(ROOT_TAG)) { content() }
                }
            }
        }
        if (insets != null) {
            golden.compose.runOnUiThread {
                WindowCompat.setDecorFitsSystemWindows(golden.compose.activity.window, false)
                applyFakeSystemInsets(golden.compose.activity, insets)
            }
        }
        golden.compose.mainClock.advanceTimeBy(1_600)
        golden.compose.waitForIdle()
        open?.let {
            it()
            golden.compose.mainClock.advanceTimeBy(1_000)
            golden.compose.waitForIdle()
        }
        golden.compose.onNodeWithTag(ROOT_TAG).assertExists()
        golden.capture(name, allWindows = allWindows)
    }

    companion object {
        private const val ROOT_TAG = "onboarding_steps_audit_root"
        private const val ImeHeight = 300

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.auditMatrix(
            PhoneGoldenConfiguration.PHONE_BARS_CONFIGURATIONS + listOf(
                PhoneGoldenConfiguration(740, 360, false),
                PhoneGoldenConfiguration(475, 330, false),
                PhoneGoldenConfiguration(932, 314, false),
                PhoneGoldenConfiguration(841, 340, false),
                PhoneGoldenConfiguration(673, 410, false),
            ),
        )
    }
}
