package cx.aswin.boxlore.feature.onboarding

import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.prefs.AppLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingAppLanguageTest {
    @Test
    fun `a suggested translation opens onboarding with the language question`() {
        val state = OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.GERMAN }
        assertEquals(OnboardingStep.APP_LANGUAGE, state.currentStep)
        assertEquals(AppLanguageQuestion(AppLanguage.GERMAN), state.appLanguageQuestion)
    }

    @Test
    fun `no suggestion or a finished onboarding starts on Welcome, without asking the phone once finished`() {
        assertEquals(OnboardingUiState(), OnboardingAppLanguage.initialState(onboardingCompleted = false) { null })
        var asked = false
        val finished = OnboardingAppLanguage.initialState(onboardingCompleted = true) {
            asked = true
            AppLanguage.SPANISH
        }
        assertEquals(OnboardingStep.WELCOME, finished.currentStep)
        assertNull(finished.appLanguageQuestion)
        assertFalse(asked)
    }

    @Test
    fun `the suggestion leads, then English and the other translations, never the phone's setting`() {
        val question = AppLanguageQuestion(AppLanguage.PORTUGUESE_BRAZIL)
        assertEquals(listOf(AppLanguage.ENGLISH, AppLanguage.SPANISH, AppLanguage.GERMAN, AppLanguage.FRENCH), question.others)
        assertEquals(AppLanguage.PORTUGUESE_BRAZIL, question.all.first())
        assertEquals(5, question.all.distinct().size)
        assertFalse(AppLanguage.SYSTEM in question.all)
    }

    @Test
    fun `an answer moves on to Welcome for good`() {
        val open = OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.FRENCH }
        val answered = OnboardingAppLanguage.answered(open)
        assertEquals(OnboardingStep.WELCOME, answered.currentStep)
        assertNull(answered.appLanguageQuestion)
    }

    @Test
    fun `analytics values are tags, with system for the phone's language`() {
        assertEquals("es", OnboardingAppLanguage.analyticsValue(AppLanguage.SPANISH))
        assertEquals("pt-BR", OnboardingAppLanguage.analyticsValue(AppLanguage.PORTUGUESE_BRAZIL))
        assertEquals("system", OnboardingAppLanguage.analyticsValue(AppLanguage.SYSTEM))
    }

    @Test
    fun `the language question reports its own step index, leaving Welcome's 0 to Welcome`() {
        assertEquals(Triple(OnboardingAppLanguage.STEP_NAME, -1, "welcome"), onboardingStepAnalytics(OnboardingStep.APP_LANGUAGE))
        assertEquals(Triple("welcome", 0, "welcome"), onboardingStepAnalytics(OnboardingStep.WELCOME))
    }

    @Test
    fun `the other languages join into one block with large outer and small inner corners`() {
        val outer = LanguageTileCorners.OUTER
        val inner = LanguageTileCorners.INNER
        // Four tiles, two rows.
        assertEquals(LanguageTileCorners(outer, inner, inner, inner), LanguageTileCorners.of(0, 4))
        assertEquals(LanguageTileCorners(inner, outer, inner, inner), LanguageTileCorners.of(1, 4))
        assertEquals(LanguageTileCorners(inner, inner, inner, outer), LanguageTileCorners.of(2, 4))
        assertEquals(LanguageTileCorners(inner, inner, outer, inner), LanguageTileCorners.of(3, 4))
        // Three tiles: the lone last tile spans the row, closing the block below both tiles above it.
        assertEquals(LanguageTileCorners(inner, outer, inner, inner), LanguageTileCorners.of(1, 3))
        assertEquals(LanguageTileCorners(inner, inner, outer, outer), LanguageTileCorners.of(2, 3))
        // One row only: each tile is rounded on its outside.
        assertEquals(LanguageTileCorners(outer, inner, inner, outer), LanguageTileCorners.of(0, 2))
        assertEquals(LanguageTileCorners(inner, outer, outer, inner), LanguageTileCorners.of(1, 2))
        assertEquals(LanguageTileCorners(outer, outer, outer, outer), LanguageTileCorners.of(0, 1))
    }

    @Test
    fun `the greeting starts on the suggested language, cycles through every word and rests with animations off`() {
        val count = 5
        val word = LanguageGreetingMotion.WordMillis
        val start = LanguageGreetingMotion.StartMillis
        assertEquals(0f, LanguageGreetingMotion.frame(0, count).alpha)
        assertEquals(LanguageGreetingMotion.Frame(0, 1f, 0f), LanguageGreetingMotion.frame(0, count, animationsDisabled = true))
        // Settled mid-word, then the next word, wrapping after the last.
        val settled = LanguageGreetingMotion.frame(start + word / 2, count)
        assertEquals(0, settled.index)
        assertEquals(1f, settled.alpha, 0.001f)
        assertEquals(0f, settled.translationFraction, 0.001f)
        assertEquals(1, LanguageGreetingMotion.frame(start + word + word / 2, count).index)
        assertEquals(0, LanguageGreetingMotion.frame(start + count * word + word / 2, count).index)
        // A word fades and lifts away just before the next one arrives.
        val leaving = LanguageGreetingMotion.frame(start + word - 1, count)
        assertTrue(leaving.alpha < 0.05f && leaving.translationFraction < 0f)
        // Entering words rise from below.
        assertTrue(LanguageGreetingMotion.frame(start + word + 60, count).translationFraction > 0f)
        assertEquals(LanguageGreetingMotion.Frame(0, 0f, 0f), LanguageGreetingMotion.frame(5_000, 0))
    }

    @Test
    fun `wide windows put the greeting beside the question, tall ones stack them`() {
        assertTrue(languageQuestionTwoPane(800.dp, 360.dp))
        assertTrue(languageQuestionTwoPane(1280.dp, 800.dp))
        assertTrue(languageQuestionTwoPane(840.dp, 600.dp))
        assertFalse(languageQuestionTwoPane(599.dp, 360.dp))
        assertFalse(languageQuestionTwoPane(673.dp, 841.dp))
        assertFalse(languageQuestionTwoPane(412.dp, 915.dp))
        assertFalse(languageQuestionTwoPane(800.dp, 800.dp))
    }

    @Test
    fun `the greeting is big but shrinks on short windows`() {
        assertEquals(68.4f, languageGreetingSize(360.dp, 800.dp), 0.01f)
        assertEquals(40f, languageGreetingSize(360.dp, 300.dp), 0.01f)
        assertEquals(92f, languageGreetingSize(1000.dp, 2000.dp), 0.01f)
    }
}
