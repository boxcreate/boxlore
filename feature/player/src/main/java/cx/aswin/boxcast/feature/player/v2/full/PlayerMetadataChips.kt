package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerMetadataChips(
    publishedDateSeconds: Long,
    durationSeconds: Int,
    currentChapter: Chapter? = null,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val chipShape = playerSheetShape(12.dp, 12.dp, 12.dp, 12.dp)

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (publishedDateSeconds > 0) {
            MetadataChip(
                label = formatRelativeDate(publishedDateSeconds),
                leadingIcon = {
            Icon(
                Icons.Rounded.CalendarToday,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
                colorScheme = colorScheme,
                shape = chipShape,
            )
        }

        if (durationSeconds > 0) {
            MetadataChip(
                label = formatDurationLabel(durationSeconds),
                leadingIcon = {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
                colorScheme = colorScheme,
                shape = chipShape,
            )
        }

        currentChapter?.title?.takeIf { it.isNotBlank() }?.let { chapterTitle ->
            MetadataChip(
                label = chapterTitle,
                leadingIcon = {
            Icon(
                Icons.Rounded.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
                colorScheme = colorScheme,
                shape = chipShape,
                emphasized = true,
            )
        }
    }
}

@Composable
private fun MetadataChip(
    label: String,
    leadingIcon: @Composable () -> Unit,
    colorScheme: ColorScheme,
    shape: androidx.compose.ui.graphics.Shape,
    emphasized: Boolean = false,
) {
    val containerColor = if (emphasized) {
        colorScheme.tertiaryContainer
    } else {
        colorScheme.surfaceContainerHigh
    }
    val labelColor = if (emphasized) {
        colorScheme.onTertiaryContainer
    } else {
        colorScheme.onSurfaceVariant
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leadingIcon,
        shape = shape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
            disabledContainerColor = containerColor,
            disabledLabelColor = labelColor,
        ),
        border = null,
    )
}

@Composable
fun MetadataSurfacePill(
    label: String,
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = playerSheetShape(12.dp, 12.dp, 12.dp, 12.dp),
        color = colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatRelativeDate(timestampSeconds: Long): String {
    if (timestampSeconds == 0L) return ""
    val now = System.currentTimeMillis() / 1000
    val diff = now - timestampSeconds
    return when {
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        diff < 2592000 -> "${diff / 604800}w ago"
        diff < 31536000 -> "${diff / 2592000}mo ago"
        else -> "${diff / 31536000}y ago"
    }
}

private fun formatDurationLabel(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
