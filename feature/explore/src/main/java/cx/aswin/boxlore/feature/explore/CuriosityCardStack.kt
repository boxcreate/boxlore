package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.playback.PlayerState
import kotlin.math.roundToInt

sealed interface CardAction {
    data object Dismiss : CardAction
    data object Queue : CardAction
    data object Play : CardAction
    data object Click : CardAction
    data object PodcastClick : CardAction
}

@Composable
fun CuriosityCardStack(
    questions: List<LearnCuriosityCard>,
    swipeState: SwipeableCardState,
    playerState: PlayerState,
    onCardAction: (CardAction, LearnCuriosityCard) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    artworkAccentColors: Map<List<String>, Int> = emptyMap()
) {
    if (questions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No more curiosities for today!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val swipeThresholdPx = with(LocalDensity.current) { LoreSwipeThresholdDp.dp.toPx() }
    val swipeProgress = remember(swipeState, swipeThresholdPx) {
        derivedStateOf { loreSwipeProgress(swipeState.offset.value.x, swipeThresholdPx) }
    }
    val visibleCards = questions.take(3)

    Box(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 520.dp)
            .padding(bottom = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        visibleCards.asReversed().forEach { card ->
            key(card.episodeId) {
                val depth = visibleCards.indexOf(card)
                val isActive = depth == 0
                val cardModifier = deckCardModifier(card, depth, swipeProgress, swipeState, swipeThresholdPx)
                val isCurrent = playerState.currentEpisode?.id == card.episodeId

                CuriosityCardContent(
                    daily = card,
                    isCurrentEpisode = isCurrent,
                    isCurrentlyPlaying = isCurrent && playerState.isPlaying,
                    isCurrentlyLoading = isCurrent && playerState.isLoading,
                    accentColor = artworkAccentColors[card.artworkSources]?.let { Color(it) } ?: accentColor,
                    interactive = isActive && swipeState.phase != LoreSwipePhase.Exiting,
                    modifier = cardModifier,
                    onAction = { action ->
                        if (isActive && swipeState.phase != LoreSwipePhase.Exiting) {
                            onCardAction(action, card)
                        }
                    }
                )
            }
        }
    }
}

private fun BoxScope.deckCardModifier(
    card: LearnCuriosityCard,
    depth: Int,
    swipeProgress: State<Float>,
    swipeState: SwipeableCardState,
    swipeThresholdPx: Float,
): Modifier = when (depth) {
    2 ->
        Modifier.matchParentSize().graphicsLayer {
            val progress = swipeProgress.value
            translationY = (24f - 10f * progress) * density
            scaleX = 0.93f + 0.035f * progress
            scaleY = scaleX
            rotationZ = 1.8f * (1f - progress)
        }
    1 ->
        Modifier.matchParentSize().graphicsLayer {
            val progress = swipeProgress.value
            translationY = (13f - 11f * progress) * density
            scaleX = 0.965f + 0.025f * progress
            scaleY = scaleX
            rotationZ = -1.15f * (1f - progress)
        }
    else ->
        Modifier
            .fillMaxSize()
            .offset {
                IntOffset(
                    swipeState.offset.value.x.roundToInt(),
                    0
                )
            }
            .graphicsLayer {
                rotationZ = (swipeState.offset.value.x / 180f)
                    .coerceIn(-2.5f, 2.5f)
                cameraDistance = 12f * density
            }
            .pointerInput(card.episodeId) {
                detectHorizontalDragGestures(
                    onDragStart = { swipeState.beginDrag() },
                    onDragEnd = {
                        val offsetX = swipeState.offset.value.x
                        if (offsetX > swipeThresholdPx) {
                            swipeState.swipe(SwipeDirection.Right)
                        } else if (offsetX < -swipeThresholdPx) {
                            swipeState.swipe(SwipeDirection.Left)
                        } else {
                            swipeState.reset()
                        }
                    },
                    onDragCancel = swipeState::reset,
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeState.drag(
                            androidx.compose.ui.geometry.Offset(
                                x = dragAmount,
                                y = 0f
                            )
                        )
                    }
                )
            }
}

