package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout

/** Tablet playback actions reserve their own row, so they cannot cover episode controls. */
@Composable
internal fun LibraryPlaybackLayout(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit, playbackAction: @Composable BoxScope.() -> Unit) {
    if (LocalBoxloreWindowLayout.current.usesTabletLayout) {
        Column(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), content = content)
            Box(Modifier.fillMaxWidth(), content = playbackAction)
        }
    } else {
        Box(modifier) {
            content()
            playbackAction()
        }
    }
}
