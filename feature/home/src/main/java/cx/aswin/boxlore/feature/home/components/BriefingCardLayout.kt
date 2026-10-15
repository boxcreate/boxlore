package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
internal fun BriefingCardLayout(tablet: Boolean, cover: @Composable () -> Unit, body: @Composable () -> Unit) {
    if (!tablet) {
        Column(Modifier.animateContentSize()) {
            cover()
            body()
        }
        return
    }
    Layout(content = {
            cover()
            body()
        }, modifier = Modifier.fillMaxWidth().animateContentSize()) { measurables, constraints ->
        val coverWidth = (constraints.maxWidth * 0.34f).toInt().coerceIn(168.dp.roundToPx(), 260.dp.roundToPx())
        val bodyWidth = (constraints.maxWidth - coverWidth).coerceAtLeast(1)
        val details = measurables[1].measure(Constraints.fixedWidth(bodyWidth))
        val height = maxOf(220.dp.roundToPx(), details.height)
        val artwork = measurables[0].measure(Constraints.fixed(coverWidth, height))
        layout(constraints.maxWidth, height) {
            artwork.placeRelative(0, 0)
            details.placeRelative(coverWidth, (height - details.height) / 2)
        }
    }
}
