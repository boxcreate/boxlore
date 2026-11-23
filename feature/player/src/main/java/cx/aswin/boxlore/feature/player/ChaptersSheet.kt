package cx.aswin.boxlore.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.ShimmerScope
import cx.aswin.boxlore.core.designsystem.theme.SkeletonBlock
import cx.aswin.boxlore.core.designsystem.theme.SkeletonTextLines
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Chapter

@Composable
private fun ChaptersHeader(onClose: () -> Unit, colorScheme: ColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.player_chapters),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = GoogleSansWeight.bold,
            color = colorScheme.onSurface
        )
        // The same close icon as the queue sheet's header.
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.player_close), tint = colorScheme.onSurface)
        }
    }
}

@Composable
private fun ChaptersEmptyPlaceholder(
    colorScheme: ColorScheme
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.player_no_chapters),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
@Suppress("LongParameterList")
fun ChaptersSheetContent(
    chapters: List<Chapter>,
    positionFlow: kotlinx.coroutines.flow.Flow<Long>,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    chaptersUrl: String? = null,
    isChaptersLoading: Boolean = false,
    showHeader: Boolean = true,
) {
    val positionMs by positionFlow.collectAsState(initial = 0L)
    // In the tablet panel (no header) the rows sit on the panel's own colour, one step lighter.
    val inactiveRowColor = if (showHeader) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (showHeader) colorScheme.surface else Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (showHeader) ChaptersHeader(onClose = onClose, colorScheme = colorScheme)

            if (isChaptersLoading) {
                ChapterRowsSkeleton(inactiveRowColor)
            } else if (chapters.isEmpty()) {
                ChaptersEmptyPlaceholder(
                    colorScheme = colorScheme
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        val startMs = (chapter.startTime * 1000).toLong()
                        val endMs = if (index < chapters.size - 1) {
                            (chapters[index + 1].startTime * 1000).toLong()
                        } else {
                            Long.MAX_VALUE
                        }
                        val isActive = positionMs >= startMs && positionMs < endMs

                        ChapterRow(
                            chapter = chapter,
                            startMs = startMs,
                            isActive = isActive,
                            colorScheme = colorScheme,
                            onClick = { onSeek(startMs) },
                            inactiveColor = inactiveRowColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChapterRow(
    chapter: Chapter,
    startMs: Long,
    isActive: Boolean,
    colorScheme: ColorScheme,
    onClick: () -> Unit,
    inactiveColor: Color = colorScheme.surfaceContainerHigh
) {
    val backgroundColor = if (isActive) {
        colorScheme.primaryContainer
    } else {
        inactiveColor
    }

    val contentColor = if (isActive) {
        colorScheme.onPrimaryContainer
    } else {
        colorScheme.onSurface
    }

    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(shape = shape, onClick = onClick)
            .clip(shape)
            .background(backgroundColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Image
        if (!chapter.img.isNullOrBlank()) {
            OptimizedImage(
                url = chapter.img,
                proxyWidth = 160,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) GoogleSansWeight.bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(startMs),
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                fontWeight = GoogleSansWeight.semiBold
            )
        }
    }
}

/** Chapters loading: placeholder rows shaped like the real ones, so the list keeps its shape when they arrive. */
@Composable
private fun ChapterRowsSkeleton(rowColor: Color) {
    ShimmerScope(active = true) {
        Column(Modifier.fillMaxWidth().testTag(CHAPTER_SKELETON_TAG), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(CHAPTER_SKELETON_ROWS) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(rowColor).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SkeletonBlock(Modifier.size(48.dp), shape = MaterialTheme.shapes.small)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        SkeletonTextLines(MaterialTheme.typography.bodyLarge, lines = 1, widths = listOf(0.8f))
                        Spacer(Modifier.height(4.dp))
                        SkeletonTextLines(MaterialTheme.typography.labelMedium, lines = 1, widths = listOf(0.25f))
                    }
                }
            }
        }
    }
}

internal const val CHAPTER_SKELETON_TAG = "chapter_skeleton"
private const val CHAPTER_SKELETON_ROWS = 5
