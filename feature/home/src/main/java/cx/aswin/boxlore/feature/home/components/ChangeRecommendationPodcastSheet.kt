package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.list.LazyListKeyPolicy
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.feature.home.R

/**
 * The show picker under Because you like: "Pick for me" first, then the listener's shows as rounded
 * rows, the current pick ([selectedPodcastId]) marked. A sheet on phones and a centred panel on
 * tablets (the shared sheet host); the list keeps its own scroll inside either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeRecommendationPodcastSheet(
    candidatePodcasts: List<Podcast>,
    onDismissRequest: () -> Unit,
    onPodcastSelect: (Podcast?) -> Unit,
    selectedPodcastId: String? = null,
) {
    TrackScreenView("recommendation_show_picker")
    // Fully open, so the list's last show sits on screen once scrolled to.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shows = remember(candidatePodcasts) { LazyListKeyPolicy.deduplicateById(candidatePodcasts) { it.id } }

    BoxloreModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("home_change_show_sheet"),
        ) {
            Text(
                text = stringResource(R.string.home_change_recs_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            Text(
                text = stringResource(R.string.home_change_recs_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // The list scrolls on its own; the sheet or panel caps its height.
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth().selectableGroup().testTag("home_change_show_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "pick_for_me") {
                    PickerRow(
                        selected = false,
                        onClick = { onPodcastSelect(null) },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.testTag("home_change_show_auto"),
                        leading = {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        },
                        title = stringResource(R.string.home_change_recs_auto),
                        subtitle = stringResource(R.string.home_change_recs_auto_body),
                    )
                }
                items(shows, key = { it.id }) { podcast ->
                    val selected = podcast.id == selectedPodcastId
                    PickerRow(
                        selected = selected,
                        onClick = { onPodcastSelect(podcast) },
                        container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("home_change_show_${podcast.id}"),
                        leading = {
                            OptimizedImage(
                                url = podcast.imageUrl,
                                fallbackUrls = podcast.artworkUrls(),
                                proxyWidth = 120,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(BoxLoreShapes.medium),
                            )
                        },
                        title = podcast.title,
                        subtitle = podcast.artist.takeIf { it.isNotEmpty() },
                        subtitleColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** One rounded picker row; the selected one carries a check before its words. */
@Composable
@Suppress("LongParameterList") // A row's state, colours and its three slots.
private fun PickerRow(
    selected: Boolean,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    subtitleColor: Color = content,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .clip(shape)
            .expressiveClickable(shape = shape, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            leading()
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
