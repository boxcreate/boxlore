package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreVisibleWindowSize
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.artworkDeepShade
import cx.aswin.boxlore.core.designsystem.theme.tabletInputFeedback
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
    // A fourth card waits hidden behind the deck and fades into third place during a swipe.
    val visibleCards = questions.take(LoreDeckDepth + 1)

    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    Box(
        modifier = modifier
            .boxloreCardWidth(BoxloreCardFamily.Reading, maximumWidthDp = 520f)
            .then(if (tablet) Modifier.wrapContentHeight(Alignment.Top).heightIn(max = 640.dp) else Modifier)
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
                            routeCardAction(action, card, swipeState, onCardAction)
                        }
                    }
                )
            }
        }
        LoreSwipeFeedback(
            swipeState = swipeState,
            thresholdPx = swipeThresholdPx,
            accentColor = artworkAccentColors[questions.first().artworkSources]?.let { Color(it) } ?: accentColor,
        )
    }
}

/** Skip and Add to queue leave the deck the same way a swipe does; other actions run directly. */
private fun routeCardAction(
    action: CardAction,
    card: LearnCuriosityCard,
    swipeState: SwipeableCardState,
    onCardAction: (CardAction, LearnCuriosityCard) -> Unit,
) {
    when (action) {
        CardAction.Dismiss -> swipeState.swipe(SwipeDirection.Left)
        CardAction.Queue -> swipeState.swipe(SwipeDirection.Right)
        else -> onCardAction(action, card)
    }
}

