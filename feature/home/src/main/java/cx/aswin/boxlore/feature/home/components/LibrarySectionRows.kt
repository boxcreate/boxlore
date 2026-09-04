package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import cx.aswin.boxlore.core.designsystem.components.NewEpisodeBadge
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.isLatestEpisodeNew

internal data class CoverPin(
    val pinned: Boolean = false,
    val onToggle: () -> Unit = {},
) {
    val actionLabel: String
        get() = if (pinned) "Unpin" else "Pin"
}

private fun selectorCoverScale(isSelected: Boolean): Float = if (isSelected) 1.05f else 0.95f

private fun selectorCoverAlpha(
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
): Float = if (isSelected || !isAnyPodcastSelected) 1f else 0.6f

private fun selectorCoverCornerDp(isSelected: Boolean): Dp = if (isSelected) 16.dp else 12.dp

private fun selectorCoverBorderDp(isSelected: Boolean): Dp = if (isSelected) 3.dp else 0.dp

@Composable
internal fun SelectorCover(
    podcast: Podcast,
    lastSeenId: String?,
    isSelected: Boolean,
    isAnyPodcastSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(60.dp),
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

    val scale by animateFloatAsState(targetValue = selectorCoverScale(isSelected), label = "scale")
    val alpha by animateFloatAsState(
        targetValue = selectorCoverAlpha(isSelected, isAnyPodcastSelected),
        label = "alpha",
    )
    val cornerRadius by animateDpAsState(
        targetValue = selectorCoverCornerDp(isSelected),
        label = "cornerRadius",
    )
    val borderStrokeWidth by animateDpAsState(
        targetValue = selectorCoverBorderDp(isSelected),
        label = "borderStrokeWidth",
    )
    val coverShape = RoundedCornerShape(cornerRadius)
    var showPinMenu by remember { mutableStateOf(false) }

    Box(
        modifier =
        modifier
            .scale(scale),
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .expressiveClickable(
                    shape = coverShape,
                    onLongClickLabel = pin.actionLabel,
                    onLongClick = { showPinMenu = true },
                    onClick = onClick,
                ).clip(coverShape),
        ) {
            OptimizedImage(
                url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
                proxyWidth = 120,
                contentDescription = podcast.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(alpha),
            )
            SelectorCoverSelectedBorder(
                visible = isSelected,
                borderStrokeWidth = borderStrokeWidth,
                coverShape = coverShape,
            )
        }

        CoverPinMenu(
            expanded = showPinMenu,
            actionLabel = pin.actionLabel,
            onDismiss = { showPinMenu = false },
            onToggle = pin.onToggle,
        )

        if (hasRecentNew && !isSelected) {
            NewEpisodeBadge(
                modifier = Modifier.offset(x = 6.dp, y = (-4).dp),
            )
        }
        if (pin.pinned) {
            HomePinnedBadge(modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}

@Composable
private fun SelectorCoverSelectedBorder(
    visible: Boolean,
    borderStrokeWidth: Dp,
    coverShape: RoundedCornerShape,
) {
    if (!visible) return
    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .border(borderStrokeWidth, MaterialTheme.colorScheme.primary, coverShape),
    )
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
            contentDescription = "Pinned",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier =
            Modifier
                .size(10.dp)
                .graphicsLayer { rotationZ = 45f },
        )
    }
}
