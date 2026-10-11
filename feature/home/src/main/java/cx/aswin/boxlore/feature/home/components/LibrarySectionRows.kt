package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls
import cx.aswin.boxlore.core.model.isLatestEpisodeNew
import cx.aswin.boxlore.feature.home.R

internal data class CoverPin(
    val pinned: Boolean = false,
    val onToggle: () -> Unit = {},
)

@Composable
internal fun SelectorCover(
    podcast: Podcast,
    lastSeenId: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(YourShowsLayout.TileWidth).height(YourShowsLayout.TileHeight),
    pin: CoverPin = CoverPin(),
) {
    val latestEpisodeId = podcast.latestEpisode?.id
    val latestEpisodePubDate = podcast.latestEpisode?.publishedDate ?: 0L

    val hasRecentNew =
        remember(
            podcast.subscribedAt,
            latestEpisodeId,
            latestEpisodePubDate,
            podcast.rssHasNewEpisodes,
            lastSeenId,
        ) {
            podcast.isLatestEpisodeNew(lastSeenId)
        }

    val coverShape = rememberYourShowsSelectorShape(podcast.id, isSelected)
    val actionLabel = stringResource(if (pin.pinned) R.string.home_shows_unpin else R.string.home_shows_pin)
    var showPinMenu by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
                        .expressiveClickable(
                            feedback = ExpressivePressFeedback.Control,
                shape = RoundedCornerShape(22.dp),
                onLongClickLabel = actionLabel,
                onLongClick = { showPinMenu = true },
                onClick = onClick,
            )
            .semantics {
                selected = isSelected
                role = Role.Button
                contentDescription = podcast.title
            }
            .testTag("home_show_selector_${podcast.id}")
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.widthIn(max = YourShowsLayout.CoverSize).fillMaxWidth().aspectRatio(1f)) {
            OptimizedImage(
                url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
                fallbackUrls = podcast.artworkUrls(),
                proxyWidth = 320,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(coverShape)
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, coverShape) else Modifier)
                    .testTag("home_show_artwork_${podcast.id}"),
            )
            if (hasRecentNew && !isSelected) {
                NewEpisodeBadge(modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
            }
            if (pin.pinned) HomePinnedBadge(modifier = Modifier.align(Alignment.BottomStart))
            if (isSelected) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, coverShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                }
            }
        }
        CoverPinMenu(showPinMenu, actionLabel, { showPinMenu = false }, pin.onToggle)
    }
}

@Composable
private fun CoverPinMenu(
    expanded: Boolean,
    actionLabel: String,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        offset = DpOffset(x = 0.dp, y = 4.dp),
        properties = PopupProperties(clippingEnabled = false, focusable = true),
    ) {
        DropdownMenuItem(
            text = { Text(actionLabel) },
            onClick = {
                onDismiss()
                onToggle()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = 45f },
                )
            },
        )
    }
}

@Composable
private fun HomePinnedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
        modifier
            .padding(2.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = stringResource(R.string.home_shows_pinned),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier =
            Modifier
                .size(10.dp)
                .graphicsLayer { rotationZ = 45f },
        )
    }
}