private fun BoxScope.deckCardModifier(
    card: LearnCuriosityCard,
    depth: Int,
    swipeProgress: State<Float>,
    swipeState: SwipeableCardState,
    swipeThresholdPx: Float,
): Modifier = if (depth > 0) {
    // The waiting card is hidden, so screen readers skip it until it joins the deck.
    val hidden = if (depth > LoreDeckDepth) Modifier.clearAndSetSemantics {} else Modifier
    Modifier.matchParentSize().then(hidden).graphicsLayer {
        val pose = loreDeckPose(depth, swipeProgress.value)
        translationY = pose.translationYDp * density
        scaleX = pose.scale
        scaleY = pose.scale
        rotationZ = pose.rotation
        alpha = pose.alpha
    }
} else {
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

/** The card's solid ground: a deep, still clearly coloured shade of the cover's colour. */
internal fun loreCardColor(accent: Color): Color = artworkDeepShade(accent, tone = 18.0)

/** Button segments sit a step lighter than the card, in the same colour. */
internal fun loreSegmentColor(accent: Color): Color = artworkDeepShade(accent, tone = 28.0)

/** Black or white, whichever contrasts more with the accent-filled Play button. */
internal fun loreOnAccent(accent: Color): Color {
    val l = accent.luminance()
    val onWhite = 1.05f / (l + 0.05f)
    val onBlack = (l + 0.05f) / 0.05f
    return if (onBlack >= onWhite) Color.Black else Color.White
}

/**
 * A Lore card: where the curiosity comes from (show and episode, which open the podcast), the full
 * question and explanation, then one action bar matching the swipe directions, Skip on the left,
 * Play in the middle and Add to queue on the right. Tapping the card or Details opens the episode.
 */
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
    val cardShape = MaterialTheme.shapes.extraLarge
    val cardInteractionSource = remember { MutableInteractionSource() }
    val pane = cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds.current
    val windowSize = if (cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout.current.usesTabletLayout && pane != null) {
        androidx.compose.ui.unit.DpSize(pane.width.dp, pane.height.dp)
    } else {
        boxloreVisibleWindowSize()
    }
    val compactSpacing = windowSize.width < 360.dp || windowSize.height < 720.dp

    OutlinedCard(
        shape = cardShape,
        colors = CardDefaults.outlinedCardColors(containerColor = loreCardColor(accentColor)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .then(if (interactive) Modifier.testTag("lore_active_card") else Modifier)
            .then(
                if (interactive) {
                    Modifier.tabletInputFeedback(cardInteractionSource, cardShape).clickable(
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
        Box(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val upperGlow = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.28f), accentColor.copy(alpha = 0.07f), Color.Transparent),
                            center = Offset(size.width * 0.12f, 0f),
                            radius = size.maxDimension * 0.75f
                        )
                        val lowerGlow = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.12f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height),
                            radius = size.maxDimension * 0.55f
                        )
                        onDrawBehind {
                            drawRect(upperGlow)
                            drawRect(lowerGlow)
                        }
                    }
            )
            Column(Modifier.fillMaxSize()) {
                LoreCardSource(
                    daily = daily,
                    accentColor = accentColor,
                    enabled = interactive,
                    compactSpacing = compactSpacing,
                    onPodcast = { onAction(CardAction.PodcastClick) },
                    onDetails = { onAction(CardAction.Click) },
                )
                LoreCardTextWithFooter(
                    question = daily.question,
                    explanation = daily.explanation,
                    footerHeight = loreActionBarHeight(compactSpacing) + loreActionBarPadding(compactSpacing) * 2,
                    lift = if (compactSpacing) 6.dp else 10.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    LoreCardActionBar(
                        isCurrentEpisode = isCurrentEpisode,
                        isPlaying = isCurrentlyPlaying,
                        isLoading = isCurrentlyLoading,
                        accentColor = accentColor,
                        enabled = interactive,
                        compactSpacing = compactSpacing,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

/**
 * Where the curiosity comes from, as a connected pair matching the action bar: a wide segment with
 * the show's cover and names (opens the podcast) joined to a square Details segment.
 */
@Composable
private fun LoreCardSource(
    daily: LearnCuriosityCard,
    accentColor: Color,
    enabled: Boolean,
    compactSpacing: Boolean,
    onPodcast: () -> Unit,
    onDetails: () -> Unit,
) {
    val height = loreBarHeight(compactSpacing)
    val outer = height / 2
    val container = loreSegmentColor(accentColor)
    val podcastTitle = daily.podcastTitle ?: stringResource(R.string.lore_podcast_fallback)
    val podcastDescription = stringResource(R.string.lore_open_podcast, podcastTitle)
    val detailsLabel = stringResource(R.string.lore_action_details)
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = if (compactSpacing) 8.dp else 12.dp)
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .semantics(mergeDescendants = true) { contentDescription = podcastDescription }
                .loreControlClickable(
                    enabled = enabled,
                    shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = ActionBarInnerCorner, bottomEnd = ActionBarInnerCorner),
                    onClick = onPodcast,
                )
                .background(container)
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OptimizedImage(
                url = daily.artworkSources.firstOrNull().orEmpty(),
                fallbackUrls = daily.artworkSources.drop(1),
                proxyWidth = 128,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Concentric with the segment's round end.
                modifier = Modifier.size(height - 12.dp).clip(RoundedCornerShape(outer - 6.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = Color.White.copy(alpha = 0.94f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = daily.episodeTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            modifier = Modifier.width(height + 4.dp).fillMaxHeight()
                .semantics { contentDescription = detailsLabel }
                .loreControlClickable(
                    enabled = enabled,
                    shape = RoundedCornerShape(topStart = ActionBarInnerCorner, bottomStart = ActionBarInnerCorner, topEnd = outer, bottomEnd = outer),
                    onClick = onDetails,
                )
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = accentColor.takeIf { loreOnAccent(it) == Color.Black } ?: Color.White.copy(alpha = 0.94f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** The source row's height. */
private fun loreBarHeight(compactSpacing: Boolean) = if (compactSpacing) 48.dp else 52.dp

/** The action bar is taller than the source row, so Play and its neighbours are easy targets. */
private fun loreActionBarHeight(compactSpacing: Boolean) = if (compactSpacing) 52.dp else 60.dp

private fun loreActionBarPadding(compactSpacing: Boolean) = if (compactSpacing) 8.dp else 10.dp

private val ActionBarInnerCorner = 8.dp

/**
 * The card's actions as one connected Material 3 button group, in swipe order: an icon-only Skip,
 * Play filling the rest of the bar in the cover colour, and an icon-only Add to queue. Outer ends
 * are fully round, inner joins tight.
 */
@Composable
private fun LoreCardActionBar(
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    enabled: Boolean,
    compactSpacing: Boolean,
    onAction: (CardAction) -> Unit,
) {
    val height = loreActionBarHeight(compactSpacing)
    val outer = height / 2
    val sideContainer = loreSegmentColor(accentColor)
    // Skip and Add to queue are icon-only ends; Play takes the rest of the bar.
    val sideWidth = height + 12.dp
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = loreActionBarPadding(compactSpacing))
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        LoreActionSegment(
            icon = Icons.Rounded.Close,
            description = stringResource(R.string.lore_action_skip),
            container = sideContainer,
            shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = ActionBarInnerCorner, bottomEnd = ActionBarInnerCorner),
            width = sideWidth,
            enabled = enabled,
            onClick = { onAction(CardAction.Dismiss) },
        )
        LorePlaySegment(
            isCurrentEpisode = isCurrentEpisode,
            isPlaying = isPlaying,
            isLoading = isLoading,
            accentColor = accentColor,
            enabled = enabled,
            onClick = { onAction(CardAction.Play) },
        )
        LoreActionSegment(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            description = stringResource(R.string.lore_action_add_to_queue),
            container = sideContainer,
            shape = RoundedCornerShape(topStart = ActionBarInnerCorner, bottomStart = ActionBarInnerCorner, topEnd = outer, bottomEnd = outer),
            width = sideWidth,
            enabled = enabled,
            onClick = { onAction(CardAction.Queue) },
        )
    }
}

/** An icon-only end segment; its full action is announced to screen readers. */
@Composable
private fun LoreActionSegment(
    icon: ImageVector,
    description: String,
    container: Color,
    shape: androidx.compose.ui.graphics.Shape,
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.width(width).fillMaxHeight()
            .semantics { contentDescription = description }
            .loreControlClickable(enabled = enabled, shape = shape, onClick = onClick)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.94f), modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RowScope.LorePlaySegment(
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(
        when {
            isLoading -> R.string.lore_loading_episode
            isPlaying -> R.string.lore_pause_episode
            isCurrentEpisode -> R.string.lore_resume_episode
            else -> R.string.lore_play_episode
        }
    )
    val label = stringResource(
        when {
            isPlaying -> R.string.lore_button_pause
            isCurrentEpisode -> R.string.lore_button_resume
            else -> R.string.lore_button_play
        }
    )
    val ink = loreOnAccent(accentColor)
    Row(
        modifier = Modifier.weight(1f).fillMaxHeight()
            .semantics(mergeDescendants = true) { contentDescription = description }
            .loreControlClickable(enabled = enabled && !isLoading, shape = RoundedCornerShape(ActionBarInnerCorner), onClick = onClick)
            .background(accentColor)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            BoxLoreLoader.CircularWavy(size = 26.dp, color = ink, trackColor = ink.copy(alpha = 0.2f))
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(26.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.bold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
