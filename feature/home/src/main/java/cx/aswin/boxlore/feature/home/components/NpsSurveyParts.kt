package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.feature.home.NpsSurveyCopy
import cx.aswin.boxlore.feature.home.NpsSurveyDefinition

/**
 * Two staggered rows, 0–5 then 6–10, each button sitting between two above it: same-size circles with
 * the same gap in both rows, shrinking to fit a narrow window. "Not likely" sits over the 0 and "Very
 * likely" under the 10.
 */
@Composable
internal fun ScoreGrid(copy: NpsSurveyCopy, onScore: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val button = npsScoreButtonSize(maxWidth)
        Column(Modifier.wrapContentWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BoundLabel(copy.notLikely, Modifier.align(Alignment.Start))
            ScoreRow(0..5, button, onScore, copy)
            ScoreRow(6..10, button, onScore, copy, Modifier.align(Alignment.CenterHorizontally))
            BoundLabel(copy.veryLikely, Modifier.align(Alignment.End).padding(end = (button + NPS_SCORE_GAP) / 2))
        }
    }
}

@Composable
internal fun ScoreRow(scores: IntRange, button: Dp, onScore: (Int) -> Unit, copy: NpsSurveyCopy, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(NPS_SCORE_GAP)) {
        scores.forEach { score ->
            val description = when (score) {
                0 -> "0, ${copy.notLikely}"
                10 -> "10, ${copy.veryLikely}"
                else -> score.toString()
            }
            Button(
                onClick = { onScore(score) },
                modifier = Modifier.size(button).semantics { contentDescription = description }.testTag("nps_score_$score"),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text(score.toString(), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
            }
        }
    }
}

@Composable
internal fun BoundLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Title(text: String, small: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.widthIn(max = 330.dp).semantics { heading() },
        style = if (small) MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp) else MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun Helper(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.widthIn(max = 342.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ScoreChip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = text,
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The idea step's promise, on a seafoam (tertiary) tag; the copy flag can change or hide it. */
@Composable
internal fun IdeaTag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            modifier = Modifier.heightIn(min = 32.dp).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** A rounded, filled answer box with its label inside and a character count; focused, its edge and label turn primary. */
@Composable
@Suppress("LongParameterList")
internal fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, color = colors.onSurface)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().testTag(NPS_ANSWER_TAG),
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        interactionSource = interaction,
        minLines = 3,
        maxLines = 6,
        decorationBox = { inner ->
            Column(
                modifier = Modifier
                    .background(colors.surfaceContainerHighest, RoundedCornerShape(16.dp))
                    .border(
                        if (focused) BorderStroke(2.dp, colors.primary) else BorderStroke(1.dp, colors.outlineVariant),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (focused) colors.primary else colors.onSurfaceVariant)
                Box {
                    if (value.isEmpty()) Text(hint, style = textStyle.copy(color = colors.onSurfaceVariant))
                    inner()
                }
                Text(
                    text = "${value.length} / ${NpsSurveyDefinition.MAX_ANSWER_LENGTH}",
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        },
    )
}

/** Score circles: 52dp, or smaller so six of them and their gaps fit [available]. */
internal fun npsScoreButtonSize(available: Dp): Dp = ((available - NPS_SCORE_GAP * 5) / 6).coerceIn(36.dp, 52.dp)

internal val NPS_SCORE_GAP = 6.dp
