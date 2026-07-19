package cx.aswin.boxlore.feature.onboarding

import androidx.compose.runtime.Composable

@Composable
internal fun AiOnboardingScreen(
    uiState: OnboardingUiState,
    onBack: () -> Unit,
    onOptionToggle: (String) -> Unit,
    onCustomInputChange: (String) -> Unit,
    onContinue: () -> Unit,
    onRevealSuggestions: () -> Unit,
    onRetryCuration: () -> Unit,
    onSwitchToManual: () -> Unit,
    onBuildFeedNow: () -> Unit,
    onSearchInstead: (String?) -> Unit,
) {
    AiChatOnboardingScreen(
        uiState = uiState,
        onBack = onBack,
        onOptionToggle = onOptionToggle,
        onCustomInputChange = onCustomInputChange,
        onContinue = onContinue,
        onRevealSuggestions = onRevealSuggestions,
        onRetryCuration = onRetryCuration,
        onSwitchToManual = onSwitchToManual,
        onBuildFeedNow = onBuildFeedNow,
        onSearchInstead = onSearchInstead,
    )
}
