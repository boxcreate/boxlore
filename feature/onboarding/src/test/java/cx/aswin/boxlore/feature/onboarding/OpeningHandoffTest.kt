package cx.aswin.boxlore.feature.onboarding

import android.app.Application
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.AppLanguage
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import java.util.Locale
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * From the language question to Welcome: nothing slides, the panel, the logo and the greeting on show
 * stay where they were, and Welcome waits for the picked language before it takes over. Then its panel
 * settles from the question's height to its own, the greeting gives way to the cover reel, and the
 * ways in rise in instead of appearing at once. Beside the panel (tablets) the panel keeps its width
 * and only its content changes. With animations off Welcome shows as usual at once. The hand-off
 * outlives the recreation a language change causes, Welcome takes it over once, leaving Welcome ends
 * it, and the pick moves on to Welcome as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1400dp-h1400dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OpeningHandoffTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var window by mutableStateOf(BoxloreTestWindows.PHONE)

    /** Stands in for the view model's state, which outlives the activity. */
    private var state by mutableStateOf(OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.GERMAN })
    private val picks = mutableListOf<AppLanguage>()
    private var handoffsDone = 0

    /** The language the screen shows: the pick once the activity has been recreated for it. */
    private var shown by mutableStateOf<AppLanguage?>(null)
    private var appliesOnPick = true
    private var surface = Color.Unspecified

    @After
    fun animationsOn() = setAnimationsOff(false)

    @Composable
    private fun Host() {
        val base = LocalConfiguration.current
        val configuration = shown?.let { language -> Configuration(base).apply { setLocale(Locale.forLanguageTag(language.tag)) } } ?: base
        BoxLoreTheme(dynamicColor = false) {
            surface = MaterialTheme.colorScheme.surface
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window, LocalConfiguration provides configuration) {
                Box(Modifier.requiredSize(window.widthDp.dp, window.heightDp.dp)) {
                    OnboardingStepTransition(state.currentStep) { step ->
                        when (step) {
                            OnboardingStep.APP_LANGUAGE -> state.appLanguageQuestion?.let { question ->
                                AppLanguageQuestionContent(question, onChoose = { language, handoff ->
                                    picks += language
                                    state = OnboardingAppLanguage.answered(state, handoff)
                                    if (appliesOnPick) shown = language
                                })
                            }
                            OnboardingStep.WELCOME -> WelcomeScreen(
                                onHelpMeFind = { state = state.copy(currentStep = OnboardingStep.SEARCH) },
                                onSearch = {},
                                onSkip = {},
                                onImportClick = {},
                                handoff = WelcomeHandoff(state.openingHandoff) {
                                    handoffsDone++
                                    state = OnboardingAppLanguage.handoffDone(state)
                                },
                            )
                            else -> Box(Modifier.requiredSize(window.widthDp.dp, window.heightDp.dp))
                        }
                    }
                }
            }
        }
    }

    private fun show(settle: Long = 1_600) {
        compose.mainClock.autoAdvance = false
        compose.setContent { Host() }
        compose.mainClock.advanceTimeBy(settle)
        compose.waitForIdle()
    }

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun advance(millis: Long) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun single(tag: String): Rect = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().single().boundsInRoot

    private fun count(tag: String): Int = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun pick(name: String) = compose.onNode(hasText(name) and hasClickAction()).performClick()

    /**
     * Scrolls the question until [name] is whole on screen, as a listener's swipe would: the page's
     * scroll runs on the paused clock, so it is started directly and then given time.
     */
    private fun scrollToShow(name: String) {
        val bottom = single(OpeningLogoTag).top - logoInset + window.heightDp
        val needed = compose.onNode(hasText(name) and hasClickAction()).fetchSemanticsNode().boundsInRoot.bottom - bottom + 8f
        if (needed <= 0f) return
        val scrollBy = compose.onNode(hasScrollAction()).fetchSemanticsNode().config[SemanticsActions.ScrollBy].action
        compose.runOnUiThread { scrollBy?.invoke(0f, needed) }
        advance(600)
    }

    /** The logo row's top below the panel's top edge, with no status bar. */
    private val logoInset = 16f

    private fun findShows(): Rect = compose.onNode(hasText("Find shows for me") and hasClickAction()).fetchSemanticsNode().boundsInRoot

    /** The colour drawn at the middle of [area]. */
    private fun colourAt(area: Rect): Color = compose.onRoot().captureToImage().toPixelMap()[area.center.x.toInt(), area.center.y.toInt()]

    private fun assertSameColour(message: String, expected: Color, actual: Color) {
        val close = abs(expected.red - actual.red) < 0.02f && abs(expected.green - actual.green) < 0.02f && abs(expected.blue - actual.blue) < 0.02f
        assertTrue("$message ($expected vs $actual)", close)
    }

    @Test
    fun `the pick moves on to Welcome with the panel's place, once`() {
        val open = OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.SPANISH }
        val handoff = OpeningHandoff(0.42f, twoPane = false, AppLanguage.SPANISH)

        val answered = OnboardingAppLanguage.answered(open, handoff)

        assertEquals(OnboardingStep.WELCOME, answered.currentStep)
        assertEquals(handoff, answered.openingHandoff)
        assertNull(answered.appLanguageQuestion)
        assertNull(OnboardingAppLanguage.handoffDone(answered).openingHandoff)
        assertNull(OnboardingAppLanguage.answered(open).openingHandoff)
    }

    @Test
    fun `turning across the two-pane gate during the hand-off ends it once and shows Welcome`() {
        window = BoxloreTestWindows.PHONE
        show()
        pick("Deutsch")
        frame()
        advance(100)
        // The question's arrangement no longer matches Welcome's: Welcome drops the hand-off and shows as usual.
        window = BoxloreTestWindows.PHONE_LAND
        advance(1_600)
        assertEquals("hand-offs ended", 1, handoffsDone)
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals("one panel", 1, count(OpeningPanelTag))
        compose.onNode(hasText("Find shows for me") and hasClickAction()).assertExists()
    }

    @Test
    fun `a resize while the panel settles ends the hand-off at the panel's new height`() {
        window = BoxloreTestWindows.PHONE
        show()
        pick("Deutsch")
        frame()
        advance(200)
        window = BoxloreTestWindows.PHONE_SMALL
        repeat(400) { if (handoffsDone == 0) frame() }
        assertEquals("hand-offs ended", 1, handoffsDone)
        val ended = single(OpeningPanelTag)
        advance(500)
        assertEquals("the panel jumps once the hand-off ends", ended.height, single(OpeningPanelTag).height, 1f)
    }

    @Test
    fun `on a phone the panel and logo stay put, then the panel settles at Welcome's height`() = handsOver(BoxloreTestWindows.PHONE)

    @Test
    fun `on the Fold 8 cover the panel and logo stay put, then the panel settles`() = handsOver(BoxloreTestWindows.COVER_475)

    @Test
    fun `beside the panel only its content changes`() {
        window = BoxloreTestWindows.TABLET
        show()
        val panel = single(OpeningPanelTag)
        val logo = single(OpeningLogoTag)

        pick("Deutsch")
        frame()

        assertEquals(listOf(AppLanguage.GERMAN), picks)
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        assertEquals(panel, single(OpeningPanelTag))
        assertEquals(logo, single(OpeningLogoTag))
        assertEquals("the greeting stays for the hand-off", 1, count("handoff_greeting"))
        assertWaysInRiseIn()
        assertEquals(panel, single(OpeningPanelTag))
        assertEquals(1, handoffsDone)
        assertEquals(0, count("handoff_greeting"))
    }

    @Test
    fun `on a phone the greeting on show at the tap stays, in the same place`() = keepsGreeting(BoxloreTestWindows.PHONE)

    @Test
    fun `on a short split window the greeting on show at the tap stays, in the same place`() = keepsGreeting(BoxloreTestWindows.SPLIT_HALF)

    @Test
    fun `beside the panel the greeting on show at the tap stays, in the same place`() = keepsGreeting(BoxloreTestWindows.TABLET)

    /** A pick while the second greeting ("Hello") is on show, not the picked language's own. */
    private fun keepsGreeting(size: BoxloreWindowLayout) {
        window = size
        state = OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.GERMAN }
        show(settle = 3_000)
        scrollToShow("English")
        advance(600)
        val greeting = single("language_greeting")

        pick("English")
        frame()

        assertEquals("the greeting on show at the tap", AppLanguage.ENGLISH, state.openingHandoff?.greeting?.language)
        val held = single("handoff_greeting")
        assertEquals("the greeting stays put", greeting.top, held.top, 0.5f)
        assertEquals("the greeting keeps its size", greeting.height, held.height, 0.5f)
        assertEquals(greeting.left, held.left, 0.5f)
    }

    @Test
    fun `a scrolled question hands over its panel where it was seen`() {
        window = BoxloreTestWindows.SPLIT_HALF
        show()
        val top = single(OpeningLogoTag).top
        scrollToShow("Français")
        val logo = single(OpeningLogoTag)
        assertTrue("the question has scrolled", logo.top < top - 1f)

        pick("Français")
        frame()

        assertEquals("the logo stays put", logo.top, single(OpeningLogoTag).top, 1f)
        advance(1_500)
        assertEquals(1, handoffsDone)
        val settled = single(OpeningLogoTag)
        assertEquals("Welcome settles at its top", top, settled.top, 0.5f)
    }

    @Test
    fun `Welcome holds the question's last frame until the picked language is in effect`() {
        appliesOnPick = false
        show()
        val panel = single(OpeningPanelTag)

        pick("Deutsch")
        frame()
        advance(1_500)

        assertEquals("the hand-off waits for the language", 0, handoffsDone)
        assertEquals("the panel holds the question's height", panel.height, single(OpeningPanelTag).height, 1f)
        assertSameColour("nothing of Welcome's below the panel shows yet", surface, colourAt(findShows()))

        shown = AppLanguage.GERMAN
        advance(1_500)

        assertEquals(1, handoffsDone)
        assertNull(state.openingHandoff)
    }

    @Test
    fun `leaving Welcome mid-hand-off ends it, so coming back shows Welcome as usual`() {
        show()
        pick("Deutsch")
        frame()
        advance(200)

        compose.onNode(hasText("Find shows for me") and hasClickAction()).performClick()
        frame()

        assertEquals(OnboardingStep.SEARCH, state.currentStep)
        assertNull(state.openingHandoff)
        assertEquals(1, handoffsDone)
        state = state.copy(currentStep = OnboardingStep.WELCOME)
        advance(1_500)
        assertEquals(0, count("handoff_greeting"))
        assertEquals(1, handoffsDone)
    }

    @Test
    fun `with animations off Welcome shows as usual at once`() {
        setAnimationsOff(true)
        show()
        pick("Deutsch")
        frame()

        assertEquals(0, count("handoff_greeting"))
        val first = single(OpeningPanelTag)
        advance(1_500)
        assertEquals("nothing moves", first, single(OpeningPanelTag))
        assertEquals(1, handoffsDone)
    }

    @Test
    fun `the hand-off outlives recreation and Welcome takes it over from the question's height`() {
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Host() }
        advance(1_600)
        val question = single(OpeningPanelTag)
        pick("Deutsch")
        val handoff = state.openingHandoff
        assertNotNull(handoff)

        restoration.emulateSavedInstanceStateRestore()
        frame()

        assertEquals(handoff, state.openingHandoff)
        assertEquals("Welcome starts at the question's height", question.height, single(OpeningPanelTag).height, 1f)
        advance(1_500)
        assertEquals(1, handoffsDone)
        assertNull(state.openingHandoff)
    }

    private fun handsOver(size: BoxloreWindowLayout) {
        window = size
        show()
        val panel = single(OpeningPanelTag)
        val logo = single(OpeningLogoTag)

        pick("Deutsch")
        frame()

        assertEquals(listOf(AppLanguage.GERMAN), picks)
        assertEquals(OnboardingStep.WELCOME, state.currentStep)
        val first = single(OpeningPanelTag)
        assertEquals("Welcome's panel starts at the question's height", panel.height, first.height, 1f)
        assertEquals("nothing slides", panel.left, first.left, 0.5f)
        assertEquals("the logo stays put", logo, single(OpeningLogoTag))
        // Welcome's ways in rise in: a moment after the pick, once the choices have faded, only the
        // surface shows where Find shows for me will be.
        val find = findShows()
        val start = compose.mainClock.currentTime
        var checked = false
        // The panel on the morph's last frame is the panel Welcome keeps: no jump when the hand-off ends.
        var lastMorph = first
        while (handoffsDone == 0) {
            lastMorph = single(OpeningPanelTag)
            if (!checked && compose.mainClock.currentTime - start >= 130) {
                assertSameColour("Find shows for me is not drawn yet", surface, colourAt(find))
                checked = true
            }
            frame()
            assertTrue("the hand-off ends", compose.mainClock.currentTime - start < 10_000)
        }
        assertTrue(checked)
        assertNotEquals("Find shows for me is drawn once the hand-off is done", surface, colourAt(findShows()))
        assertNull(state.openingHandoff)
        val settled = single(OpeningPanelTag)
        assertEquals("no jump at the end of the morph", settled.height, lastMorph.height, 0.5f)
        frame()
        assertEquals(settled, single(OpeningPanelTag))
        assertTrue("the panel sits at the top", settled.top <= panel.top + 0.5f)
    }

    /**
     * The question's choices fade on top while Welcome's ways in wait, then rise in: a moment after the
     * pick only the surface shows where Find shows for me will be, and it is drawn once the hand-off is done.
     */
    private fun assertWaysInRiseIn() {
        val find = findShows()
        advance(130)
        assertSameColour("Find shows for me is not drawn yet", surface, colourAt(find))
        advance(1_500)
        assertEquals(1, handoffsDone)
        assertNotEquals("Find shows for me is drawn once the hand-off is done", surface, colourAt(findShows()))
    }

    private fun setAnimationsOff(off: Boolean) {
        val resolver = ApplicationProvider.getApplicationContext<Application>().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (off) 0f else 1f)
    }
}
