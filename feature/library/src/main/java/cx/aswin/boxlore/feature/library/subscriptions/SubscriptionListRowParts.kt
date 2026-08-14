package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Podcast

@Composable
internal fun SubscriptionPinnedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(5.dp)
                .size(22.dp)
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
                    .size(12.dp)
                    .graphicsLayer { rotationZ = 45f },
        )
    }
}

@Composable
internal fun SubscriptionListRowText(
    title: String,
    artist: String,
    hasRecentNew: Boolean,
    updateLabel: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = GoogleSansWeight.semiBold,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasRecentNew) {
                Text(
                    text = "NEW",
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = GoogleSansWeight.bold,
                            fontSize = 10.sp,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = artist.takeIf { it.isNotEmpty() } ?: "Podcast",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (updateLabel != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = updateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SubscriptionListArtwork(
    podcast: Podcast,
    isPinned: Boolean,
    isDragging: Boolean,
    dragModifier: Modifier,
    artworkShape: RoundedCornerShape,
    dragScale: Float,
    dragElevation: Dp,
) {
    Box(
        modifier =
            dragModifier
                .size(64.dp)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                    shape = artworkShape
                    clip = true
                }.shadow(elevation = dragElevation, shape = artworkShape, clip = false)
                .clip(artworkShape),
    ) {
        OptimizedImage(
            url = podcast.imageUrl.takeIf { it.isNotEmpty() } ?: podcast.fallbackImageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(artworkShape),
            errorContent = {
                ArtworkTitleFallback(title = podcast.title)
            },
        )
        if (isPinned) {
            SubscriptionPinnedBadge(modifier = Modifier.align(Alignment.BottomStart))
        }
    }
}
