package cx.aswin.boxlore.feature.onboarding

// i18n-ignore-file: not routed by OnboardingScreen (the legacy chat and import screens), so none of its text reaches listeners.

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun ImportOnboardingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text("Import library flow placeholder - to be refined later.")
    }
}
