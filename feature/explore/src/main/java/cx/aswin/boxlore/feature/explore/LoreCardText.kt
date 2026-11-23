package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal data class LoreCardTextSpec(
    val headingSp: Int,
    val horizontalPadding: Int,
    val verticalPadding: Int,
    val gap: Int,
)

internal data class LoreMeasuredCardText(val spec: LoreCardTextSpec, val heightPx: Int, val headingLines: Int = 1)

/** Largest first: the 32sp step is for short and medium questions on tall cards only. */
internal val loreTextSpecs = listOf(
    LoreCardTextSpec(32, 24, 20, 16),
    LoreCardTextSpec(28, 24, 18, 16),
    LoreCardTextSpec(28, 20, 12, 12),
    LoreCardTextSpec(26, 20, 12, 12),
    LoreCardTextSpec(24, 20, 8, 12),
)

internal fun TextStyle.loreHeading(spec: LoreCardTextSpec): TextStyle = copy(
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
        val height = heading.size.height + bodyHeight + 2 * spec.verticalPadding.dp.roundToPx()
        // A long question stays at today's sizes: the large step never fits past four lines.
        val eligible = spec.headingSp < LORE_LARGE_HEADING_SP || heading.lineCount <= LORE_LARGE_HEADING_MAX_LINES
        LoreMeasuredCardText(spec, if (eligible) height else Int.MAX_VALUE, heading.lineCount)
    }
}

private const val LORE_LARGE_HEADING_SP = 32

/** The heading size the bar's position rule is measured at. */
private const val LORE_LIFT_RULE_MAX_SP = 28
private const val LORE_LARGE_HEADING_MAX_LINES = 4

/**
 * The text size picked for a card: the first candidate that fits. [overflows] is true only when even
 * the smallest one is taller than the room, so the text scrolls instead of being cut off.
 */
internal data class LoreTextSelection(val measured: LoreMeasuredCardText, val overflows: Boolean) {
    val spec: LoreCardTextSpec get() = measured.spec
    val heightPx: Int get() = measured.heightPx
}

internal fun selectLoreCardText(candidates: List<LoreMeasuredCardText>, heightPx: Int): LoreTextSelection {
    val fitting = candidates.firstOrNull { it.heightPx <= heightPx }
    return LoreTextSelection(fitting ?: candidates.last(), overflows = fitting == null)
}

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
        val selection = selectLoreCardText(candidates, with(density) { maxHeight.roundToPx() })
        val spec = selection.spec
        // Too long for this card even at the smallest size: the text scrolls, never clips.
        val scroll = if (selection.overflows) rememberScrollState() else null
        Column(
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
                .then(if (scroll != null) Modifier.fillMaxHeight().loreScrollFade(scroll).verticalScroll(scroll) else Modifier)
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

/** Fades the edge where more text waits, read in draw so scrolling doesn't recompose the card. */
private fun Modifier.loreScrollFade(scroll: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val fade = LoreScrollFade.toPx().coerceAtMost(size.height / 2)
        if (scroll.canScrollBackward) {
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = fade), blendMode = BlendMode.DstIn)
        }
        if (scroll.canScrollForward) {
            drawRect(Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - fade, endY = size.height), blendMode = BlendMode.DstIn)
        }
    }

private val LoreScrollFade = 16.dp

/**
 * The card's text with its action bar below. When the full text has room to spare, the bar is
 * raised by [lift]; when the text needs that room, the bar stays at the bottom. Decided in one
 * layout pass from the text's measured height, so the bar never alternates between positions.
 */
@Composable
internal fun LoreCardTextWithFooter(
    question: String,
    explanation: String?,
    footerHeight: Dp,
    lift: Dp,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit,
) {
    val headingStyle = MaterialTheme.typography.headlineMedium
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val candidates = remember(question, explanation, headingStyle, bodyStyle, density, maxWidth, measurer) {
            measureLoreCardText(measurer, question, explanation, headingStyle, bodyStyle, density, with(density) { maxWidth.roundToPx() })
        }
        // The bar keeps the rule it always had: raised when the full text fits at 28sp. The larger
        // step is picked inside that room, so it never moves the bar.
        val roomWithLift = with(density) { (maxHeight - footerHeight - lift).roundToPx() }
        val fullText = candidates.first { it.spec.headingSp <= LORE_LIFT_RULE_MAX_SP }
        val appliedLift = if (fullText.heightPx <= roomWithLift) lift else 0.dp
        Column(Modifier.fillMaxSize()) {
            LoreCardText(question = question, explanation = explanation, modifier = Modifier.fillMaxWidth().weight(1f))
            footer()
            Spacer(Modifier.height(appliedLift))
        }
    }
}
