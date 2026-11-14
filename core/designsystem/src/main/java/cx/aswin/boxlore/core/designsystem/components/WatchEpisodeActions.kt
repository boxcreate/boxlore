package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Episode

/** Whether a linked watch holds an episode's audio. */
enum class WatchEpisodeCopy { None, Downloading, OnWatch }

/**
 * What an episode page can do with a linked watch: see whether the watch holds the episode, send
 * it there and remove it again. Each order reports whether the watch could be told.
 */
@Immutable
class WatchEpisodeActions(
    val copyOf: (episodeId: String, audioUrl: String?) -> WatchEpisodeCopy,
    val send: (episode: Episode, onResult: (Boolean) -> Unit) -> Unit,
    val remove: (episode: Episode, onResult: (Boolean) -> Unit) -> Unit,
)

/** Provided by the app's root once boxlore on a watch has linked with this phone; null until then. */
val LocalWatchEpisodeActions = staticCompositionLocalOf<WatchEpisodeActions?> { null }

/**
 * A watch with a small badge: an arrow to send the episode there, the same arrow on the accent
 * colour while it downloads, and a tick once the watch has it.
 */
@Composable
fun SendToWatchIcon(copy: WatchEpisodeCopy, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = copy != WatchEpisodeCopy.None
    Box(modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Watch, contentDescription = null, tint = if (copy == WatchEpisodeCopy.OnWatch) colors.primary else LocalContentColor.current, modifier = Modifier.size(24.dp))
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 3.dp)
                .size(14.dp)
                .background(if (accent) colors.primary else colors.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (copy == WatchEpisodeCopy.OnWatch) Icons.Rounded.Check else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = if (accent) colors.onPrimary else LocalContentColor.current,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}
