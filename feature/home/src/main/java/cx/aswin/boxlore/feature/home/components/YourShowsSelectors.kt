package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R

/**
 * Cover tiles are square. Covers sit 10dp apart (gap plus both tiles' padding) and their outer
 * edges line up with the 16dp content inset below.
 */
internal object YourShowsLayout {
    val CoverSize = 60.dp

    /** Room around each cover for the selection outline and its badges. */
    val TilePadding = 4.dp
    val TileWidth = CoverSize + TilePadding * 2
    val TileHeight = TileWidth
    val Padding = 12.dp
    val Gap = 2.dp
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
    vertical: Boolean = false,
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
        val itemModifier = Modifier.width(tileWidth).height(tileWidth)
        val items: List<Podcast?> = if (podcasts.size > 1) listOf(null) + podcasts else podcasts
        if (vertical) {
            VerticalYourShowsSelectors(items) { podcast ->
                ShowSelectorItem(podcast, podcasts.size, selectedPodcastId, lastSeenEpisodes, pinnedPodcastIds, onPodcastSelected, onTogglePin, Modifier.size(YourShowsLayout.TileWidth))
            }
        } else if (podcasts.size <= 4) {
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

private val TitlePressInset = 6.dp

@Composable
internal fun SelectedShowHeader(
    podcast: Podcast,
    canClear: Boolean,
    onOpenShow: () -> Unit,
    onClear: () -> Unit,
) {
    YourShowsContentHeader(
        modifier = Modifier.testTag("home_selected_show_header"),
        lead = {
            val openLabel = stringResource(R.string.home_shows_open_show)
            // The name and its sort line also open the show, like the arrow button.
            Column(
                // The press shape clips, so the text gets inner room and shifts back onto the 16dp grid.
                modifier = Modifier
                    .offset(x = -TitlePressInset)
                    .expressiveClickable(shape = RoundedCornerShape(12.dp), onClick = onOpenShow)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        onClick(label = openLabel) {
                            onOpenShow()
                            true
                        }
                    }
                    .testTag("home_selected_show_title")
                    .padding(horizontal = TitlePressInset, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SelectedShowTitle(podcast.title, podcast.isRss || podcast.id.startsWith("rss:"), maxLines = 2, style = YourShowsText.title())
                // Newest first is the default and needs no label; serial shows say they continue in order.
                if (podcast.preferredSort == "oldest") {
                    Text(
                        text = stringResource(R.string.home_shows_next_episodes),
                        style = YourShowsText.subtitle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        FilledTonalIconButton(onClick = onOpenShow, modifier = Modifier.testTag("home_selected_show_info")) {
            Icon(Icons.Rounded.ChevronRight, stringResource(R.string.home_shows_open_show))
        }
        if (canClear) {
            FilledTonalIconButton(
                onClick = onClear,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.testTag("home_selected_show_clear"),
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.home_shows_clear_filter))
            }
        }
    }
}
