package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage

/** A selected episode card's fill and the colour of its text. */
@Immutable
internal data class EpisodeSelectionColors(val container: Color, val content: Color)

/**
 * A selected episode card's colours. The regular page fills it with the theme's primary
 * container. A Showcase page's accent is the show's own colour, often bright or pale (The Rest Is
 * History's ivory, Serial's red), and its primary container is that same colour, which would turn
 * the card into a block of it under text meant for a dark card; there the card keeps its own
 * surface, tinted with the accent, and its usual text ([brandedSelectionColors]). Both draw the
 * accent's 2dp ring and tick.
 */
@Composable
internal fun episodeSelectionColors(): EpisodeSelectionColors {
    val scheme = MaterialTheme.colorScheme
    return if (LocalBrandedPage.current != null) {
        brandedSelectionColors(scheme)
    } else {
        EpisodeSelectionColors(scheme.primaryContainer, scheme.onPrimaryContainer)
    }
}

/** A Showcase page's selected card: its card surface with [SELECTED_TINT] of the accent, and its usual text. */
internal fun brandedSelectionColors(scheme: ColorScheme): EpisodeSelectionColors =
    EpisodeSelectionColors(scheme.primary.copy(alpha = SELECTED_TINT).compositeOver(scheme.surfaceContainerLow), scheme.onSurface)

/** How much of the accent tints a selected card on a Showcase page. */
internal const val SELECTED_TINT = 0.16f
