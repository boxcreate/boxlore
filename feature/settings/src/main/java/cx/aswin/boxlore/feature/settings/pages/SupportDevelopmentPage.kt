package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

private val SUPPORT_CONTENT_BOTTOM_PADDING = 220.dp

@Composable
internal fun SupportDevelopmentPage(
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 2) { SUPPORT_TIER_CARDS.size }
    val coroutineScope = rememberCoroutineScope()
    val activeTier = SUPPORT_TIER_CARDS[pagerState.currentPage]

    val activeAuraColor by animateColorAsState(
        targetValue = activeTier.auraColor,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "active_aura_color",
    )

    Scaffold(
        topBar = {
            SupportTopAppBar(onBack = onBack)
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SupportHeroHeader()

            SupportCardlessStage(
                pagerState = pagerState,
                tiers = SUPPORT_TIER_CARDS,
                activeAuraColor = activeAuraColor,
            )

            SupportTierPicker(
                selectedIndex = pagerState.currentPage,
                tiers = SUPPORT_TIER_CARDS,
                activeColor = activeAuraColor,
                onSelect = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
            )

            SupportCtaSection(
                tier = activeTier,
                activeColor = activeAuraColor,
            )

            SupportAllocationMatrix(
                tier = activeTier,
                activeColor = activeAuraColor,
            )

            Spacer(modifier = Modifier.height(SUPPORT_CONTENT_BOTTOM_PADDING))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportTopAppBar(
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = "Support us",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun SupportHeroHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Keep boxlore ad-free for everyone",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "boxlore is completely free and ad-free. Contributions directly support our development and help keep it free for everyone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SupportCardlessStage(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stage_atmosphere")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "unit_levitation",
    )
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring_rotation",
    )
    val plasmaPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plasma_pulse",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center,
        ) {
            HolographicReactorBackdrop(
                auraColor = activeAuraColor,
                rotation = ringRotation,
                pulse = plasmaPulse,
                modifier = Modifier.fillMaxSize(),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tier = tiers[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val scale = lerp(1f, 0.8f, pageOffset.coerceIn(0f, 1f))
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.25f, pageOffset.coerceIn(0f, 1f))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = tier.iconRes),
                        contentDescription = tier.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(150.dp)
                            .graphicsLayer {
                                if (page == pagerState.currentPage) {
                                    translationY = floatOffset.dp.toPx()
                                }
                            },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        SupportStageTierInfo(
            tier = tiers[pagerState.currentPage],
            activeAuraColor = activeAuraColor,
        )
    }
}

@Composable
private fun HolographicReactorBackdrop(
    auraColor: Color,
    rotation: Float,
    pulse: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height * 0.68f
        val baseRadius = size.width * 0.36f

        // 1. Ambient radiant plasma bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.32f * pulse),
                    auraColor.copy(alpha = 0.10f * pulse),
                    Color.Transparent,
                ),
                center = Offset(centerX, size.height * 0.46f),
                radius = baseRadius * 1.3f * pulse,
            ),
        )

        // 2. Base Pedestal Frosted Glow
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.25f),
                    auraColor.copy(alpha = 0.18f),
                    Color.Transparent,
                ),
                center = Offset(centerX, centerY),
                radius = baseRadius * 0.72f,
            ),
            topLeft = Offset(centerX - baseRadius * 0.72f, centerY - 24.dp.toPx()),
            size = Size(baseRadius * 1.44f, 48.dp.toPx()),
        )

        // 3. Outer counter-rotating holographic dashed rings
        val outerRingWidth = baseRadius * 1.55f
        val outerRingHeight = 54.dp.toPx()
        drawOval(
            color = auraColor.copy(alpha = 0.45f),
            topLeft = Offset(centerX - outerRingWidth / 2f, centerY - outerRingHeight / 2f),
            size = Size(outerRingWidth, outerRingHeight),
            style = Stroke(
                width = 1.4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(12f, 10f),
                    rotation * 1.5f,
                ),
            ),
        )

        // 4. Inner reverse-rotating dashed ring
        val innerRingWidth = baseRadius * 1.15f
        val innerRingHeight = 38.dp.toPx()
        drawOval(
            color = auraColor.copy(alpha = 0.65f),
            topLeft = Offset(centerX - innerRingWidth / 2f, centerY - innerRingHeight / 2f),
            size = Size(innerRingWidth, innerRingHeight),
            style = Stroke(
                width = 1.1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(8f, 10f),
                    -rotation * 2f,
                ),
            ),
        )

        // 5. Center sharp focal dais
        drawOval(
            color = auraColor.copy(alpha = 0.85f),
            topLeft = Offset(centerX - (baseRadius * 0.75f) / 2f, centerY - 12.dp.toPx()),
            size = Size(baseRadius * 0.75f, 24.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun SupportStageTierInfo(
    tier: SupportTierCardData,
    activeAuraColor: Color,
) {
    AnimatedContent(
        targetState = tier,
        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
        label = "tier_stage_details",
    ) { targetTier ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (targetTier.isFeatured) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = targetTier.auraColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        text = "★ SUPREME PATRON UNIT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (targetTier.auraColor.luminance() > 0.45f) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = targetTier.title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            SupportSegmentedGauge(
                segments = targetTier.energySegments,
                powerImpact = targetTier.powerImpact,
                activeColor = activeAuraColor,
            )
        }
    }
}

