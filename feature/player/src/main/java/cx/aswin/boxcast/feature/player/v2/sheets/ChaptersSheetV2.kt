package cx.aswin.boxcast.feature.player.v2.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cx.aswin.boxcast.core.model.Chapter
import cx.aswin.boxcast.feature.player.ChaptersSheetContent
import kotlinx.coroutines.flow.Flow

@Composable
fun ChaptersSheetV2(
    chapters: List<Chapter>,
    positionFlow: Flow<Long>,
    colorScheme: ColorScheme,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    chaptersUrl: String? = null,
    isChaptersLoading: Boolean = false,
    hasTranscript: Boolean = false,
    onGenerateChapters: () -> Unit = {},
) {
    PlayerSheetScaffold(
        colorScheme = colorScheme,
        modifier = modifier.fillMaxWidth(),
    ) {
        ChaptersSheetContent(
            chapters = chapters,
            positionFlow = positionFlow,
            colorScheme = colorScheme,
            onSeek = onSeek,
            onClose = onClose,
            chaptersUrl = chaptersUrl,
            isChaptersLoading = isChaptersLoading,
            hasTranscript = hasTranscript,
            onGenerateChapters = onGenerateChapters,
        )
    }
}
