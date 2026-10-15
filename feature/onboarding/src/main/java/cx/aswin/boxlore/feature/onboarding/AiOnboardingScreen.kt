package cx.aswin.boxlore.feature.onboarding

import androidx.compose.runtime.Composable

@Composable
internal fun AiOnboardingScreen(uiState: OnboardingUiState, callbacks: EchoCallbacks, deckClips: EchoShowPreview? = null) {
    EchoOnboardingScreen(uiState = uiState, callbacks = callbacks, deckClips = deckClips)
}
