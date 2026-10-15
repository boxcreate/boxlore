package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.delay

/**
 * One full-width main button. An optional way out sits above it: with a note, as a slim one-line
 * pill that says where things stand and what tapping does; otherwise as a centred text button.
 * While Echo is busy the footer fades out but keeps its space.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EchoFooterBar(footer: EchoFooter, onAction: (EchoAction) -> Unit) {
    val alpha by animateFloatAsState(if (footer.visible) 1f else 0f, tween(if (footer.visible) 220 else 120), label = "Echo footer")
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
            .alpha(alpha)
            .then(if (footer.visible) Modifier else Modifier.semantics { hideFromAccessibility() })
            .testTag("echo_footer"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = footer.secondary to footer.note,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = EchoMotion.handoff(),
            contentAlignment = Alignment.BottomCenter,
            label = "Echo secondary action",
        ) { (secondary, note) ->
            when {
                secondary == null -> Box(Modifier.fillMaxWidth())
                note != null -> EchoOptionalPill(footer.questionsLeft, secondary, enabled = footer.visible && footer.secondaryEnabled) { onAction(secondary) }
                else -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = { onAction(secondary) },
                        enabled = footer.visible && footer.secondaryEnabled,
                        modifier = Modifier.heightIn(min = 48.dp).padding(bottom = 4.dp).testTag("echo_secondary"),
                    ) { Text(stringResource(echoActionLabel(secondary)), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        val enabled = footer.visible && footer.primaryEnabled
        val (container, content) = echoMainButtonColors(footer.busy, enabled)
        Button(
            onClick = { onAction(footer.primary) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("echo_primary"),
            shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(28.dp), pressedShape = RoundedCornerShape(18.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = container, disabledContentColor = content),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        ) {
            EchoMainButtonLabel(footer)
        }
    }
}

/** Thinking reads as alive (tonal), not as switched off; the colours ease between the states. */
@Composable
private fun echoMainButtonColors(busy: Boolean, enabled: Boolean): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when {
            busy -> colors.primaryContainer
            enabled -> colors.primary
            else -> colors.onSurface.copy(alpha = 0.12f)
        },
        label = "Echo main button",
    )
    val content by animateColorAsState(
        when {
            busy -> colors.onPrimaryContainer
            enabled -> colors.onPrimary
            else -> colors.onSurface.copy(alpha = 0.38f)
        },
        label = "Echo main button text",
    )
    return container to content
}

@Composable
private fun EchoMainButtonLabel(footer: EchoFooter) {
    AnimatedContent(targetState = footer.busy, transitionSpec = EchoMotion.handoff(), contentAlignment = Alignment.Center, label = "Echo main label") { busy ->
        if (busy) {
            EchoThinkingLabel()
        } else {
            Text(
                stringResource(echoActionLabel(footer.primary)),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Echo at work, inside the main button: a morphing loader and a word that changes every couple of
 * seconds ("Thinking…", "Pondering…"), with a band of colour sweeping across the letters.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EchoThinkingLabel(words: Array<String> = stringArrayResource(R.array.echo_thinking_words)) {
    var index by remember(words.size) { mutableIntStateOf(0) }
    LaunchedEffect(words.size) {
        while (words.size > 1) {
            delay(EchoThinkingWordMillis)
            index = (index + 1) % words.size
        }
    }
    val sweep by rememberInfiniteTransition(label = "Thinking shimmer").animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(EchoThinkingSweepMillis, easing = LinearEasing)),
        label = "Thinking sweep",
    )
    val glint = MaterialTheme.colorScheme.primary
    Row(
        Modifier.testTag("echo_thinking"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LoadingIndicator(Modifier.size(26.dp), color = LocalContentColor.current)
        AnimatedContent(
            targetState = words[index.coerceIn(0, words.lastIndex)],
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 } + fadeIn(tween(160))) togetherWith
                    (slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120)))
            },
            label = "Thinking word",
        ) { word ->
            Text(
                word,
                Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val centre = sweep * size.width
                        val band = size.width * 0.35f
                        drawRect(
                            Brush.horizontalGradient(listOf(Color.Transparent, glint, Color.Transparent), startX = centre - band, endX = centre + band),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
        }
    }
}

private const val EchoThinkingWordMillis = 1_800L
private const val EchoThinkingSweepMillis = 1_400

/**
 * One slim line: "Up to 3 more questions · I'm done →". The count is a ceiling (Echo may stop
 * sooner), so it never overpromises; the last possible question reads "Last question". The whole pill is the button; the
 * note sets expectations and the coloured action says what the tap does.
 */
@Composable
private fun EchoOptionalPill(questionsLeft: Int, action: EchoAction, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val actionColor = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f)
    Surface(
        shape = shape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .heightIn(min = 48.dp)
            .semantics { role = Role.Button }
            .expressiveClickable(enabled = enabled, shape = shape, onClick = onClick)
            .testTag("echo_secondary"),
    ) {
        Row(Modifier.padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                echoNoteText(questionsLeft),
                Modifier.weight(1f).padding(end = 12.dp).testTag("echo_footer_note"),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(stringResource(echoActionLabel(action)), style = MaterialTheme.typography.labelLarge, color = actionColor, maxLines = 1)
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(start = 4.dp).size(18.dp), tint = actionColor)
        }
    }
}

internal fun echoActionLabel(action: EchoAction): Int = when (action) {
    EchoAction.Continue -> R.string.echo_continue
    EchoAction.FindShows -> R.string.echo_find_shows
    EchoAction.FindShowsNow -> R.string.echo_find_shows_now
    EchoAction.SeeShows -> R.string.echo_see_shows
    EchoAction.TryAgain -> R.string.echo_try_again
    EchoAction.PickTopics -> R.string.echo_pick_topics
    EchoAction.TellMore -> R.string.echo_tell_more
    EchoAction.ChangeAnswers -> R.string.echo_change_answers
}

@Composable
private fun echoNoteText(questionsLeft: Int): String = when {
    questionsLeft <= 1 -> stringResource(R.string.echo_note_last_question)
    else -> pluralStringResource(R.plurals.echo_note_questions_left, questionsLeft, questionsLeft)
}
