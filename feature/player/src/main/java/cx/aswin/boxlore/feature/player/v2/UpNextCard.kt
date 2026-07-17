package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.player.v2.logic.queueSourceLabel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Below-the-fold preview of the next three queued episodes.
 */
@Composable
fun UpNextCard(
    queuedEpisodes: List<Episode>,
    colorScheme: ColorScheme,
    onOpenQueue: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (queuedEpisodes.isEmpty()) return

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 28.dp, smoothnessAsPercentTL = 60,
        cornerRadiusTR = 28.dp, smoothnessAsPercentTR = 60,
        cornerRadiusBL = 28.dp, smoothnessAsPercentBL = 60,
        cornerRadiusBR = 28.dp, smoothnessAsPercentBR = 60
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Up next",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (queuedEpisodes.size == 1) {
                            "1 episode queued"
                        } else {
                            "${queuedEpisodes.size} episodes queued"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onOpenQueue,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View queue")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            queuedEpisodes.take(3).forEachIndexed { index, episode ->
                QueuePreviewRow(
                    episode = episode,
                    queuePosition = index + 1,
                    colorScheme = colorScheme,
                    onPlay = onPlayNext.takeIf { index == 0 }
                )
                if (index < minOf(queuedEpisodes.lastIndex, 2)) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun QueuePreviewRow(
    episode: Episode,
    queuePosition: Int,
    colorScheme: ColorScheme,
    onPlay: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedImage(
            url = episode.imageUrl?.takeIf { it.isNotBlank() } ?: episode.podcastImageUrl,
            proxyWidth = 128,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 16.dp, smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 16.dp, smoothnessAsPercentTR = 60,
                        cornerRadiusBL = 16.dp, smoothnessAsPercentBL = 60,
                        cornerRadiusBR = 16.dp, smoothnessAsPercentBR = 60
                    )
                )
                .background(colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title.replace("+", " "),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = queueSourceLabel(episode) ?: episode.podcastTitle
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle.replace("+", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (onPlay != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary)
                    .expressiveClickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play next episode",
                    tint = colorScheme.onPrimary,
                    modifier = Modifier.size(25.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = colorScheme.surfaceContainerHighest,
                contentColor = colorScheme.onSurfaceVariant
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = queuePosition.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
