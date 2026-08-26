@file:Suppress("ktlint:standard:function-naming")

package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast

private val ArtworkShape = RoundedCornerShape(22.dp)
private val SpotlightTileWidth = 232.dp

@Composable
internal fun FeaturedVideoPodcastsShowcase(
    podcasts: List<Podcast>,
    tedTalksSdPodcast: Podcast,
    onPodcastClick: (Podcast, Int, String) -> Unit,
    onDismissForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (podcasts.isEmpty()) return
    var showDismissConfirmation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(podcasts) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackVideoSpotlightImpression(
            itemsCount = podcasts.size,
            podcastIds = podcasts.map(Podcast::id),
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        VideoSpotlightHeader(
            onDismissClick = { showDismissConfirmation = true },
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(end = 20.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            itemsIndexed(
                items = podcasts,
                key = { _, podcast -> podcast.id },
            ) { index, podcast ->
                SpotlightArtworkTile(
                    podcast = podcast,
                    rank = index + 1,
                    isLead = index == 0,
                    onClick = { onPodcastClick(podcast, index, "card") },
                    onHdClick =
                        if (index == 0) {
                            { onPodcastClick(podcast, index, "hd") }
                        } else {
                            null
                        },
                    onSdClick =
                        if (index == 0) {
                            { onPodcastClick(tedTalksSdPodcast, index, "sd") }
                        } else {
                            null
                        },
                )
            }
        }
    }

    if (showDismissConfirmation) {
        AlertDialog(
            onDismissRequest = { showDismissConfirmation = false },
            icon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
            title = { Text("Hide this spotlight?") },
            text = {
                Text(
                    "It won’t appear on Home again. You can still find every show through search.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDismissConfirmation = false
                        onDismissForever()
                    },
                ) {
                    Text("Hide forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDismissConfirmation = false }) {
                    Text("Keep it")
                }
            },
        )
    }
}

@Composable
private fun VideoSpotlightHeader(
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomeChildSectionHeader(
            title = "Video Spotlight",
            icon = Icons.Rounded.Movie,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismissClick,
            modifier = Modifier.size(36.dp),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hide this spotlight",
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun SpotlightArtworkTile(
    podcast: Podcast,
    rank: Int,
    isLead: Boolean,
    onClick: () -> Unit,
    onHdClick: (() -> Unit)?,
    onSdClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier.width(SpotlightTileWidth),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 3.dp,
                        shape = ArtworkShape,
                        clip = false,
                    ).border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = ArtworkShape,
                    ).expressiveClickable(
                        shape = ArtworkShape,
                        onClick = onClick,
                    ).clip(ArtworkShape),
        ) {
            OptimizedImage(
                url = podcast.imageUrl,
                proxyWidth = 560,
                contentDescription = podcast.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                shape = RoundedCornerShape(11.dp),
                color =
                    if (isLead) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                contentColor =
                    if (isLead) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            ) {
                Text(
                    text = if (isLead) "EDITOR’S PICK" else rank.toString().padStart(2, '0'),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = GoogleSansWeight.bold,
                )
            }
            if (onSdClick != null) {
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Button(
                        onClick = onHdClick ?: onClick,
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 15.dp),
                    ) {
                        Text(
                            text = "HD",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.bold,
                        )
                    }
                    FilledTonalButton(
                        onClick = onSdClick,
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 15.dp),
                    ) {
                        Text(
                            text = "SD",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.bold,
                        )
                    }
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = RoundedCornerShape(12.dp),
                        onClick = onClick,
                    ).padding(horizontal = 3.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
