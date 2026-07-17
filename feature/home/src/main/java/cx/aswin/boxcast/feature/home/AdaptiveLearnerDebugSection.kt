package cx.aswin.boxcast.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxcast.core.data.ranking.FeatureSlot
import cx.aswin.boxcast.core.data.ranking.LearnerExposureDebug
import cx.aswin.boxcast.core.data.ranking.LearnerFacetDebug
import cx.aswin.boxcast.core.data.ranking.LearnerFeatureWeightDebug
import cx.aswin.boxcast.core.data.ranking.LearnerInspectorSnapshot
import cx.aswin.boxcast.core.data.ranking.PreferenceFacetType
import cx.aswin.boxcast.core.data.ranking.RankingDebugSnapshot
import cx.aswin.boxcast.core.data.ranking.RankingObjective
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun AdaptiveLearnerDebugSection(
    snapshot: LearnerInspectorSnapshot?,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "On-device bandit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh learner")
                }
            }
        }

        if (snapshot == null) {
            Text(
                text = if (loading) "Reading local model…" else "No learner state yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }

        ScorePipelineGraph(snapshot = snapshot)
        ObjectiveMaturityRings(objectives = snapshot.objectives)
        TasteConstellation(facets = snapshot.facets)
        GenrePolarityChart(
            facets = snapshot.facets.filter { it.type == PreferenceFacetType.GENRE },
        )
        FeatureWeightBars(weights = snapshot.featureWeights)
        ExposurePulseStrip(
            exposures = snapshot.recentExposures,
            pendingCount = snapshot.pendingExposureCount,
            resolvedCount = snapshot.resolvedExposureCount,
        )
    }
}

@Composable
private fun ScorePipelineGraph(snapshot: LearnerInspectorSnapshot) {
    val discovery = snapshot.objectives.firstOrNull { it.objective == RankingObjective.DISCOVERY }
        ?: snapshot.objectives.first()
    val priorWeight = (1.0 - discovery.learnedBlend).toFloat()
    val learnedWeight = discovery.learnedBlend.toFloat()
    val exploreWeight = if (discovery.explorationEnabled) 0.18f else 0.04f
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Score merge",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .background(surface, RoundedCornerShape(20.dp))
                .padding(12.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftX = size.width * 0.18f
                val midX = size.width * 0.52f
                val rightX = size.width * 0.86f
                val yPrior = size.height * 0.22f
                val yLearned = size.height * 0.50f
                val yExplore = size.height * 0.78f
                val yFinal = size.height * 0.50f
                val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

                fun drawEdge(from: Offset, to: Offset, color: Color, width: Float) {
                    drawLine(
                        color = color.copy(alpha = 0.75f),
                        start = from,
                        end = to,
                        strokeWidth = width,
                        cap = StrokeCap.Round,
                        pathEffect = if (width < 5f) dashed else null,
                    )
                }

                drawEdge(Offset(leftX, yPrior), Offset(midX, yFinal), primary, 3f + priorWeight * 10f)
                drawEdge(Offset(leftX, yLearned), Offset(midX, yFinal), secondary, 3f + learnedWeight * 12f)
                drawEdge(Offset(leftX, yExplore), Offset(midX, yFinal), tertiary, 2f + exploreWeight * 14f)
                drawEdge(Offset(midX, yFinal), Offset(rightX, yFinal), onSurface.copy(alpha = 0.45f), 6f)

                fun node(center: Offset, radius: Float, color: Color) {
                    drawCircle(color = color.copy(alpha = 0.22f), radius = radius + 8f, center = center)
                    drawCircle(color = color, radius = radius, center = center)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f),
                        radius = radius * 0.35f,
                        center = center + Offset(-radius * 0.2f, -radius * 0.2f),
                    )
                }

                node(Offset(leftX, yPrior), 16f + priorWeight * 10f, primary)
                node(Offset(leftX, yLearned), 16f + learnedWeight * 12f, secondary)
                node(Offset(leftX, yExplore), 12f + exploreWeight * 14f, tertiary)
                node(Offset(midX, yFinal), 22f, outline)
                node(Offset(rightX, yFinal), 26f, primary)
            }

            PipelineLabel("Prior", Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 4.dp))
            PipelineLabel(
                "Learned",
                Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
            )
            PipelineLabel(
                if (discovery.explorationEnabled) "Explore" else "Explore·off",
                Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 4.dp),
            )
            PipelineLabel("Blend", Modifier.align(Alignment.Center).offset(x = (-8).dp))
            PipelineLabel("Rank", Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
        }
        BlendMeter(learnedBlend = discovery.learnedBlend, exploration = discovery.explorationEnabled)
    }
}

