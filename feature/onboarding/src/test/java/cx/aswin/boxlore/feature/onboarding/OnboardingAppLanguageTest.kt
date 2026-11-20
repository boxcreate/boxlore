package cx.aswin.boxlore.feature.onboarding

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
    fun `quick choices lead with the suggestion and English, other languages hold the rest`() {
        val question = AppLanguageQuestion(AppLanguage.PORTUGUESE_BRAZIL)
        assertEquals(listOf(AppLanguage.PORTUGUESE_BRAZIL, AppLanguage.ENGLISH), question.quickChoices)
        assertEquals(
            listOf(AppLanguage.SYSTEM, AppLanguage.SPANISH, AppLanguage.GERMAN, AppLanguage.FRENCH),
            question.otherChoices,
        )
        assertEquals(listOf(AppLanguage.ENGLISH), AppLanguageQuestion(AppLanguage.ENGLISH).quickChoices)
    }

    @Test
    fun `other languages open and close, and an answer moves on to Welcome for good`() {
        val asked = OnboardingAppLanguage.initialState(onboardingCompleted = false) { AppLanguage.FRENCH }
        val open = OnboardingAppLanguage.showingAll(asked, true)
        assertTrue(open.appLanguageQuestion!!.showsAll)
        assertFalse(OnboardingAppLanguage.showingAll(open, false).appLanguageQuestion!!.showsAll)

        val answered = OnboardingAppLanguage.answered(open)
        assertEquals(OnboardingStep.WELCOME, answered.currentStep)
        assertNull(answered.appLanguageQuestion)
        // Opening the list after answering changes nothing.
        assertEquals(answered, OnboardingAppLanguage.showingAll(answered, true))
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
}
