package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The deck: the show on top as a physical card, the rest of the deck peeking out underneath, and
 * the clip button on its own below (the card leans under a finger, so nothing on it is a button).
 * A card leaves the way the listener answered it: flung right for Sounds good, left for Not for me,
 * up for I already listen, down for Not sure; the next one rises from the deck.
 */
@Composable
internal fun EchoDeckCard(deck: EchoDeck, metrics: EchoLayoutMetrics, preview: EchoShowPreview?, modifier: Modifier = Modifier) {
    // After the last answer the deck hands off to the wait; the last card stays put while it fades.
    val position = deck.reactions.size.coerceAtMost(deck.cards.lastIndex)
    val behind = (deck.cards.size - position - 1).coerceIn(0, EchoDeckMaxBehind)
    // Each card is revealed the first time it comes up; Back brings earlier ones back face up.
    var revealedUpTo by remember { mutableIntStateOf(-1) }
    val clip = deck.cards.getOrNull(position)?.let(EchoDeckLogic::clip)
    val status = preview?.status?.value?.takeIf { clip != null && it.showId == clip.showId }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f, fill = false).padding(bottom = EchoDeckPeek * EchoDeckMaxBehind), contentAlignment = Alignment.TopCenter) {
            for (layer in behind downTo 1) EchoDeckBackCard(layer, Modifier.matchParentSize())
            AnimatedContent(
                targetState = position,
                transitionSpec = {
                    if (targetState > initialState) echoDeckForward() else echoDeckBackward()
                },
                contentAlignment = Alignment.TopCenter,
                label = "Echo deck card",
            ) { index ->
                val card = deck.cards.getOrNull(index) ?: return@AnimatedContent
                val fling by transition.animateFloat({ tween(EchoDeckFlingMillis) }, label = "Card fling") { if (it == EnterExitState.PostExit) 1f else 0f }
                val throwTo = echoDeckThrow(deck.reactions.getOrNull(index)?.reaction)
                val reveal = remember {
                    when {
                        index <= revealedUpTo -> null
                        revealedUpTo < 0 -> EchoCardReveal.Deal
                        else -> EchoCardReveal.Flip
                    }
                }
                LaunchedEffect(Unit) { revealedUpTo = maxOf(revealedUpTo, index) }
                EchoHoloCard(
                    card,
                    index,
                    deck.cards.size,
                    metrics.artworkSize,
                    reveal = reveal,
                    modifier = Modifier.graphicsLayer {
                        translationX = throwTo.x * fling * size.width * 1.15f
                        translationY = throwTo.y * fling * size.height * 0.7f
                        rotationZ = throwTo.turn * fling
                    },
                )
            }
        }
        // Space stays reserved on cards without a clip, so the card keeps its size from one to the next.
        val showClip = clip != null && preview != null && status?.failed != true
        EchoClipButton(
            status,
            enabled = showClip,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp).alpha(if (showClip) 1f else 0f),
        ) { preview?.toggle() }
    }
}

/** Where a card goes when answered, as shares of its size, and how far it turns. */
private data class EchoDeckThrow(val x: Float, val y: Float, val turn: Float)

private fun echoDeckThrow(reaction: String?): EchoDeckThrow = when (reaction) {
    EchoDeckReaction.Like.wire -> EchoDeckThrow(1f, -0.1f, 14f)
    EchoDeckReaction.Dislike.wire -> EchoDeckThrow(-1f, -0.1f, -14f)
    EchoDeckReaction.Listen.wire -> EchoDeckThrow(0f, -1f, 0f)
    EchoDeckReaction.Unknown.wire -> EchoDeckThrow(0f, 0.6f, 4f)
    else -> EchoDeckThrow(0f, 0f, 0f)
}

private const val EchoDeckFlingMillis = 300
private const val EchoDeckMaxBehind = 2
private val EchoDeckPeek = 8.dp

private fun echoDeckForward(): ContentTransform = ContentTransform(
    // The next card turns over on the deck (its own reveal) while the answered one is thrown on top of it.
    targetContentEnter = fadeIn(tween(90)),
    initialContentExit = fadeOut(tween(160, delayMillis = EchoDeckFlingMillis - 160)),
    targetContentZIndex = -1f,
    sizeTransform = SizeTransform(clip = false),
)

private fun echoDeckBackward(): ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(spring(dampingRatio = 0.75f, stiffness = 420f), initialScale = 1.06f) + fadeIn(tween(180)),
    initialContentExit = scaleOut(tween(160), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)) + fadeOut(tween(160)),
    targetContentZIndex = 1f,
    sizeTransform = SizeTransform(clip = false),
)

/** A card further down the deck, a little smaller and lower each layer. */
@Composable
private fun EchoDeckBackCard(layer: Int, modifier: Modifier) {
    EchoHoloBack(
        modifier.graphicsLayer {
            translationY = (EchoDeckPeek * layer).toPx()
            scaleX = 1f - 0.06f * layer
            transformOrigin = TransformOrigin(0.5f, 1f)
            shadowElevation = (4.dp * (EchoDeckMaxBehind + 1 - layer)).toPx()
            shape = EchoHoloShape
            clip = true
        },
    )
}

