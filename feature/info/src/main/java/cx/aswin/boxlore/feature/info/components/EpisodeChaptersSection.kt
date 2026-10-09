package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.feature.info.R
import java.util.Locale

@Composable
internal fun EpisodeChaptersSection(episodeId: String, chapters: List<Chapter>, positionMs: Long, onSeekTo: (Long) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(episodeId) { mutableStateOf(false) }
    val visible = if (expanded) chapters else chapters.take(4)
    val active = if (positionMs > 0) chapters.indexOfLast { it.startTime * 1_000 <= positionMs } else -1
    Column(modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.episode_info_chapters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.episode_info_chapter_count, chapters.size, chapters.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visible.forEachIndexed { index, chapter ->
            val time = chapterTime(chapter.startTime)
            val action = stringResource(R.string.episode_info_jump_chapter, chapter.title, time)
            val current = stringResource(R.string.episode_info_current_chapter)
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 24.dp else 5.dp,
                topEnd = if (index == 0) 24.dp else 5.dp,
                bottomStart = if (index == visible.lastIndex) 24.dp else 5.dp,
                bottomEnd = if (index == visible.lastIndex) 24.dp else 5.dp
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = shape,
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                    contentDescription = action
                    if (active == index) stateDescription = current
                }
                    .expressiveClickable(shape = shape) { onSeekTo((chapter.startTime * 1_000).toLong()) }
            ) {
                Row(Modifier.heightIn(min = 64.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (active == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Text(time, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
                    }
                    Text(chapter.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (chapters.size > 4) {
            TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.episode_info_fewer_chapters else R.string.episode_info_all_chapters))
        }
        }
    }
}

internal fun chapterTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    return if (total >= 3_600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", total / 3_600, total / 60 % 60, total % 60)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60)
    }
}
