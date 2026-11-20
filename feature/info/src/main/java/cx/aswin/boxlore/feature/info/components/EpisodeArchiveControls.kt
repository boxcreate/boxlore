package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.R

/** Marks an archived episode: its row when Show archived or a search lists it, and its episode page. */
@Composable
internal fun EpisodeArchivedPill(modifier: Modifier = Modifier) {
    Surface(
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp),
    ) {
        Text(
            stringResource(R.string.episode_archived),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Show archived episodes (N) / Hide archived episodes, next to Hide completed. */
@Composable
internal fun ArchivedEpisodesMenuItem(archive: PodcastInfoArchiveMenu, onDismiss: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                if (archive.showArchived) {
                    stringResource(R.string.podcast_info_hide_archived)
                } else {
                    stringResource(R.string.podcast_info_show_archived, archive.archivedCount)
                },
            )
        },
        onClick = {
            onDismiss()
            archive.onToggle()
        },
        leadingIcon = { Icon(if (archive.showArchived) Icons.Rounded.VisibilityOff else Icons.Rounded.Archive, contentDescription = null) },
    )
}
