package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.prefs.AppLanguage

/**
 * Onboarding's first question when the phone suggests one of boxlore's translations: the suggested
 * language and English as quick choices, and the rest of the list under "Other languages" ([showsAll]).
 */
data class AppLanguageQuestion(
    val suggested: AppLanguage,
    val showsAll: Boolean = false,
) {
    /** The quick choices, suggested language first. */
    val quickChoices: List<AppLanguage> get() = listOf(suggested, AppLanguage.ENGLISH).distinct()

    /** The choices behind "Other languages": the phone's language, then the languages not offered above. */
    val otherChoices: List<AppLanguage> get() = AppLanguage.entries - quickChoices.toSet()
}

/** Where onboarding starts and how the language question moves on, as pure state changes. */
internal object OnboardingAppLanguage {
    /** Analytics step name for the question (`onboarding_step_viewed`, `onboarding_abandoned`). */
    const val STEP_NAME = "app_language"

    /** `settings_interaction` key for the pick; Settings reports later changes as `app_language_changed`. */
    const val ANALYTICS_KEY = "onboarding_app_language_chosen"

    /**
     * The first state: the language question when onboarding isn't finished and [suggestion] (asked
     * only then) names a translation; otherwise Welcome.
     */
    fun initialState(onboardingCompleted: Boolean, suggestion: () -> AppLanguage?): OnboardingUiState {
        if (onboardingCompleted) return OnboardingUiState()
        val suggested = suggestion() ?: return OnboardingUiState()
        return OnboardingUiState(currentStep = OnboardingStep.APP_LANGUAGE, appLanguageQuestion = AppLanguageQuestion(suggested))
    }

    /** After a pick onboarding continues on Welcome, and the question never comes back. */
    fun answered(state: OnboardingUiState): OnboardingUiState = state.copy(currentStep = OnboardingStep.WELCOME, appLanguageQuestion = null)

    fun showingAll(state: OnboardingUiState, showsAll: Boolean): OnboardingUiState =
        state.copy(appLanguageQuestion = state.appLanguageQuestion?.copy(showsAll = showsAll))

    /** The analytics value for a pick: its tag, or "system" for the phone's language. */
    fun analyticsValue(language: AppLanguage): String = language.tag.ifEmpty { "system" }
}
