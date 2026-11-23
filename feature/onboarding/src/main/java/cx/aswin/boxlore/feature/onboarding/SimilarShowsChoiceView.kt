package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreScrollableCenteredColumn
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.artworkUrls

/**
 * Search and import, before any request: the question, just above the middle, with the picked
 * shows' covers in a small fan under it and how many are already in the library. The footer holds
 * both answers (See similar shows, Go to Home); the content scrolls only in short windows.
 */
@Composable
internal fun SimilarShowsChoiceContent(uiState: OnboardingUiState) {
    val origin = OnboardingSuggestionsPresentation.origin(uiState)
    val seeds = OnboardingSuggestionsPresentation.seedShows(uiState)
    val count = uiState.suggestionSeedCount.takeIf { it > 0 } ?: seeds.size
    BoxloreScrollableCenteredColumn(
        Modifier.fillMaxSize().testTag("similar_choice"),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.widthIn(max = SimilarChoiceMaxWidth).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.similar_choice_title),
                Modifier.semantics { heading() }.testTag("similar_choice_title"),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.similar_choice_body),
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (seeds.isNotEmpty()) SimilarChoiceCovers(seeds.take(MaxCovers).map { it.imageUrl to it.artworkUrls() })
            Text(
                if (origin == SuggestionsOrigin.Library) {
                    pluralStringResource(R.plurals.suggestions_library_seeds, count, count)
                } else {
                    pluralStringResource(R.plurals.suggestions_search_seeds, count, count)
                },
                Modifier.padding(top = 12.dp).testTag("suggestions_seed_summary"),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Up to five picked covers, overlapping in a slight fan. */
@Composable
private fun SimilarChoiceCovers(covers: List<Pair<String, List<String>>>) {
    val colors = MaterialTheme.colorScheme
    val middle = (covers.size - 1) / 2f
    Box(
        Modifier
            .padding(top = 28.dp)
            .width(CoverSize + CoverStep * (covers.size - 1))
            .height(CoverSize + 8.dp)
            .testTag("similar_choice_covers"),
    ) {
        covers.forEachIndexed { index, (url, fallbacks) ->
            OptimizedImage(
                url = url,
                fallbackUrls = fallbacks,
                proxyWidth = 160,
                contentDescription = null,
                modifier = Modifier
                    .offset(x = CoverStep * index, y = if (index.toFloat() == middle) 0.dp else 6.dp)
                    .graphicsLayer { rotationZ = (index - middle) * CoverTurn }
                    .size(CoverSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHigh)
                    .testTag("similar_choice_cover"),
            )
        }
    }
}

private const val MaxCovers = 5
private const val CoverTurn = 6f
private val CoverSize = 64.dp
private val CoverStep = 40.dp
private val SimilarChoiceMaxWidth = 480.dp
