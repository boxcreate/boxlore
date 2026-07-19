package cx.aswin.boxlore.feature.library.history

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.ModeNight
import androidx.compose.material.icons.rounded.Pending
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.feature.library.DetailedHistoryStats
import java.util.concurrent.TimeUnit


@Composable
fun HistoryStatsCardContainer(
    gradientColors: List<Color>,
    shapes: List<Shape>,
    shapeColors: List<Color>,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stats_card_shapes")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    ElevatedCard(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = gradientColors.firstOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Brush.linearGradient(colors = gradientColors))
        ) {
            // Floating shapes in background
            if (shapes.size >= 1 && shapeColors.size >= 1) {
                CardFloatingShape(
                    shape = shapes[0],
                    rotation = rotation,
                    color = shapeColors[0],
                    size = 140.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 20.dp, y = (-30).dp + floatOffset.dp)
                )
            }
            if (shapes.size >= 2 && shapeColors.size >= 2) {
                CardFloatingShape(
                    shape = shapes[1],
                    rotation = -rotation * 0.5f,
                    color = shapeColors[1],
                    size = 120.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-20).dp - floatOffset.dp, y = 30.dp)
                )
            }
            if (shapes.size >= 3 && shapeColors.size >= 3) {
                CardFloatingShape(
                    shape = shapes[2],
                    rotation = rotation * 0.3f,
                    color = shapeColors[2],
                    size = 100.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 30.dp, y = 20.dp + (floatOffset * 0.5f).dp)
                )
            }

            // Content container above background shapes
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .zIndex(1f)
            ) {
                content()
            }
        }
    }
}


