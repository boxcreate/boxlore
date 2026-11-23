package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** [EpisodeTypeChip]'s two sizes: the show's episode list, and the larger episode page. */
internal enum class EpisodeTypeChipSize { Small, Medium }

/**
 * An episode's type tag (Bonus, Trailer) or its Video tag, the same pill wherever it appears: a
 * solid `surfaceContainerHigh` pill with `onSurfaceVariant` text, and a camera for video.
 */
@Composable
internal fun EpisodeTypeChip(label: String, video: Boolean = false, size: EpisodeTypeChipSize = EpisodeTypeChipSize.Small, modifier: Modifier = Modifier) {
    Surface(modifier, shape = ExpressiveShapes.Pill, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        val padding = if (size == EpisodeTypeChipSize.Small) Modifier.padding(horizontal = 8.dp, vertical = 3.dp) else Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        Row(padding, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (video) Icon(Icons.Rounded.Videocam, null, Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = GoogleSansWeight.medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