/** Play, a squiggly spinner while it buffers, then pause inside a squiggly ring that fills over the clip. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EchoClipButton(status: EchoPreviewStatus?, enabled: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val playing = status?.playing == true
    FilledTonalButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).then(if (enabled) Modifier.testTag("echo_deck_clip") else Modifier.clearAndSetSemantics { }),
        contentPadding = PaddingValues(start = 12.dp, end = 20.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            when {
                status?.buffering == true -> CircularWavyProgressIndicator(Modifier.fillMaxSize())
                playing -> {
                    CircularWavyProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxSize())
                    Icon(Icons.Rounded.Pause, null, Modifier.size(14.dp))
                }
                else -> Icon(Icons.Rounded.PlayArrow, null, Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(if (playing) R.string.echo_deck_pause else R.string.echo_deck_play), style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private val EchoDeckRows = listOf(
    listOf(EchoDeckReaction.Dislike, EchoDeckReaction.Like),
    listOf(EchoDeckReaction.Listen, EchoDeckReaction.Unknown),
)

/**
 * A new card ignores taps until it has turned face up, so a quick double tap can't answer a card no
 * one has read: longer for the first card, which is dealt, than for the ones that flip in place.
 */
private fun echoDeckTapLock(position: Int): Long = if (position == 0) 460L else 300L

/**
 * Four big connected buttons in the footer's place, the two that matter most on top. Only the
 * group's outer corners are round; a pressed button widens a little and its corners soften.
 */
@Composable
internal fun EchoDeckReactions(position: Int, metrics: EchoLayoutMetrics, onReact: (EchoDeckReaction) -> Unit) {
    var ready by remember(position) { mutableStateOf(false) }
    LaunchedEffect(position) {
        delay(echoDeckTapLock(position))
        ready = true
    }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp)
            .testTag("echo_deck_reactions"),
        verticalArrangement = Arrangement.spacedBy(EchoLayout.GroupGap),
    ) {
        EchoDeckRows.forEachIndexed { row, reactions ->
            Row(Modifier.fillMaxWidth().height(metrics.rowHeight), horizontalArrangement = Arrangement.spacedBy(EchoLayout.GroupGap)) {
                reactions.forEachIndexed { column, reaction ->
                    EchoReactionButton(reaction, echoTileCorners(row, column, EchoDeckRows.size, reactions.size, selected = false)) {
                        if (ready) {
                            ready = false
                            onReact(reaction)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.EchoReactionButton(reaction: EchoDeckReaction, corners: EchoCorners, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val weight by animateFloatAsState(if (pressed) 1.12f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "Reaction width")
    val (container, content) = when (reaction) {
        EchoDeckReaction.Like -> colors.primary to colors.onPrimary
        EchoDeckReaction.Dislike -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.surfaceContainerHighest to colors.onSurface
    }
    val style = MaterialTheme.typography.titleMedium
    Button(
        onClick = onClick,
        modifier = Modifier.weight(weight).fillMaxHeight().testTag("echo_deck_${reaction.wire}"),
        shapes = ButtonDefaults.shapes(
            shape = RoundedCornerShape(topStart = corners.topStart, topEnd = corners.topEnd, bottomEnd = corners.bottomEnd, bottomStart = corners.bottomStart),
            pressedShape = RoundedCornerShape(EchoReactionPressedCorner),
        ),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 12.dp),
        interactionSource = press,
    ) {
        Icon(echoReactionIcon(reaction), null, Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(echoReactionLabel(reaction)),
            style = style,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
        )
    }
}

private val EchoReactionPressedCorner: Dp = 14.dp

private fun echoReactionIcon(reaction: EchoDeckReaction): ImageVector = when (reaction) {
    EchoDeckReaction.Like -> Icons.Rounded.ThumbUp
    EchoDeckReaction.Dislike -> Icons.Rounded.ThumbDown
    EchoDeckReaction.Listen -> Icons.Rounded.Headphones
    EchoDeckReaction.Unknown -> Icons.Rounded.QuestionMark
}

internal fun echoReactionLabel(reaction: EchoDeckReaction): Int = when (reaction) {
    EchoDeckReaction.Like -> R.string.echo_deck_like
    EchoDeckReaction.Dislike -> R.string.echo_deck_dislike
    EchoDeckReaction.Listen -> R.string.echo_deck_listen
    EchoDeckReaction.Unknown -> R.string.echo_deck_unknown
}

/** How long the "added to your picks" note stays up. */
private const val EchoPickedNoteMillis = 1_800L

/**
 * A brief note when a positive answer picks the show ("Serial added to your picks"). It slides in
 * near the top, clear of the next card and its clip button, and is read out once by screen readers.
 * Going back never shows it.
 */
@Composable
internal fun EchoPickedNote(deck: EchoDeck, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<String?>(null) }
    var seen by remember { mutableIntStateOf(deck.reactions.size) }
    LaunchedEffect(deck.reactions.size) {
        val grew = deck.reactions.size > seen
        seen = deck.reactions.size
        val last = deck.reactions.lastOrNull()
        if (!grew || last == null || !EchoDeckLogic.picks(last.reaction)) {
            shown = null
            return@LaunchedEffect
        }
        shown = last.title
        delay(EchoPickedNoteMillis)
        shown = null
    }
    AnimatedVisibility(
        visible = shown != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { -it } + fadeIn(tween(140)),
        exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(160)),
    ) {
        val title = shown.orEmpty()
        Surface(
            Modifier.padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("echo_picked_note"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.inversePrimary)
                Text(
                    stringResource(R.string.echo_deck_added, title),
                    Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
