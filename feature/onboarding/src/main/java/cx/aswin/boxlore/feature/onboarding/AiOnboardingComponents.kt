package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import kotlinx.coroutines.delay

@Composable
internal fun ChoiceRow(
    title: String,
    description: String,
    icon: ImageVector,
    selectedIcon: ImageVector,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    val borderStroke =
        if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }

    Card(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .graphicsLayer { alpha = if (enabled) 1.0f else 0.38f }
                .expressiveClickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSelected) selectedIcon else icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) contentColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        ).border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiAvatar(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .background(
                    brush =
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                        ),
                    shape = CircleShape,
                ).border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
internal fun AiMessageBubble(
    text: String,
    isCompact: Boolean = false,
) {
    val alpha by animateFloatAsState(targetValue = if (isCompact) 0.5f else 1.0f, label = "alpha")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isCompact) {
            AiAvatar(modifier = Modifier.padding(top = 4.dp))
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(44.dp))
        }

        Box(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                    .background(
                        if (isCompact) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                    ).border(
                        width = 1.dp,
                        color =
                            if (isCompact) {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            },
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    ).padding(
                        horizontal = if (isCompact) 12.dp else 16.dp,
                        vertical = if (isCompact) 8.dp else 12.dp,
                    ),
        ) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (isCompact) 13.sp else 15.sp,
                        lineHeight = if (isCompact) 18.sp else 22.sp,
                    ),
                color =
                    if (isCompact) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
        }
    }
}

@Composable
internal fun UserMessageBubble(
    text: String,
    isCompact: Boolean = false,
) {
    val alpha by animateFloatAsState(targetValue = if (isCompact) 0.5f else 1.0f, label = "alpha")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp))
                    .background(
                        if (isCompact) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                    ).padding(
                        horizontal = if (isCompact) 12.dp else 16.dp,
                        vertical = if (isCompact) 8.dp else 12.dp,
                    ),
        ) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (isCompact) 13.sp else 15.sp,
                        lineHeight = if (isCompact) 18.sp else 22.sp,
                    ),
                fontWeight = if (isCompact) FontWeight.Normal else FontWeight.Medium,
                color = if (isCompact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
internal fun DelayBypassBanner(
    onSwitchToManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .size(16.dp)
                            .padding(top = 1.dp),
                )
                Text(
                    text = "Taking longer than expected? You can skip the chat and customize your feed directly by picking your favorite topics.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 15.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FilledTonalButton(
                onClick = onSwitchToManual,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(17.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(
                    text = "Choose Topics Manually",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
internal fun AiLoadingBubble(
    stage: AiLoadingStage,
    elapsedSeconds: Int,
    onSwitchToManual: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        AiAvatar(modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThinkingIndicator(stage = stage, elapsedSeconds = elapsedSeconds)

                AnimatedVisibility(
                    visible = elapsedSeconds >= 15,
                    enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                    exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        DelayBypassBanner(onSwitchToManual = onSwitchToManual)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotAlphas =
        List(3) { index ->
            infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 150, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "dotAlpha_$index",
            )
        }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        dotAlphas.forEach { alpha ->
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { this.alpha = alpha.value }
                        .background(MaterialTheme.colorScheme.primary, shape = CircleShape),
            )
        }
    }
}

@Composable
internal fun ThinkingIndicator(
    stage: AiLoadingStage,
    elapsedSeconds: Int,
) {
    val thinkingMessages =
        remember(stage) {
            when (stage) {
                AiLoadingStage.GENERATING_RESPONSE ->
                    listOf(
                        "Analyzing conversation context...",
                        "Formulating suggestions...",
                        "Searching podcast indexes...",
                        "Formatting questions...",
                    )
                AiLoadingStage.SYNTHESIZING_PREFERENCES ->
                    listOf(
                        "Analyzing conversation history...",
                        "Extracting your topic preferences...",
                        "Building interest matrices...",
                    )
                AiLoadingStage.FETCHING_CATALOGS ->
                    listOf(
                        "Searching podcast database...",
                        "Connecting to vector index...",
                        "Matching topic catalogs to shows...",
                    )
                AiLoadingStage.ASSEMBLING_FEED ->
                    listOf(
                        "Generating tailored rows...",
                        "Curating top choices...",
                        "Polishing curriculum guide...",
                    )
                else ->
                    listOf(
                        "Thinking...",
                        "Refining curation...",
                    )
            }
        }

    var currentMessageIndex by remember { mutableStateOf(0) }

    LaunchedEffect(thinkingMessages) {
        currentMessageIndex = 0
        while (true) {
            delay(2500)
            currentMessageIndex = (currentMessageIndex + 1) % thinkingMessages.size
        }
    }

    val baseText = thinkingMessages.getOrElse(currentMessageIndex) { "Thinking..." }
    val displayedText =
        if (elapsedSeconds >= 15) {
            "Hmm, this is taking slightly longer than expected. Should be done in about ${maxOf(1, 45 - elapsedSeconds)}s..."
        } else {
            baseText
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (elapsedSeconds >= 15) {
            BoxLoreLoader.Expressive(size = 18.dp)
        } else {
            TypingIndicator()
        }

        AnimatedContent(
            targetState = displayedText,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
            },
            label = "thinkingText",
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun FinalSynthesisLoadingBubble(
    stage: AiLoadingStage,
    elapsedSeconds: Int,
    onSwitchToManual: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        AiAvatar(modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.width(8.dp))

        FinalSynthesisLoadingPanel(stage = stage, elapsedSeconds = elapsedSeconds, onSwitchToManual = onSwitchToManual)
    }
}

@Composable
internal fun FinalSynthesisLoadingPanel(
    stage: AiLoadingStage,
    elapsedSeconds: Int,
    onSwitchToManual: () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border =
            BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale1 by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.2f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseScale1",
            )
            val pulseScale2 by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.0f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(1800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "pulseScale2",
            )

            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    drawCircle(
                        color = Color(0xFF81B29A).copy(alpha = 0.15f * (1f - pulseScale2 / 1.2f)),
                        radius = size.minDimension / 2 * pulseScale2,
                    )
                }
                Canvas(modifier = Modifier.size(56.dp)) {
                    drawCircle(
                        color = Color(0xFF3D5A80).copy(alpha = 0.2f * (1f - pulseScale1 / 1.2f)),
                        radius = size.minDimension / 2 * pulseScale1,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(
                                brush =
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors =
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary,
                                            ),
                                    ),
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            ThinkingIndicator(stage = stage, elapsedSeconds = elapsedSeconds)

            AnimatedVisibility(
                visible = elapsedSeconds >= 15,
                enter = fadeIn(animationSpec = tween(400)) + expandVertically(animationSpec = tween(400)),
                exit = fadeOut(animationSpec = tween(300)) + shrinkVertically(animationSpec = tween(300)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 1.dp)
                    DelayBypassBanner(onSwitchToManual = onSwitchToManual)
                }
            }
        }
    }
}