@Composable
private fun CuriosityCardContent(
    daily: LearnCuriosityCard,
    isCurrentEpisode: Boolean,
    isCurrentlyPlaying: Boolean,
    isCurrentlyLoading: Boolean,
    accentColor: Color,
    interactive: Boolean = true,
    onAction: (CardAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverArt = daily.artworkSources.firstOrNull().orEmpty()
    val cardShape = MaterialTheme.shapes.extraLarge
    val cardInteractionSource = remember { MutableInteractionSource() }

    OutlinedCard(
        shape = cardShape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Black
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = cardInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { onAction(CardAction.Click) }
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            OptimizedImage(
                url = coverArt,
                proxyWidth = 320,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.24f
                        scaleY = 1.24f
                    }
                    .blur(
                        radius = 64.dp,
                        edgeTreatment = BlurredEdgeTreatment.Unbounded
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val upperWash = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.22f),
                                accentColor.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.08f, size.height * 0.02f),
                            radius = size.maxDimension * 0.8f
                        )
                        val lowerWash = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(size.width, size.height),
                            radius = size.maxDimension * 0.7f
                        )
                        onDrawBehind {
                            drawRect(upperWash)
                            drawRect(lowerWash)
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.9f),
                                accentColor.copy(alpha = 0.75f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                CardQuickActions(
                    enabled = interactive,
                    onDismiss = { onAction(CardAction.Dismiss) },
                    onInfo = { onAction(CardAction.Click) },
                    onQueue = { onAction(CardAction.Queue) },
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp
                    )
                )
                LoreCardText(
                    question = daily.question,
                    explanation = daily.explanation,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                LoreCardFooter(
                    daily = daily,
                    isCurrentEpisode = isCurrentEpisode,
                    isPlaying = isCurrentlyPlaying,
                    isLoading = isCurrentlyLoading,
                    accentColor = accentColor,
                    enabled = interactive,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun CardQuickActions(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardQuickAction(
            icon = Icons.Rounded.Close,
            label = stringResource(R.string.lore_action_skip),
            enabled = enabled,
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        CardQuickAction(
            icon = Icons.Rounded.Info,
            label = stringResource(R.string.lore_action_details),
            enabled = enabled,
            onClick = onInfo,
            modifier = Modifier.weight(1f)
        )
        CardQuickAction(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            label = stringResource(R.string.lore_action_add_to_queue),
            enabled = enabled,
            onClick = onQueue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CardQuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .loreControlClickable(
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.76f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.medium,
            color = Color.White.copy(alpha = 0.86f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoreCardFooter(
    daily: LearnCuriosityCard,
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onAction: (CardAction) -> Unit,
) {
    val artworkShape = RoundedCornerShape(12.dp)
    val podcastTitle = daily.podcastTitle ?: stringResource(R.string.lore_podcast_fallback)
    val podcastDescription = stringResource(R.string.lore_open_podcast, podcastTitle)
    val windowSize = LocalWindowInfo.current.containerSize
    val compactSpacing = with(LocalDensity.current) {
        windowSize.width.toDp() < 360.dp || windowSize.height.toDp() < 720.dp
    }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = accentColor.copy(alpha = 0.24f), modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = if (compactSpacing) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = podcastDescription
                    }
                    .loreControlClickable(enabled = enabled, shape = artworkShape) {
                        onAction(CardAction.PodcastClick)
                    }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OptimizedImage(
                    url = daily.artworkSources.firstOrNull().orEmpty(),
                    proxyWidth = 128,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(artworkShape),
                )
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.size(16.dp),
                )
            }
            EpisodePlayButton(
                isCurrentEpisode = isCurrentEpisode,
                isPlaying = isPlaying,
                isLoading = isLoading,
                accentColor = accentColor,
                enabled = enabled,
                compactSpacing = compactSpacing,
                onClick = { onAction(CardAction.Play) },
            )
        }
    }
}

@Composable
private fun EpisodePlayButton(
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    enabled: Boolean,
    compactSpacing: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(
        when {
            isLoading -> R.string.lore_loading_episode
            isPlaying -> R.string.lore_pause_episode
            isCurrentEpisode -> R.string.lore_resume_episode
            else -> R.string.lore_play_episode
        }
    )
    Box(
        modifier = Modifier.size(if (compactSpacing) 56.dp else 64.dp)
            .loreControlClickable(enabled = enabled && !isLoading, shape = CircleShape, onClick = onClick)
            .background(lerp(accentColor, Color.Black, 0.58f))
            .border(1.dp, accentColor.copy(alpha = 0.6f), CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compactSpacing) 30.dp else 34.dp),
            )
        }
    }
}
