package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.contrastColor
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.info.EpisodeSort
import kotlinx.coroutines.launch

/**
 * A custom non-overlapping icon button for the toolbar to bypass minimum touch target overlap.
 */
@Composable
internal fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .expressiveClickable(onClick = onClick, shape = ExpressiveShapes.Pill)
                .background(containerColor, ExpressiveShapes.Pill),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Episode Toolbar - M3 Expressive
 * Contains: Search, Sort Toggle, Subscribe Button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isSearching: Boolean,
    currentSort: EpisodeSort,
    onSortToggle: () -> Unit,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    accentColor: Color,
    supportsReleaseAutomation: Boolean = true,
    notificationsEnabled: Boolean = false,
    onNotificationsToggle: () -> Unit = {},
    autoDownloadEnabled: Boolean = false,
    onAutoDownloadToggle: () -> Unit = {},
    genre: String = "",
    onSearchFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // --- Genre-themed celebration icon ---
    val genreLower = genre.lowercase()
    val celebrationIcon: ImageVector =
        when {
            genreLower.contains("music") -> Icons.Rounded.MusicNote
            genreLower.contains("comedy") -> Icons.Rounded.SentimentVerySatisfied
            genreLower.contains("sport") -> Icons.Rounded.EmojiEvents
            genreLower.contains("science") -> Icons.Rounded.Science
            genreLower.contains("tech") -> Icons.Rounded.Computer
            genreLower.contains("news") -> Icons.Rounded.Newspaper
            genreLower.contains("health") -> Icons.Rounded.MonitorHeart
            genreLower.contains("history") -> Icons.Rounded.AccountBalance
            genreLower.contains("arts") -> Icons.Rounded.Palette
            genreLower.contains("education") -> Icons.Rounded.School
            genreLower.contains("tv") || genreLower.contains("film") -> Icons.Rounded.Movie
            genreLower.contains("fiction") -> Icons.Rounded.AutoStories
            genreLower.contains("religion") || genreLower.contains("spiritual") -> Icons.Rounded.SelfImprovement
            genreLower.contains("family") || genreLower.contains("kids") -> Icons.Rounded.ChildCare
            genreLower.contains("leisure") -> Icons.Rounded.Weekend
            genreLower.contains("business") -> Icons.Rounded.Work
            genreLower.contains("government") -> Icons.Rounded.Gavel
            genreLower.contains("society") || genreLower.contains("culture") -> Icons.Rounded.Groups
            genreLower.contains("crime") -> Icons.Rounded.Fingerprint
            else -> Icons.Rounded.Favorite // Fallback: heart
        }

    // --- 3-state machine: IDLE → CELEBRATING → DONE ---
    // 0 = normal, 1 = celebrating (genre icon), 2 = done (subscribed)
    var celebrationPhase by remember { mutableIntStateOf(if (isSubscribed) 2 else 0) }
    var prevSubscribed by remember { mutableStateOf(isSubscribed) }

    // Detect subscribe transition
    LaunchedEffect(isSubscribed) {
        if (isSubscribed && !prevSubscribed) {
            // Just subscribed → celebration
            celebrationPhase = 1
            kotlinx.coroutines.delay(900L) // Hold the genre icon
            celebrationPhase = 2
        } else if (!isSubscribed) {
            kotlinx.coroutines.delay(120L)
            celebrationPhase = 0
        }
        prevSubscribed = isSubscribed
    }

    // Celebration icon animation
    val celebScale = remember { Animatable(0f) }
    val celebRotation = remember { Animatable(0f) }

    LaunchedEffect(celebrationPhase) {
        if (celebrationPhase == 1) {
            // Reset
            celebScale.snapTo(0f)
            celebRotation.snapTo(-30f)
            // Scale in with overshoot (parallel with rotation)
            launch {
                celebScale.animateTo(
                    1.3f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
                celebScale.animateTo(
                    1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                )
            }
            // Rotate in
            celebRotation.animateTo(
                0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            )
        }
    }

    // Screen configuration for layout optimization on narrow screens
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val isSmallScreen = screenWidth < 360

    val spacing = if (isSmallScreen) 6.dp else 8.dp
    val buttonHeight = if (isSmallScreen) 40.dp else 48.dp
    val buttonSize = if (isSmallScreen) 40.dp else 48.dp
    val iconSize = if (isSmallScreen) 20.dp else 22.dp
    val textStyle = MaterialTheme.typography.labelLarge
    val subIconSize = if (isSmallScreen) 18.dp else 20.dp
    val subIconGap = if (isSmallScreen) 6.dp else 8.dp

    val targetHorizontalPadding =
        if (celebrationPhase == 2) {
            if (isSmallScreen) 10.dp else 16.dp
        } else {
            if (isSmallScreen) 16.dp else 24.dp
        }
    val animatedHorizontalPadding by animateDpAsState(
        targetValue = targetHorizontalPadding,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "horizontalPadding",
    )

    val sortRotation by animateFloatAsState(
        targetValue = if (currentSort == EpisodeSort.NEWEST) 0f else 180f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "sortRotation",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Subscribe Button
        val subInteractionSource =
            remember {
                androidx.compose.foundation.interaction
                    .MutableInteractionSource()
            }
        val isSubPressed by subInteractionSource.collectIsPressedAsState()
        val subScale by animateFloatAsState(
            targetValue = if (isSubPressed) 0.9f else 1f,
            animationSpec = if (isSubPressed) cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.BouncySpring,
            label = "subScale",
        )

        // Animate container color smoothly
        val containerColor by animateColorAsState(
            targetValue =
                when (celebrationPhase) {
                    1 -> accentColor // Keep accent during celebration
                    2 -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> accentColor
                },
            animationSpec = tween(400),
            label = "containerColor",
        )
        // Pick text color based on container luminance for guaranteed contrast
        val onAccent = accentColor.contrastColor()
        val contentColor by animateColorAsState(
            targetValue =
                when (celebrationPhase) {
                    1 -> onAccent
                    2 -> MaterialTheme.colorScheme.onSurface
                    else -> onAccent
                },
            animationSpec = tween(400),
            label = "contentColor",
        )

        FilledTonalButton(
            onClick = onSubscribeClick,
            colors =
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            shape = ExpressiveShapes.Pill,
            contentPadding = PaddingValues(horizontal = animatedHorizontalPadding, vertical = 10.dp),
            interactionSource = subInteractionSource,
            modifier =
                Modifier
                    .weight(1f)
                    .height(buttonHeight)
                    .graphicsLayer {
                        scaleX = subScale
                        scaleY = subScale
                    },
        ) {
            // Content: AnimatedContent for the 3 phases
            AnimatedContent(
                targetState = celebrationPhase,
                transitionSpec = {
                    fadeIn(tween(150)).togetherWith(fadeOut(tween(100)))
                },
                contentAlignment = Alignment.Center,
                label = "subContent",
            ) { phase ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    when (phase) {
                        0 -> {
                            // Normal "Subscribe" state
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(subIconSize),
                            )
                            Spacer(modifier = Modifier.width(subIconGap))
                            Text(
                                text = "Subscribe",
                                style = textStyle,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        1 -> {
                            // Celebration: genre icon with bounce + rotate
                            Icon(
                                imageVector = celebrationIcon,
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(28.dp)
                                        .graphicsLayer {
                                            scaleX = celebScale.value
                                            scaleY = celebScale.value
                                            rotationZ = celebRotation.value
                                        },
                            )
                        }
                        else -> {
                            // Normal "Subscribed" state (text-only)
                            Text(
                                text = "Subscribed",
                                style = textStyle,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        AnimatedContent(
            targetState = celebrationPhase == 2 && supportsReleaseAutomation,
            transitionSpec = {
                (
                    fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                        scaleIn(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                            initialScale = 0.85f,
                        )
                ).togetherWith(
                    fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                        scaleOut(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                            targetScale = 0.85f,
                        ),
                ) using
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium,
                        )
                    }
            },
            contentAlignment = Alignment.CenterEnd,
            label = "actionsGroup",
        ) { showExtraActions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showExtraActions) {
                    // Notification Toggle Button (Bell icon)
                    val bellInteractionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        }
                    val isBellPressed by bellInteractionSource.collectIsPressedAsState()
                    val bellScale by animateFloatAsState(
                        targetValue = if (isBellPressed) 0.9f else 1f,
                        animationSpec = if (isBellPressed) cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.BouncySpring,
                        label = "bellScale",
                    )

                    val bellContainerColor by animateColorAsState(
                        targetValue =
                            if (notificationsEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        animationSpec = tween(300),
                        label = "bellContainerColor",
                    )

                    val bellContentColor by animateColorAsState(
                        targetValue =
                            if (notificationsEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        animationSpec = tween(300),
                        label = "bellContentColor",
                    )

                    ToolbarIconButton(
                        icon = if (notificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                        contentDescription = "Toggle notifications",
                        containerColor = bellContainerColor,
                        contentColor = bellContentColor,
                        onClick = onNotificationsToggle,
                        modifier =
                            Modifier
                                .size(buttonSize)
                                .graphicsLayer {
                                    scaleX = bellScale
                                    scaleY = bellScale
                                },
                        iconSize = iconSize,
                    )

                    // Auto-Download Toggle Button
                    val downloadInteractionSource =
                        remember {
                            androidx.compose.foundation.interaction
                                .MutableInteractionSource()
                        }
                    val isDownloadPressed by downloadInteractionSource.collectIsPressedAsState()
                    val downloadScale by animateFloatAsState(
                        targetValue = if (isDownloadPressed) 0.9f else 1f,
                        animationSpec = if (isDownloadPressed) cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.QuickSpring else cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion.BouncySpring,
                        label = "downloadScale",
                    )

                    val downloadContainerColor by animateColorAsState(
                        targetValue =
                            if (autoDownloadEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        animationSpec = tween(300),
                        label = "downloadContainerColor",
                    )

                    val downloadContentColor by animateColorAsState(
                        targetValue =
                            if (autoDownloadEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        animationSpec = tween(300),
                        label = "downloadContentColor",
                    )

                    val cloudDownloadIcon = ImageVector.vectorResource(cx.aswin.boxlore.feature.info.R.drawable.ic_cloud_download)
                    ToolbarIconButton(
                        icon = cloudDownloadIcon,
                        contentDescription = "Toggle auto-download",
                        containerColor = downloadContainerColor,
                        contentColor = downloadContentColor,
                        onClick = onAutoDownloadToggle,
                        modifier =
                            Modifier
                                .size(buttonSize)
                                .graphicsLayer {
                                    scaleX = downloadScale
                                    scaleY = downloadScale
                                },
                        iconSize = iconSize,
                    )
                }

                // Sort Button
                ToolbarIconButton(
                    icon = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "Sort",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onSortToggle,
                    modifier =
                        Modifier
                            .graphicsLayer { rotationX = sortRotation }
                            .size(buttonSize),
                    iconSize = iconSize,
                )

                // Search Button
                ToolbarIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Search",
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onSearchFocused,
                    modifier = Modifier.size(buttonSize),
                    iconSize = iconSize,
                )
            }
        }
    }
}