@Composable
fun OverviewStatsCard(stats: DetailedHistoryStats) {
    val darkTheme = isSystemInDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    )
    val shapeColors = if (darkTheme) {
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.06f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
        )
    }
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    val infiniteTransition = rememberInfiniteTransition(label = "streak_flame")
    val flameColor by infiniteTransition.animateColor(
        initialValue = Color.White,
        targetValue = Color(0xFFFFF176), // Light amber/yellow
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flame_color"
    )

    HistoryStatsCardContainer(
        gradientColors = gradientColors,
        shapes = listOf(ExpressiveShapes.Sunny, ExpressiveShapes.Flower, ExpressiveShapes.Burst),
        shapeColors = shapeColors
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Listening Time & Streak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening Time",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val hours = TimeUnit.MILLISECONDS.toHours(stats.totalListeningMs)
                    val mins = TimeUnit.MILLISECONDS.toMinutes(stats.totalListeningMs) % 60
                    Text(
                        text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (stats.listeningStreakDays > 0) {
                    val streakBgBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF3D00),
                            Color(0xFFFF9100)
                        )
                    )
                    Box(
                        modifier = Modifier
                            .background(brush = streakBgBrush, shape = RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Whatshot,
                                contentDescription = "Streak",
                                tint = flameColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${stats.listeningStreakDays}-Day Streak",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(
                                color = contentColor.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "0-Day Streak",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = contentColor.copy(alpha = 0.25f),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Bottom Section: 3-column metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverviewMetricColumn(
                    icon = Icons.Rounded.CheckCircle,
                    iconColor = Color(0xFF00E676), // Vibrant Neon Green
                    label = "Completed",
                    value = "${stats.completedEpisodesCount}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricColumn(
                    icon = Icons.Rounded.Pending,
                    iconColor = Color(0xFFFFD600), // Vibrant Yellow
                    label = "In Progress",
                    value = "${stats.inProgressEpisodesCount}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
                OverviewMetricColumn(
                    icon = Icons.Rounded.Favorite,
                    iconColor = Color(0xFFFF1744), // Vibrant Pink-Red
                    label = "Liked",
                    value = "${stats.likedEpisodesCount}",
                    contentColor = contentColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


@Composable
fun OverviewMetricColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor.copy(alpha = 0.95f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
    }
}


@Composable
fun HabitsStatsCard(stats: DetailedHistoryStats) {
    val darkTheme = isSystemInDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    )
    val shapeColors = if (darkTheme) {
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        )
    }
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer

    val vibeIcon = calculateHabitsVibeIcon(stats.peakListeningVibe)
    val peakHourText = calculatePeakHourText(stats.peakListeningHour)

    HistoryStatsCardContainer(
        gradientColors = gradientColors,
        shapes = listOf(ExpressiveShapes.Flower, ExpressiveShapes.Cookie12, ExpressiveShapes.Burst),
        shapeColors = shapeColors
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Vibe & Peak Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening Vibe",
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stats.peakListeningVibe,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = contentColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.40f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = vibeIcon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = peakHourText,
                            style = MaterialTheme.typography.titleSmall,
                            color = contentColor,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Middle Section: Distribution Graph spanning full width, filling remaining vertical space
            val barValues = remember(stats.hourlyDistribution) {
                FloatArray(12) { i ->
                    stats.hourlyDistribution.getOrElse(i * 2) { 0f } + stats.hourlyDistribution.getOrElse(i * 2 + 1) { 0f }
                }
            }
            val maxVal = remember(barValues) { barValues.maxOrNull() ?: 1f }.let { if (it == 0f) 1f else it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    barValues.forEachIndexed { index, value ->
                        val normalizedHeight = (value / maxVal).coerceIn(0.12f, 1f)
                        val isPeak = index == stats.peakListeningHour / 2
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .fillMaxHeight(normalizedHeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (isPeak) contentColor
                                    else contentColor.copy(alpha = 0.55f)
                                )
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("12 AM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("6 AM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("12 PM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("6 PM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                    Text("11 PM", style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}


@Composable
fun TopShowStatsCard(stats: DetailedHistoryStats) {
    val darkTheme = isSystemInDarkTheme()
    val gradientColors = listOf(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    )
    val shapeColors = if (darkTheme) {
        listOf(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
    }
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    HistoryStatsCardContainer(
        gradientColors = gradientColors,
        shapes = listOf(ExpressiveShapes.Burst, ExpressiveShapes.SoftBurst, ExpressiveShapes.Diamond),
        shapeColors = shapeColors
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = contentColor.copy(alpha = 0.1f),
                border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.2f)),
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (stats.topPodcastImageUrl != null) {
                    OptimizedImage(
                        url = stats.topPodcastImageUrl,
                        proxyWidth = 200,
                        contentDescription = stats.topPodcastName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Top Podcast",
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stats.topPodcastName ?: "No podcast found",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor
                )

                if (stats.topPodcastName != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = contentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, contentColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            val playsText = if (stats.topPodcastPlayCount == 1) "1 play" else "${stats.topPodcastPlayCount} plays"
                            Text(
                                text = playsText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = contentColor
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Listen to podcasts to see your top show!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}


@Composable
fun StatsMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


@Composable
fun CardFloatingShape(
    shape: Shape,
    rotation: Float,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = rotation
            }
            .background(color = color, shape = shape)
    )
}


@Composable
fun CompactMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}


private fun calculateHabitsVibeIcon(vibe: String?): androidx.compose.ui.graphics.vector.ImageVector {
    return when (vibe) {
        "Morning Ritual" -> Icons.Rounded.LightMode
        "Midday Flow" -> Icons.Rounded.Bolt
        "Evening Unwind" -> Icons.Rounded.ModeNight
        "Night Owl" -> Icons.Rounded.Bedtime
        else -> Icons.Rounded.AccessTime
    }
}


private fun calculatePeakHourText(hour: Int): String {
    if (hour < 0) return "No activity"
    val ampm = if (hour >= 12) "PM" else "AM"
    val hour12 = if (hour % 12 == 0) 12 else hour % 12
    return "$hour12 $ampm peak"
}

