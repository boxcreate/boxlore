package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SupportIntroHeader()

            SupportStage(
                pagerState = pagerState,
                tiers = SUPPORT_TIER_CARDS,
                activeAuraColor = activeAuraColor,
            )

            SupportDialSelector(
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

            SupportSpecCard(
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
private fun SupportIntroHeader() {
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
            text = "boxlore is completely free of charge and built without advertisements. Listener contributions directly fund our cloud servers, search indexes, sync pipeline, and ongoing development so everyone enjoys an open podcast experience.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SupportStage(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stage_levitation")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "unit_levitation",
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    activeAuraColor.copy(alpha = 0.5f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    activeAuraColor.copy(alpha = 0.2f),
                ),
            ),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SupportStagePager(
                pagerState = pagerState,
                tiers = tiers,
                activeAuraColor = activeAuraColor,
                floatOffset = floatOffset,
            )

            Spacer(modifier = Modifier.height(4.dp))

            SupportStageTierInfo(
                tier = tiers[pagerState.currentPage],
                activeAuraColor = activeAuraColor,
            )
        }
    }
}

@Composable
private fun SupportStagePager(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
    floatOffset: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        HolographicPedestal(
            auraColor = activeAuraColor,
            modifier = Modifier.align(Alignment.BottomCenter),
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
                        val scale = lerp(1f, 0.82f, pageOffset.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(1f, 0.35f, pageOffset.coerceIn(0f, 1f))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = tier.iconRes),
                    contentDescription = tier.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(135.dp)
                        .graphicsLayer {
                            if (page == pagerState.currentPage) {
                                translationY = floatOffset.dp.toPx()
                            }
                        },
                )
            }
        }
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
                        color = if (targetTier.auraColor.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = targetTier.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
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
private fun HolographicPedestal(
    auraColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(bottom = 6.dp)
            .size(width = 190.dp, height = 54.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.5.dp.toPx()
            val centerOffset = Offset(size.width / 2f, size.height / 2f)

            // 1. Ambient radiant energy bloom
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = 0.45f),
                        auraColor.copy(alpha = 0.15f),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = size.width * 0.5f,
                ),
            )

            // 2. Outer holographic dashed projection ring
            drawOval(
                color = auraColor.copy(alpha = 0.55f),
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                ),
            )

            // 3. Frosted glass dais core
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        auraColor.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    center = centerOffset,
                    radius = size.width * 0.35f,
                ),
            )

            // 4. Inner sharp luminous ring
            drawOval(
                color = auraColor.copy(alpha = 0.7f),
                topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                size = Size(size.width * 0.64f, size.height * 0.64f),
                style = Stroke(width = 1.dp.toPx()),
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = powerImpact,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = activeColor,
            )
        }
    }
}

@Composable
private fun SupportDialSelector(
    selectedIndex: Int,
    tiers: List<SupportTierCardData>,
    activeColor: Color,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tiers.forEachIndexed { index, tier ->
            val isSelected = index == selectedIndex
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    activeColor.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                animationSpec = tween(200),
                label = "dial_container",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) {
                    activeColor
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                animationSpec = tween(200),
                label = "dial_border",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    activeColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(200),
                label = "dial_content",
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = backgroundColor,
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(index) },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tier.shortDuration,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold),
                        color = contentColor,
                    )
                    Text(
                        text = tier.cost,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.medium),
                        color = contentColor.copy(alpha = if (isSelected) 0.95f else 0.75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportSpecCard(
    tier: SupportTierCardData,
    activeColor: Color,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Sensors,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INFRASTRUCTURE IMPACT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = activeColor,
                    letterSpacing = 0.5.sp,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = tier.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            if (tier.technicalBreakdown.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tier.technicalBreakdown.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = activeColor,
                                modifier = Modifier.padding(end = 6.dp),
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

@Composable
private fun SupportCtaSection(
    tier: SupportTierCardData,
    activeColor: Color,
) {
    val buttonContentColor = if (activeColor.luminance() > 0.55f) Color.Black else Color.White

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
        title = "Orbital Quantum Beacon",
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
