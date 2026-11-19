package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.HiddenShow
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsIconContainer

/** The shows hidden everywhere, newest first, and how to unhide one ([HiddenShowsSection]). */
data class HiddenShowsControl(
    /** Null until the stored list is read, so the empty state never shows before the list. */
    val shows: List<HiddenShow>? = emptyList(),
    val onUnhide: (HiddenShow) -> Unit = {},
)

/** LeaveMeAlone's Hidden shows: each hidden show with Unhide, or how to hide one when there are none. */
@Composable
internal fun HiddenShowsSection(control: HiddenShowsControl) {
    val shows = control.shows ?: return
    SettingsGroup(
        title = stringResource(R.string.recommendations_hidden_title),
        footer = stringResource(R.string.recommendations_hidden_footer),
    ) {
        if (shows.isEmpty()) {
            HiddenShowsEmptyRow()
        } else {
            shows.forEachIndexed { index, show ->
                if (index > 0) SettingsDivider()
                HiddenShowRow(show) {
                    AnalyticsHelper.trackSettingsInteraction(ACTION_UNHIDDEN, show.id)
                    control.onUnhide(show)
                }
            }
        }
    }
}

@Composable
private fun HiddenShowsEmptyRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag(HIDDEN_SHOWS_EMPTY_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsIconContainer(
            icon = Icons.Rounded.VisibilityOff,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.recommendations_hidden_empty), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.recommendations_hidden_empty_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HiddenShowRow(show: HiddenShow, onUnhide: () -> Unit) {
    val title = show.title.ifBlank { stringResource(R.string.recommendations_hidden_unknown) }
    val unhideDescription = stringResource(R.string.recommendations_hidden_unhide_description, title)
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HiddenShowArtwork(show.imageUrl)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUnhide, modifier = Modifier.semantics { contentDescription = unhideDescription }) {
            Text(stringResource(R.string.recommendations_hidden_unhide))
        }
    }
}

@Composable
private fun HiddenShowArtwork(imageUrl: String) {
    val shape = MaterialTheme.shapes.medium
    val placeholder = @Composable {
        Box(Modifier.size(40.dp).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
    }
    if (imageUrl.isBlank()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).clip(shape),
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

/** `settings_interaction` action for an Unhide here; the value is the show ID. Same key as the show page's. */
private const val ACTION_UNHIDDEN = "show_unhidden"

/** Test tag on the row shown when no show is hidden. */
internal const val HIDDEN_SHOWS_EMPTY_TAG = "hidden_shows_empty"
