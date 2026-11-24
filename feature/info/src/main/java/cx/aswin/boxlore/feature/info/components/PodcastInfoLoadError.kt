package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.feature.info.R

/** Podcast Info error state (no local row and show details failed): what happened, Try again and Back. */
@Composable
internal fun PodcastInfoLoadError(
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoStatusMessage(
        icon = Icons.Rounded.CloudOff,
        title = stringResource(R.string.podcast_info_load_error),
        body = stringResource(R.string.info_load_error_body),
        primary = InfoStatusAction(stringResource(R.string.podcast_info_retry), onRetry),
        secondary = InfoStatusAction(stringResource(R.string.episode_info_back), onBack),
        modifier = modifier,
    )
}

/**
 * The end of the episode list when its next page (or, with [listEmpty], a new order's first page)
 * couldn't load after a few tries: what happened and Try again. One full-width list row, capped to
 * the utility card width on wide windows; announced politely to screen readers.
 */
@Composable
internal fun ShowPageLoadFailedRow(
    listEmpty: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (listEmpty) R.string.podcast_info_episodes_failed else R.string.podcast_info_more_episodes_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.podcast_info_retry))
        }
    }
}
