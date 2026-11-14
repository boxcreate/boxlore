package cx.aswin.boxlore.wear.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import cx.aswin.boxlore.wear.R

/**
 * Asked when Play is pressed here while the phone is playing: the watch could be meant as the
 * phone's remote, or as the thing to listen on. The watch is offered first, since that is where
 * the listener's hand is.
 */
@Composable
internal fun PlayWhereScreen(episodeTitle: String, onWatch: () -> Unit, onPhone: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(text = episodeTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            item {
                Text(
                    text = stringResource(R.string.wear_phone_is_playing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                )
            }
            item {
                Button(
                    onClick = onWatch,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(Icons.Rounded.Watch, contentDescription = null) },
                    label = { Text(stringResource(R.string.wear_play_on_watch), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onPhone,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    icon = { Icon(Icons.Rounded.Smartphone, contentDescription = null) },
                    label = { Text(stringResource(R.string.wear_play_on_phone), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}
