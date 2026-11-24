package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The footer for [uiState]: the similar-shows choice, loading, a failed load ([failed]), adding, or Add N shows. */
internal fun suggestionsFooterState(uiState: OnboardingUiState, loading: Boolean, failed: Boolean): SuggestionsFooterState = when {
    SimilarShowsChoice.showing(uiState) -> SuggestionsFooterState.Choice(homeFirst = SimilarShowsChoice.homeIsPrimary(uiState))
    loading -> SuggestionsFooterState.Loading
    failed -> SuggestionsFooterState.Retry
    uiState.isCompleting -> SuggestionsFooterState.Adding
    else -> SuggestionsFooterState.Finish(OnboardingSuggestionsPresentation.addCount(uiState))
}

/**
 * The one sticky button. Nothing selected: a tonal "Start without adding". Shows selected: a
 * filled "Add N shows". Loading and adding keep the button in place and say what is happening;
 * a failed load offers "Try again" with "Start without adding" above it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SuggestionsFooter(state: SuggestionsFooterState, actions: SuggestionsFooterActions) {
    val (container, content) = suggestionsButtonColors(state)
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp).testTag("suggestions_footer"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SuggestionsSecondaryAction(state, actions)
        Button(
            onClick = {
                when (state) {
                    SuggestionsFooterState.Retry -> actions.onRetry()
                    is SuggestionsFooterState.Choice -> if (state.homeFirst) actions.onGoHome() else actions.onSeeSimilar()
                    else -> actions.onFinish()
                }
            },
            enabled = state !is SuggestionsFooterState.Loading && state !is SuggestionsFooterState.Adding,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("suggestions_finish"),
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(28.dp), pressedShape = RoundedCornerShape(18.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = container, disabledContentColor = content),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            AnimatedContent(targetState = state, transitionSpec = EchoMotion.handoff(), contentAlignment = Alignment.Center, label = "suggestionsButtonLabel") { target ->
                SuggestionsFooterLabel(target)
            }
        }
    }
}

/** What the footer's buttons do; [canSkipToHome] adds "Skip to Home" while search or import suggestions load. */
internal data class SuggestionsFooterActions(
    val onFinish: () -> Unit,
    val onRetry: () -> Unit,
    val onSeeSimilar: () -> Unit,
    val onGoHome: () -> Unit,
    val canSkipToHome: Boolean,
)

/** The quieter way on, above the main button: start without, the other choice, or skip a load. */
@Composable
private fun SuggestionsSecondaryAction(state: SuggestionsFooterState, actions: SuggestionsFooterActions) {
    val (label, onClick, tag) = when {
        state is SuggestionsFooterState.Retry -> Triple(R.string.suggestions_start_without, actions.onFinish, "suggestions_start_without")
        state is SuggestionsFooterState.Choice && state.homeFirst -> Triple(R.string.similar_choice_see, actions.onSeeSimilar, "similar_choice_similar")
        state is SuggestionsFooterState.Choice -> Triple(R.string.similar_choice_home, actions.onGoHome, "similar_choice_home")
        state is SuggestionsFooterState.Loading && actions.canSkipToHome -> Triple(R.string.similar_skip_home, actions.onGoHome, "suggestions_skip_home")
        else -> return
    }
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 4.dp).testTag(tag)) {
        Text(stringResource(label))
    }
}

/** Busy reads tonal and alive; nothing selected is a quieter tonal; something to add is filled. */
@Composable
private fun suggestionsButtonColors(state: SuggestionsFooterState): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val busy = state is SuggestionsFooterState.Loading || state is SuggestionsFooterState.Adding
    val filled = state is SuggestionsFooterState.Retry || state is SuggestionsFooterState.Choice || (state is SuggestionsFooterState.Finish && state.count > 0)
    val container by animateColorAsState(
        when {
            busy -> colors.primaryContainer
            filled -> colors.primary
            else -> colors.secondaryContainer
        },
        label = "suggestionsButton",
    )
    val content by animateColorAsState(
        when {
            busy -> colors.onPrimaryContainer
            filled -> colors.onPrimary
            else -> colors.onSecondaryContainer
        },
        label = "suggestionsButtonText",
    )
    return container to content
}

@Composable
private fun SuggestionsFooterLabel(state: SuggestionsFooterState) {
    when (state) {
        SuggestionsFooterState.Loading -> EchoThinkingLabel(stringArrayResource(R.array.suggestions_finding_words))
        SuggestionsFooterState.Adding -> EchoThinkingLabel(arrayOf(stringResource(R.string.suggestions_adding)))
        SuggestionsFooterState.Retry -> Text(stringResource(R.string.suggestions_try_again), style = MaterialTheme.typography.titleMedium)
        // Labels wrap rather than lose their last words at 320dp with large text; the button grows.
        is SuggestionsFooterState.Choice -> Text(
            stringResource(if (state.homeFirst) R.string.similar_choice_home else R.string.similar_choice_see),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("suggestions_finish_label"),
        )
        is SuggestionsFooterState.Finish -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.count > 0) pluralStringResource(R.plurals.suggestions_add_shows, state.count, state.count) else stringResource(R.string.suggestions_start_without),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false).testTag("suggestions_finish_label"),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
        }
    }
}
