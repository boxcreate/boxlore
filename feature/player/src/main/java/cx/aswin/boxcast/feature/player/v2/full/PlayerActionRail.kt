package cx.aswin.boxcast.feature.player.v2.full

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.components.AdvancedPlayerControls
import cx.aswin.boxcast.core.designsystem.components.AutoTranscriptState
import cx.aswin.boxcast.core.designsystem.components.ControlStyle

@Composable
fun PlayerActionRail(
    isLiked: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    colorScheme: ColorScheme,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
    overrideColor: Color? = null,
    horizontalArrangement: Arrangement.Horizontal? = null,
    showAddQueueIcon: Boolean = false,
    isQueued: Boolean = false,
    showShareButton: Boolean = true,
    isPlayed: Boolean = false,
    showMarkPlayedButton: Boolean = true,
    onMarkPlayedClick: (() -> Unit)? = null,
    hasChapters: Boolean = false,
    isChaptersLoading: Boolean = false,
    autoTranscriptState: AutoTranscriptState = AutoTranscriptState.NONE,
    autoChaptersState: AutoTranscriptState = AutoTranscriptState.NONE,
    isTranscriptActive: Boolean = false,
    onChaptersClick: (() -> Unit)? = null,
    onTranscriptClick: (() -> Unit)? = null,
) {
    AdvancedPlayerControls(
        isLiked = isLiked,
        isDownloaded = isDownloaded,
        isDownloading = isDownloading,
        colorScheme = colorScheme,
        onLikeClick = onLikeClick,
        onDownloadClick = onDownloadClick,
        onQueueClick = onQueueClick,
        style = ControlStyle.TonalSquircle,
        controlSize = 56.dp,
        overrideColor = overrideColor,
        horizontalArrangement = horizontalArrangement,
        showAddQueueIcon = showAddQueueIcon,
        isQueued = isQueued,
        showShareButton = showShareButton,
        isPlayed = isPlayed,
        showMarkPlayedButton = showMarkPlayedButton,
        onMarkPlayedClick = onMarkPlayedClick,
        hasChapters = hasChapters,
        isChaptersLoading = isChaptersLoading,
        autoTranscriptState = autoTranscriptState,
        autoChaptersState = autoChaptersState,
        isTranscriptActive = isTranscriptActive,
        onChaptersClick = onChaptersClick,
        onTranscriptClick = onTranscriptClick,
        modifier = modifier.fillMaxWidth(),
    )
}
