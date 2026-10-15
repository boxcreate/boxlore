package cx.aswin.boxlore.core.testing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.boxlorePaneSpec
import kotlin.math.roundToInt

/** Standalone feature fixtures use the shell's contiguous region; players own their fold layout. */
@Composable
internal fun GoldenFoldSafeViewport(window: BoxloreWindowLayout, content: @Composable () -> Unit) {
    val spec = boxlorePaneSpec(window, false)
    val bounds = spec.primary.copy(left = spec.primary.left - spec.railWidthDp)
    CompositionLocalProvider(LocalBoxlorePaneWidth provides bounds.width, LocalBoxlorePaneBounds provides bounds) {
        Layout(content = { Box(Modifier.fillMaxSize().clipToBounds()) { content() } }, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { measurables, constraints ->
            val child = measurables.single().measure(Constraints.fixed((bounds.width * density).roundToInt(), (bounds.height * density).roundToInt()))
            layout(constraints.maxWidth, constraints.maxHeight) {
                child.place((bounds.left * density).roundToInt(), (bounds.top * density).roundToInt())
            }
        }
    }
}
