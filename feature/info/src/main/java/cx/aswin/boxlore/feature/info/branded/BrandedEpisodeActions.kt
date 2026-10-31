package cx.aswin.boxlore.feature.info.branded

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeActions
import cx.aswin.boxlore.feature.info.components.PodcastEpisodeProgress

/** A Showcase card's colours for the episode buttons: the like, download and queue toggles, and Play. */
@Immutable
internal data class BrandedActionColors(
    val toggle: Color,
    val onToggle: Color,
    val checked: Color,
    val onChecked: Color,
    val play: Color,
    val onPlay: Color,
)

/**
 * The spotlight's buttons: the regular episode card's row, the same buttons in the same order,
 * shapes and sizes, in the show's colours. Only its width follows the card. With [showProgress],
 * the regular card's listening progress follows while the episode is partway: a bar in the Play
 * colour on the buttons' own, with the time left at its end.
 */
@Composable
internal fun BrandedEpisodeActions(spotlight: BrandedSpotlight, colors: BrandedActionColors, modifier: Modifier = Modifier, showProgress: Boolean = false) {
    val scheme = MaterialTheme.colorScheme.copy(
        surfaceContainerHighest = colors.toggle,
        onSurfaceVariant = colors.onToggle,
        primaryContainer = colors.checked,
        onPrimaryContainer = colors.onChecked,
        // The row's Play takes the accent's paired foreground from the theme, so both are set here.
        primary = colors.play,
        onPrimary = colors.onPlay,
    )
    MaterialTheme(colorScheme = scheme) {
        Column(modifier) {
            PodcastEpisodeActions(spotlight.actions, spotlight.callbacks, scheme.primary)
            if (showProgress) spotlight.progress?.let { PodcastEpisodeProgress(it, spotlight.timeLeft, scheme.primary) }
        }
    }
}
