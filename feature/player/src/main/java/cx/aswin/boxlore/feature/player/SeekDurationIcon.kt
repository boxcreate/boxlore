package cx.aswin.boxlore.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

internal data class SeekDurationSpec(
    val seconds: Int,
    val forward: Boolean,
)

data class SeekControlDurations(
    val backwardSeconds: Int = 10,
    val forwardSeconds: Int = 30,
)

@Composable
internal fun seekDurationContentDescription(seconds: Int, forward: Boolean): String =
    pluralStringResource(
        if (forward) R.plurals.seek_forward_seconds else R.plurals.seek_back_seconds,
        seconds,
        seconds,
    )

@Composable
internal fun SeekDurationIcon(
    seconds: Int,
    forward: Boolean,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.085f
            val inset = strokeWidth * 1.35f
            val startAngle = if (forward) -55f else 235f
            val sweepAngle = if (forward) 290f else -290f
            drawArc(
                color = tint,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - inset * 2f,
                    size.height - inset * 2f,
                ),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            val endRadians = Math.toRadians((startAngle + sweepAngle).toDouble())
            val radius = (size.minDimension - inset * 2f) / 2f
            val iconCenter = center
            val arcEnd = androidx.compose.ui.geometry.Offset(
                x = iconCenter.x + radius * cos(endRadians).toFloat(),
                y = iconCenter.y + radius * sin(endRadians).toFloat(),
            )
            val direction = if (forward) 1f else -1f
            val tangent = androidx.compose.ui.geometry.Offset(
                x = -sin(endRadians).toFloat() * direction,
                y = cos(endRadians).toFloat() * direction,
            )
            val perpendicular = androidx.compose.ui.geometry.Offset(-tangent.y, tangent.x)
            val headLength = strokeWidth * 2.25f
            val tip = arcEnd + tangent * headLength * 0.55f
            val base = arcEnd - tangent * headLength * 0.45f
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(
                    base.x + perpendicular.x * headLength * 0.55f,
                    base.y + perpendicular.y * headLength * 0.55f,
                )
                lineTo(
                    base.x - perpendicular.x * headLength * 0.55f,
                    base.y - perpendicular.y * headLength * 0.55f,
                )
                close()
            }
            drawPath(path = arrow, color = tint)
        }

        val numberScale = if (seconds >= 100) 0.21f else 0.27f
        val numberSize = (maxWidth.value * numberScale).coerceIn(6.5f, 12f).sp
        Text(
            text = seconds.toString(),
            color = tint,
            fontSize = numberSize,
            lineHeight = numberSize,
            fontWeight = FontWeight.Bold,
        )
    }
}
