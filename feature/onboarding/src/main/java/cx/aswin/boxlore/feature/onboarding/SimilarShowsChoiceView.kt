package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/**
 * Search and import, before any request: the shows already in the library, then the question.
 * The footer holds both answers (See similar shows, Go to Home); the content scrolls in short windows.
 */
@Composable
internal fun SimilarShowsChoiceContent(uiState: OnboardingUiState) {
    val origin = OnboardingSuggestionsPresentation.origin(uiState)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).testTag("similar_choice"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SuggestionsSeedSummary(uiState, origin)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.similar_choice_title),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
            )
            Text(
                stringResource(R.string.similar_choice_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