@Composable
internal fun AnimatedMessageContainer(content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter =
            fadeIn(animationSpec = tween(durationMillis = 500)) +
                slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                        ),
                ) +
                scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                ),
        exit = fadeOut(animationSpec = tween(durationMillis = 300)),
    ) {
        content()
    }
}

@Composable
internal fun SuggestionBubble(
    option: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected && enabled) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )

    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    val icons = getOptionIcons(option)
    val icon = if (isSelected) icons.second else icons.first

    OutlinedCard(
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = containerColor,
            ),
        shape = RoundedCornerShape(16.dp),
        elevation =
            CardDefaults.outlinedCardElevation(
                defaultElevation = if (isSelected && enabled) 3.dp else 0.dp,
            ),
        border =
            BorderStroke(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled) 1.0f else 0.38f
                }.then(
                    if (enabled) {
                        Modifier.expressiveClickable { onClick() }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = option,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier =
                    Modifier
                        .size(22.dp)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ).border(
                            width = 2.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun BuildFeedNowChip(
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val containerColor =
        MaterialTheme.colorScheme.primaryContainer
            .copy(alpha = 0.25f)
            .compositeOver(MaterialTheme.colorScheme.surface)

    val contentColor = MaterialTheme.colorScheme.primary

    OutlinedCard(
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = containerColor,
            ),
        shape = RoundedCornerShape(16.dp),
        border =
            BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .graphicsLayer {
                    alpha = if (enabled) 1.0f else 0.38f
                }.then(
                    if (enabled) {
                        Modifier.expressiveClickable { onClick() }
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "✨ Build my feed now",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Text(
                    text = "Synthesize choices and generate recommendations",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
