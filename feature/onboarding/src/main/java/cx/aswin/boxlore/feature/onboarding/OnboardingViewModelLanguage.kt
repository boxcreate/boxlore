package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.prefs.AppLanguage
import kotlinx.coroutines.flow.update

/**
 * Records the listener's language and moves on to Welcome, with how the question's panel stood
 * ([handoff]) so Welcome's panel takes over from it. The app applies the language itself
 * (`OnboardingScreen`'s `onAppLanguageChosen`), which may recreate the activity; this view model and
 * its state outlive that.
 */
fun OnboardingViewModel.chooseAppLanguage(language: AppLanguage, handoff: OpeningHandoff? = null) {
    if (_uiState.value.currentStep != OnboardingStep.APP_LANGUAGE) return
    // The step's view is `onboarding_step_viewed` (app_language); the pick goes with Settings' language
    // changes under its own key, since the manual step event would also count a manual-topics step.
    AnalyticsHelper.trackSettingsInteraction(OnboardingAppLanguage.ANALYTICS_KEY, OnboardingAppLanguage.analyticsValue(language))
    _uiState.update { OnboardingAppLanguage.answered(it, handoff) }
}

/** Welcome has taken over from the language question's panel. */
fun OnboardingViewModel.finishOpeningHandoff() {
    _uiState.update(OnboardingAppLanguage::handoffDone)
}
