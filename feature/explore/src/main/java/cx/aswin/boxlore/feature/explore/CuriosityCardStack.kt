package cx.aswin.boxlore.feature.explore

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.playback.PlayerState
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.isActive

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
    val artworkShape = RoundedCornerShape(14.dp)
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val cardShape = MaterialTheme.shapes.extraLarge
    var hidePodcastMetadata by remember(daily.episodeId, daily.explanation) {
        mutableStateOf(false)
    }

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
                    Modifier.expressiveClickable(
                        shape = cardShape,
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
                    .background(Color.Black.copy(alpha = 0.72f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val upperWash = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.34f),
                                accentColor.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.08f, size.height * 0.02f),
                            radius = size.maxDimension * 0.8f
                        )
                        val lowerWash = Brush.radialGradient(
                            colors = listOf(
                                tertiaryColor.copy(alpha = 0.18f),
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
                                tertiaryColor.copy(alpha = 0.75f),
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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 18.dp,
                            bottom = 18.dp
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = daily.question,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = GoogleSansWeight.bold,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.semantics { heading() }
                    )
                    if (!daily.explanation.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = daily.explanation.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.78f),
                            textAlign = TextAlign.Start,
                            onTextLayout = { result ->
                                if (result.hasVisualOverflow) {
                                    hidePodcastMetadata = true
                                }
                            }
                        )
                    }
                }

                if (!hidePodcastMetadata) {
                    Column(
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            bottom = 18.dp
                        )
                    ) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.18f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .expressiveClickable(
                                    enabled = interactive,
                                    onClick = { onAction(CardAction.PodcastClick) }
                                )
                                .padding(vertical = 2.dp)
                        ) {
                            OptimizedImage(
                                url = coverArt,
                                proxyWidth = 128,
                                contentDescription = "Episode artwork",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(
                                        width = 1.dp,
                                        color = accentColor.copy(alpha = 0.45f),
                                        shape = artworkShape
                                    )
                                    .clip(artworkShape)
                            )
                            Text(
                                text = daily.podcastTitle ?: "Podcast",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = GoogleSansWeight.semiBold,
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Open podcast",
                                tint = Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                EpisodePlayButton(
                    isCurrentEpisode = isCurrentEpisode,
                    isPlaying = isCurrentlyPlaying,
                    isLoading = isCurrentlyLoading,
                    accentColor = accentColor,
                    enabled = interactive,
                    onClick = { onAction(CardAction.Play) }
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
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            label = "Skip",
            enabled = enabled,
            onClick = onDismiss,
            modifier = Modifier.weight(1f)
        )
        CardQuickAction(
            icon = Icons.Rounded.Info,
            label = "Info",
            enabled = enabled,
            onClick = onInfo,
            modifier = Modifier.weight(1f)
        )
        CardQuickAction(
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            label = "Queue",
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
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .expressiveClickable(
                enabled = enabled,
                shape = CircleShape,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.52f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = GoogleSansWeight.medium,
            color = Color.White.copy(alpha = 0.58f)
        )
    }
}

@Composable
private fun EpisodePlayButton(
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        isLoading -> "Loading episode"
        isPlaying -> "Pause episode"
        isCurrentEpisode -> "Resume episode"
        else -> "Play episode"
    }
    val iconVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
    val darkAccent = lerp(accentColor, Color.Black, 0.58f)
    val waveColor = lerp(accentColor, Color.White, 0.34f)
    val railShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.975f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlaybackRailPress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(railShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF111016),
                        Color(0xFF15131B),
                        darkAccent
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !isLoading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .padding(horizontal = 20.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(29.dp)
            )
            Spacer(modifier = Modifier.width(15.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            PlaybackWave(
                active = enabled && (isPlaying || isLoading),
                color = waveColor,
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
            )
        }
    }
}

@Composable
private fun PlaybackWave(
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val amplitudeFactor by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "PlaybackWaveAmplitude"
    )
    val phaseAnimation = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (isActive) {
            phaseAnimation.snapTo(0f)
            phaseAnimation.animateTo(
                targetValue = (2f * PI).toFloat(),
                animationSpec = tween(
                    durationMillis = 1_200,
                    easing = LinearEasing
                )
            )
        }
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val startX = strokeWidth / 2f
        val endX = size.width - (strokeWidth / 2f)
        if (endX <= startX) return@Canvas

        val centerY = size.height / 2f
        val amplitude = 3.dp.toPx() * amplitudeFactor
        if (amplitude <= 0.15f) {
            drawLine(
                color = color,
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            return@Canvas
        }

        val wavelength = 36.dp.toPx()
        val path = Path()
        var x = startX
        path.moveTo(
            startX,
            centerY + amplitude * sin(phaseAnimation.value)
        )
        while (x < endX) {
            x = (x + 3f).coerceAtMost(endX)
            val y = centerY + amplitude *
                sin((x / wavelength) * 2f * PI.toFloat() + phaseAnimation.value)
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
