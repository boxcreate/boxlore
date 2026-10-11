package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R

internal object YourShowsLayout {
    val TileWidth = 68.dp
    val CoverSize = 54.dp
    val TileHeight = 68.dp
    val Padding = 10.dp
    val Gap = 4.dp
}

@Composable
@Suppress("LongParameterList")
internal fun YourShowsSelectors(
    podcasts: List<Podcast>,
    selectedPodcastId: String?,
    lastSeenEpisodes: Map<String, String>,
    pinnedPodcastIds: Set<String>,
    onPodcastSelected: (String?) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    val tileHeight = YourShowsLayout.TileHeight
    val interleaved = remember(podcasts) {
        if (podcasts.size > 9) podcasts.chunked(2).flatMap { it.reversed() } else podcasts
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_show_selectors"),
    ) {
        val tileWidth = ((maxWidth - YourShowsLayout.Padding * 2 - YourShowsLayout.Gap * 4) / 5)
            .coerceAtMost(YourShowsLayout.TileWidth)
        val itemModifier = Modifier.width(tileWidth).height(tileHeight)
        val items: List<Podcast?> = if (podcasts.size > 1) listOf(null) + podcasts else podcasts
        if (podcasts.size <= 4) {
            LazyRow(
                contentPadding = PaddingValues(YourShowsLayout.Padding),
                horizontalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap),
            ) {
                items(items, key = { it?.id ?: "mixtape" }) { podcast ->
                    ShowSelectorItem(
                        podcast,
                        podcasts.size,
                        selectedPodcastId,
                        lastSeenEpisodes,
                        pinnedPodcastIds,
                        onPodcastSelected,
                        onTogglePin,
                        Modifier.width(YourShowsLayout.TileWidth).height(tileHeight)
                            .animateItem(fadeInSpec = null, fadeOutSpec = null),
                    )
                }
            }
        } else if (podcasts.size <= 9) {
            Column(
                modifier = Modifier.padding(YourShowsLayout.Padding),
                verticalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap),
            ) {
                items.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap)) {
                        row.forEach { podcast ->
                            ShowSelectorItem(
                                podcast,
                                podcasts.size,
                                selectedPodcastId,
                                lastSeenEpisodes,
                                pinnedPodcastIds,
                                onPodcastSelected,
                                onTogglePin,
                                itemModifier,
                            )
                        }
                    }
                }
            }
        } else {
            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier.height(tileHeight * 2 + YourShowsLayout.Padding * 2 + YourShowsLayout.Gap),
                contentPadding = PaddingValues(YourShowsLayout.Padding),
                horizontalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap),
                verticalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap),
            ) {
                item(key = "mixtape") {
                    ShowSelectorItem(
                        null,
                        podcasts.size,
                        selectedPodcastId,
                        lastSeenEpisodes,
                        pinnedPodcastIds,
                        onPodcastSelected,
                        onTogglePin,
                        Modifier.width(YourShowsLayout.TileWidth).height(tileHeight)
                    )
                }
                items(interleaved, key = { it.id }) { podcast ->
                    ShowSelectorItem(
                        podcast,
                        podcasts.size,
                        selectedPodcastId,
                        lastSeenEpisodes,
                        pinnedPodcastIds,
                        onPodcastSelected,
                        onTogglePin,
                        Modifier.width(YourShowsLayout.TileWidth).height(tileHeight)
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ShowSelectorItem(
    podcast: Podcast?,
    count: Int,
    selectedId: String?,
    lastSeen: Map<String, String>,
    pinnedIds: Set<String>,
    onSelected: (String?) -> Unit,
    onTogglePin: (String) -> Unit,
    modifier: Modifier,
) {
    if (podcast == null) {
        MixtapeSelectorCover(
            isSelected = selectedId == null,
            onClick = { onSelected(null) },
            modifier = modifier,
        )
    } else {
        SelectorCover(
            podcast = podcast,
            lastSeenId = lastSeen[podcast.id],
            isSelected = selectedId == podcast.id || count == 1,
            onClick = { if (count > 1) onSelected(if (selectedId == podcast.id) null else podcast.id) },
            modifier = modifier,
            pin = CoverPin(podcast.id in pinnedIds) { onTogglePin(podcast.id) },
        )
    }
}

@Composable
internal fun SelectedShowHeader(
    podcast: Podcast,
    canClear: Boolean,
    onOpenShow: () -> Unit,
    onClear: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().testTag("home_selected_show_header"), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            SelectedShowTitle(podcast.title, podcast.isRss || podcast.id.startsWith("rss:"), maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(if (podcast.preferredSort == "oldest") R.string.home_shows_next_episodes else R.string.home_shows_latest_episodes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenShow, modifier = Modifier.testTag("home_selected_show_info")) {
            Box(
                modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ChevronRight, stringResource(R.string.home_shows_open_show), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        if (canClear) {
            IconButton(onClick = onClear, modifier = Modifier.testTag("home_selected_show_clear")) {
                Icon(Icons.Rounded.Close, stringResource(R.string.home_shows_clear_filter), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
