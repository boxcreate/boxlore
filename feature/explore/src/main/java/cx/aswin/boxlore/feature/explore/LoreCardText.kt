package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal data class LoreCardTextSpec(
    val headingSp: Int,
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val gap: Int,
)

internal data class LoreMeasuredCardText(val spec: LoreCardTextSpec, val heightPx: Int)

private val loreTextSpecs = listOf(
    LoreCardTextSpec(28, 24, 18, 16),
    LoreCardTextSpec(28, 20, 12, 12),
    LoreCardTextSpec(26, 20, 12, 12),
    LoreCardTextSpec(24, 20, 8, 12),
)

private fun TextStyle.loreHeading(spec: LoreCardTextSpec): TextStyle = copy(
    fontSize = spec.headingSp.sp,
    lineHeight = (spec.headingSp + 4).sp,
    fontWeight = GoogleSansWeight.bold,
)

internal fun measureLoreCardText(
    measurer: TextMeasurer,
    question: String,
    explanation: String?,
    headingStyle: TextStyle,
    bodyStyle: TextStyle,
    density: Density,
    widthPx: Int,
): List<LoreMeasuredCardText> = loreTextSpecs.map { spec ->
    with(density) {
        val width = (widthPx - 2 * spec.horizontalPadding.dp.roundToPx()).coerceAtLeast(1)
        val constraints = Constraints(maxWidth = width)
        val heading = measurer.measure(AnnotatedString(question), headingStyle.loreHeading(spec), constraints = constraints)
        val bodyHeight = if (explanation.isNullOrBlank()) {
            0
        } else {
            measurer.measure(AnnotatedString(explanation), bodyStyle, constraints = constraints).size.height + spec.gap.dp.roundToPx()
        }
        LoreMeasuredCardText(spec, heading.size.height + bodyHeight + 2 * spec.verticalPadding.dp.roundToPx())
    }
}

internal fun selectLoreCardText(candidates: List<LoreMeasuredCardText>, heightPx: Int): LoreMeasuredCardText =
    candidates.firstOrNull { it.heightPx <= heightPx } ?: candidates.last()

@Composable
internal fun LoreCardText(question: String, explanation: String?, modifier: Modifier = Modifier) {
    val headingStyle = MaterialTheme.typography.headlineMedium
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val candidates = remember(question, explanation, headingStyle, bodyStyle, density, maxWidth, measurer) {
            measureLoreCardText(measurer, question, explanation, headingStyle, bodyStyle, density, with(density) { maxWidth.roundToPx() })
        }
        val spec = selectLoreCardText(candidates, with(density) { maxHeight.roundToPx() }).spec
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                .padding(horizontal = spec.horizontalPadding.dp, vertical = spec.verticalPadding.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = question,
                style = headingStyle.loreHeading(spec),
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            if (!explanation.isNullOrBlank()) {
                Spacer(Modifier.height(spec.gap.dp))
                Text(text = explanation, style = bodyStyle, color = Color.White.copy(alpha = 0.84f))
            }
        }
    }
}