@Composable
private fun PipelineLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun BlendMeter(learnedBlend: Double, exploration: Boolean) {
    val prior = 1.0 - learnedBlend
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(prior.toFloat().coerceAtLeast(0.05f))
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(learnedBlend.toFloat().coerceAtLeast(0.05f))
                .background(MaterialTheme.colorScheme.secondary, CircleShape),
        )
        if (exploration) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.12f)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }
    }
}

@Composable
private fun ObjectiveMaturityRings(objectives: List<RankingDebugSnapshot>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Objectives",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            objectives.forEach { objective ->
                MaturityRing(snapshot = objective)
            }
        }
    }
}

@Composable
private fun MaturityRing(snapshot: RankingDebugSnapshot) {
    val progress = (snapshot.updateCount.toFloat() / 50f).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val active = when {
        snapshot.updateCount == 0L -> MaterialTheme.colorScheme.outline
        progress < 1f -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp),
    ) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = Size(size.width - stroke, size.height - stroke),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                )
                drawArc(
                    color = active,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    size = Size(size.width - stroke, size.height - stroke),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                )
            }
            Text(
                text = "${snapshot.updateCount}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = snapshot.objective.name
                .lowercase()
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar(Char::titlecase) },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (snapshot.explorationEnabled) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun TasteConstellation(facets: List<LearnerFacetDebug>) {
    val top = remember(facets) {
        facets
            .filter { abs(it.affinity) >= 0.05 }
            .take(28)
    }
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val neutral = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Preference graph",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .background(surface, RoundedCornerShape(20.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(color = neutral.copy(alpha = 0.25f), radius = min(cx, cy) * 0.72f, center = Offset(cx, cy))
                drawCircle(color = neutral.copy(alpha = 0.18f), radius = min(cx, cy) * 0.42f, center = Offset(cx, cy))
                drawCircle(color = neutral.copy(alpha = 0.35f), radius = 10f, center = Offset(cx, cy))

                top.forEachIndexed { index, facet ->
                    val ring = when (facet.type) {
                        PreferenceFacetType.GENRE -> 0.78f
                        PreferenceFacetType.SHOW -> 0.55f
                        PreferenceFacetType.SOURCE -> 0.34f
                        else -> 0.65f
                    }
                    val angle = (index.toFloat() / max(top.size, 1).toFloat()) *
                        (Math.PI.toFloat() * 2f) +
                        when (facet.type) {
                            PreferenceFacetType.GENRE -> 0f
                            PreferenceFacetType.SHOW -> 0.4f
                            PreferenceFacetType.SOURCE -> 0.8f
                            else -> 1.2f
                        }
                    val radius = min(cx, cy) * ring
                    val x = cx + cos(angle) * radius
                    val y = cy + sin(angle) * radius
                    val affinity = facet.affinity.toFloat().coerceIn(-1f, 1f)
                    val color = when {
                        affinity > 0.05f -> lerp(neutral, positive, affinity)
                        affinity < -0.05f -> lerp(neutral, negative, -affinity)
                        else -> neutral
                    }
                    drawLine(
                        color = color.copy(alpha = 0.35f),
                        start = Offset(cx, cy),
                        end = Offset(x, y),
                        strokeWidth = 1.5f + abs(affinity) * 3f,
                    )
                    drawCircle(
                        color = color,
                        radius = 5f + abs(affinity) * 10f,
                        center = Offset(x, y),
                    )
                }
            }
            Text(
                text = "outer · genre   mid · show   inner · source",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GenrePolarityChart(facets: List<LearnerFacetDebug>) {
    val rows = remember(facets) {
        facets.sortedByDescending { abs(it.affinity) }.take(12)
    }
    if (rows.isEmpty()) return

    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Taste polarity",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { facet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = facet.key.take(14),
                        modifier = Modifier.width(78.dp),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(12.dp)
                            .background(track, CircleShape),
                    ) {
                        val affinity = facet.affinity.toFloat().coerceIn(-1f, 1f)
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val mid = size.width / 2f
                            val barWidth = (size.width / 2f) * abs(affinity)
                            if (affinity >= 0f) {
                                drawRoundRect(
                                    color = positive,
                                    topLeft = Offset(mid, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                )
                            } else {
                                drawRoundRect(
                                    color = negative,
                                    topLeft = Offset(mid - barWidth, 0f),
                                    size = Size(barWidth, size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                )
                            }
                            drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.5f, center = Offset(mid, size.height / 2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureWeightBars(weights: List<LearnerFeatureWeightDebug>) {
    val visible = remember(weights) {
        weights
            .filter { it.slot != FeatureSlot.INTERCEPT }
            .sortedByDescending { abs(it.weight) }
            .take(10)
    }
    if (visible.isEmpty()) return
    val maxAbs = visible.maxOf { abs(it.weight) }.coerceAtLeast(1e-6)
    val positive = MaterialTheme.colorScheme.secondary
    val negative = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Feature pull (Discovery θ)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEach { item ->
                val ratio = (abs(item.weight) / maxAbs).toFloat().coerceIn(0.05f, 1f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.slot.name.lowercase().replace('_', ' '),
                        modifier = Modifier.width(110.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(track, CircleShape),
                        contentAlignment = if (item.weight >= 0) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ratio)
                                .background(
                                    if (item.weight >= 0) positive else negative,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExposurePulseStrip(
    exposures: List<LearnerExposureDebug>,
    pendingCount: Int,
    resolvedCount: Int,
) {
    val positive = MaterialTheme.colorScheme.primary
    val negative = MaterialTheme.colorScheme.error
    val pending = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Learning pulse",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$resolvedCount✓  $pendingCount…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            ExposurePulseCanvas(
                exposures = exposures,
                positive = positive,
                negative = negative,
                pending = pending,
            )
        }
        if (exposures.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                exposures.take(10).forEach { exposure ->
                    ExposureChip(exposure)
                }
            }
        }
    }
}

@Composable
private fun ExposurePulseCanvas(
    exposures: List<LearnerExposureDebug>,
    positive: Color,
    negative: Color,
    pending: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseline = size.height * 0.65f
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Transparent,
                    pending.copy(alpha = 0.5f),
                    Color.Transparent,
                ),
            ),
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 2f,
        )
        if (exposures.isEmpty()) return@Canvas
        val step = size.width / max(exposures.size - 1, 1).toFloat()
        exposures.asReversed().forEachIndexed { index, exposure ->
            drawExposurePulse(
                x = index * step,
                baseline = baseline,
                reward = exposure.reward,
                positive = positive,
                negative = negative,
                pending = pending,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawExposurePulse(
    x: Float,
    baseline: Float,
    reward: Double?,
    positive: Color,
    negative: Color,
    pending: Color,
) {
    val color = when {
        reward == null -> pending
        reward >= 0 -> positive
        else -> negative
    }
    val magnitude = abs(reward ?: 0.15).toFloat().coerceIn(0.12f, 1f)
    val direction = if ((reward ?: 0.0) >= 0) 1f else -0.35f
    val y = baseline - magnitude * size.height * 0.55f * direction
    drawLine(
        color = color.copy(alpha = 0.45f),
        start = Offset(x, baseline),
        end = Offset(x, y),
        strokeWidth = 2f,
    )
    drawCircle(color = color, radius = 4f + magnitude * 5f, center = Offset(x, y))
}

@Composable
private fun ExposureChip(exposure: LearnerExposureDebug) {
    val reward = exposure.reward
    val color = when {
        reward == null -> MaterialTheme.colorScheme.outlineVariant
        reward >= 0 -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Surface(
        color = color,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
            shape = RoundedCornerShape(12.dp),
        ),
    ) {
        Text(
            text = exposure.source.take(10),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
