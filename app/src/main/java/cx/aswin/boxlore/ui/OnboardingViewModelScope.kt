package cx.aswin.boxlore.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxlore.feature.onboarding.OnboardingViewModel

/** The key the activity's onboarding view model is stored under. */
internal const val ONBOARDING_VIEW_MODEL_KEY = "boxlore-onboarding"

/**
 * The activity's one [OnboardingViewModel], kept in [owner]'s view model store rather than in
 * composition.
 *
 * Every onboarding path shares this instance: search, Echo (AI), genre picks, the import sheet,
 * Home's AI onboarding entry and the sign-in detour through Settings. Shows picked in one flow
 * therefore carry over to the others, and the instance survives the activity being recreated.
 *
 * Because the store owns it, `onCleared` runs when the activity finishes for good: Echo requests
 * are cancelled, deck audio is released, and an onboarding left unfinished is reported as
 * abandoned. Created with `remember`, it was never cleared, so none of that happened.
 *
 * [build] runs once per store; later calls return the stored instance.
 */
@Composable
internal fun rememberOnboardingViewModel(
    owner: ViewModelStoreOwner,
    build: () -> OnboardingViewModel,
): OnboardingViewModel = viewModel(
    viewModelStoreOwner = owner,
    key = ONBOARDING_VIEW_MODEL_KEY,
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    },
)
