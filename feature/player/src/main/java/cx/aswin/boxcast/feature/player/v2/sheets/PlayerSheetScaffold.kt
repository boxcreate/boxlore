package cx.aswin.boxcast.feature.player.v2.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.feature.player.v2.chrome.PlayerChromeGeometry
import cx.aswin.boxcast.feature.player.v2.chrome.playerSheetShape

/**
 * Shared overlay chrome for player V2 bottom sheets: squircle top corners,
 * drag-handle pill, and [ColorScheme.surfaceContainerHigh] background.
 */
@Composable
fun PlayerSheetScaffold(
    colorScheme: ColorScheme,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = playerSheetShape(
        topStart = PlayerChromeGeometry.SheetOverlayTopCorner,
        topEnd = PlayerChromeGeometry.SheetOverlayTopCorner,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surfaceContainerHigh,
        shape = shape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 4.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            content()
        }
    }
}