@Composable
private fun SupportSegmentedGauge(
    segments: Int,
    powerImpact: String,
    activeColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            for (i in 1..5) {
                val isFilled = i <= segments
                val segmentColor by animateColorAsState(
                    targetValue = if (isFilled) {
                        activeColor
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    label = "gauge_segment",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(segmentColor),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val accessibleAccent = getAccessibleAccentColor(activeColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = accessibleAccent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = powerImpact,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = accessibleAccent,
            )
        }
    }
}

@Composable
private fun SupportTierPicker(
    selectedIndex: Int,
    tiers: List<SupportTierCardData>,
    activeColor: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tiers.forEachIndexed { index, tier ->
            Box(modifier = Modifier.weight(1f)) {
                SupportTierPill(
                    tier = tier,
                    isSelected = index == selectedIndex,
                    activeColor = activeColor,
                    onSelect = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun SupportTierPill(
    tier: SupportTierCardData,
    isSelected: Boolean,
    activeColor: Color,
    onSelect: () -> Unit,
) {
    val elevationOffset by animateFloatAsState(
        targetValue = if (isSelected) (-3f) else 0f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pill_elevation",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            activeColor
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(200),
        label = "pill_background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            activeColor
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "pill_border",
    )
    val durationTextColor by animateColorAsState(
        targetValue = if (isSelected) {
            getSupportButtonContentColor(activeColor)
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(200),
        label = "pill_duration_color",
    )
    val costTextColor by animateColorAsState(
        targetValue = if (isSelected) {
            getSupportButtonContentColor(activeColor).copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "pill_cost_color",
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = elevationOffset.dp.toPx() }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = tier.shortDuration,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = durationTextColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tier.cost,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.medium),
                color = costTextColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun getAccessibleAccentColor(color: Color): Color {
    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    if (!isLightMode) return color
    return when (color) {
        Color(0xFF00E5FF) -> Color(0xFF007A87)
        Color(0xFF00E676) -> Color(0xFF1B5E20)
        Color(0xFFFFB300) -> Color(0xFFB26A00)
        Color(0xFF8B5CF6) -> Color(0xFF6D28D9)
        Color(0xFFFF2D55) -> Color(0xFFC2185B)
        else -> color
    }
}

@Composable
private fun SupportAllocationMatrix(
    tier: SupportTierCardData,
    activeColor: Color,
) {
    val accessibleAccent = getAccessibleAccentColor(activeColor)
    val isLightMode = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isLightMode) 0.85f else 0.55f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = accessibleAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INFRASTRUCTURE IMPACT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accessibleAccent,
                        letterSpacing = 0.6.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = activeColor.copy(alpha = if (isLightMode) 0.20f else 0.12f),
                ) {
                    Text(
                        text = tier.powerImpact,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accessibleAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tier.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            if (tier.technicalBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tier.technicalBreakdown.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "◆",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accessibleAccent,
                                modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun getSupportButtonContentColor(backgroundColor: Color): Color =
    if (backgroundColor.luminance() > 0.45f) Color.Black else Color.White

@Composable
private fun SupportCtaSection(
    tier: SupportTierCardData,
    activeColor: Color,
) {
    val buttonContentColor = getSupportButtonContentColor(activeColor)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = activeColor,
                contentColor = buttonContentColor,
            ),
        ) {
            Icon(
                imageVector = if (tier.isFeatured) Icons.Rounded.Favorite else Icons.Rounded.Bolt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Deploy ${tier.title} • ${tier.cost}",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "One-time contribution • No recurring subscription",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Directly keeps boxlore ad-free and free of cost for everyone.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

internal data class SupportTierCardData(
    val title: String,
    val powerImpact: String,
    val shortDuration: String,
    val energySegments: Int,
    val scope: String,
    val description: String,
    val technicalBreakdown: List<String> = emptyList(),
    val cost: String,
    @DrawableRes val iconRes: Int,
    val auraColor: Color,
    val isFeatured: Boolean = false,
)

internal val SUPPORT_TIER_CARDS = listOf(
    SupportTierCardData(
        title = "Micro Energy Cell",
        powerImpact = "Powers 3 Hours",
        shortDuration = "3h",
        energySegments = 1,
        scope = "Sync & Feed Ingestion",
        description = "Directly funds real-time background feed synchronization, RSS ingestion webhooks, and podcast catalog lookups across our edge network.",
        technicalBreakdown = listOf(
            "RSS feed polling & catalog lookups",
            "Cloud edge worker execution & cache hits",
            "Real-time delta sync for active listeners",
        ),
        cost = "$0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
        auraColor = Color(0xFF00E5FF),
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers 8 Hours",
        shortDuration = "8h",
        energySegments = 2,
        scope = "Catalog Caching",
        description = "Covers high-frequency episode metadata indexing, fast podcast artwork delivery, and database read queries during peak listening hours.",
        technicalBreakdown = listOf(
            "Podcast artwork CDN delivery & edge caching",
            "Database read queries & full-text search indexing",
            "Continuous episode release monitoring",
        ),
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
        auraColor = Color(0xFF00E676),
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers 1 Full Day",
        shortDuration = "1d",
        energySegments = 3,
        scope = "AI & Vector Search",
        description = "Drives a full 24-hour computing capacity for AI semantic embeddings, semantic search queries, transcript processing, and backend streaming proxies.",
        technicalBreakdown = listOf(
            "AI vector embeddings & semantic episode matching",
            "Cloudflare worker proxies & network bandwidth",
            "Fast transcript retrieval & metadata enrichment",
        ),
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
        auraColor = Color(0xFFFFB300),
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers 3 Full Days",
        shortDuration = "3d",
        energySegments = 4,
        scope = "Database Cluster",
        description = "Sustains 72 hours of multi-tenant database clusters, high-throughput cloud sync pipelines, automated database backups, and audio metadata caches.",
        technicalBreakdown = listOf(
            "Primary database cluster & automated failovers",
            "Realtime multi-device cloud sync engine",
            "Audio stream caching & high-volume downloads",
        ),
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
        auraColor = Color(0xFF8B5CF6),
    ),
    SupportTierCardData(
        title = "Quantum Beacon",
        powerImpact = "Powers 1 Full Week",
        shortDuration = "7d",
        energySegments = 5,
        scope = "Global Grid",
        description = "Supreme patron beacon: keeps the entire global boxlore infrastructure fully funded for a full week, covering cloud compute, AI vector databases, global CDN caching, and continuous independent open-source development.",
        technicalBreakdown = listOf(
            "Full-stack server compute & database clusters",
            "Global CDN edge distribution & high-bandwidth audio",
            "Vector search models, AI embeddings & cloud sync",
            "Dedicated independent development & open-source tools",
        ),
        cost = "$17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        auraColor = Color(0xFFFF2D55),
        isFeatured = true,
    ),
)
