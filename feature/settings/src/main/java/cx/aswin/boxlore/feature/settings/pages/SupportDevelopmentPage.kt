package cx.aswin.boxlore.feature.settings.pages

import android.app.Activity
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private val SUPPORT_CONTENT_BOTTOM_PADDING = 220.dp

@Composable
internal fun SupportDevelopmentPage(
    onBack: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousAppearance = insetsController?.isAppearanceLightStatusBars
        insetsController?.isAppearanceLightStatusBars = false
        onDispose {
            previousAppearance?.let { insetsController.isAppearanceLightStatusBars = it }
        }
    }

    val pagerState = rememberPagerState(initialPage = 2) { SUPPORT_TIER_CARDS.size }
    val coroutineScope = rememberCoroutineScope()
    val activeTier = SUPPORT_TIER_CARDS[pagerState.currentPage]

    val activeAuraColor by animateColorAsState(
        targetValue = activeTier.auraColor,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "active_aura_color",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "support_page_atmosphere")
    val plasmaPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "plasma_pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Scaffold(
            topBar = {
                SupportTopAppBar(onBack = onBack)
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SupportCardlessStage(
                    pagerState = pagerState,
                    tiers = SUPPORT_TIER_CARDS,
                    activeAuraColor = activeAuraColor,
                    plasmaPulse = plasmaPulse,
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

                SupportMissionCard(
                    activeColor = activeAuraColor,
                )

                Spacer(modifier = Modifier.height(SUPPORT_CONTENT_BOTTOM_PADDING))
            }
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
                color = Color.White,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SupportCardlessStage(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
    plasmaPulse: Float,
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
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_progress",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp),
            contentAlignment = Alignment.Center,
        ) {
            HolographicReactorBackdrop(
                auraColor = activeAuraColor,
                rotation = ringRotation,
                pulse = plasmaPulse,
                particleProgress = particleProgress,
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
                            .size(165.dp)
                            .graphicsLayer {
                                if (page == pagerState.currentPage) {
                                    translationY = floatOffset.dp.toPx()
                                }
                            },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

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
    particleProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height * 0.78f
        val baseRadius = size.width * 0.40f

        drawReactorAtmosphereAndSparks(
            centerX = centerX,
            centerY = centerY,
            baseRadius = baseRadius,
            auraColor = auraColor,
            pulse = pulse,
            particleProgress = particleProgress,
        )
        drawHolographicOrbitalRings(centerX, centerY, baseRadius, auraColor, rotation)
    }
}

private fun DrawScope.drawReactorAtmosphereAndSparks(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    auraColor: Color,
    pulse: Float,
    particleProgress: Float,
) {
    // 1. Spreading ambient aura centered behind 3D unit (scrolls with page and moved up!)
    val auraRadius = size.width * 1.30f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                auraColor.copy(alpha = 0.35f * pulse),
                auraColor.copy(alpha = 0.16f * pulse),
                auraColor.copy(alpha = 0.05f * pulse),
                Color.Transparent,
            ),
            center = Offset(centerX, size.height * 0.48f),
            radius = auraRadius * pulse,
        ),
    )

    // 2. Focused reactor dais core glow directly beneath the asset
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                auraColor.copy(alpha = 0.40f * pulse),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = baseRadius * 0.85f,
        ),
        topLeft = Offset(centerX - baseRadius * 0.85f, centerY - 20.dp.toPx()),
        size = Size(baseRadius * 1.70f, 40.dp.toPx()),
    )

    // 3. Rising photon energy particles from dais up into the void
    val particleCount = 14
    for (i in 0 until particleCount) {
        val seed = (i * 73.17f) % 360f
        val rad = seed * (PI.toFloat() / 180f)
        val progress = (particleProgress + i.toFloat() / particleCount) % 1f
        val spreadX = (baseRadius * 0.95f) * cos(rad)
        val wobble = sin(progress * 2f * PI.toFloat() + seed) * 12.dp.toPx()
        val px = centerX + spreadX + wobble
        val py = centerY - (progress * 150.dp.toPx())
        val alpha = sin(progress * PI.toFloat()).coerceIn(0f, 1f) * 0.75f

        if (alpha > 0.02f) {
            drawCircle(
                color = auraColor.copy(alpha = alpha * 0.45f),
                radius = 3.5.dp.toPx(),
                center = Offset(px, py),
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.90f),
                radius = 1.2.dp.toPx(),
                center = Offset(px, py),
            )
        }
    }
}

private fun DrawScope.drawHolographicOrbitalRings(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    auraColor: Color,
    rotation: Float,
) {
    val outerWidth = baseRadius * 1.65f
    val outerHeight = 48.dp.toPx()
    drawOval(
        color = auraColor.copy(alpha = 0.50f),
        topLeft = Offset(centerX - outerWidth / 2f, centerY - outerHeight / 2f),
        size = Size(outerWidth, outerHeight),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), rotation * 1.5f),
        ),
    )

    val outerAngleRad = rotation * (PI / 180.0)
    val sparkX1 = (centerX + (outerWidth / 2f) * cos(outerAngleRad)).toFloat()
    val sparkY1 = (centerY + (outerHeight / 2f) * sin(outerAngleRad)).toFloat()
    drawCircle(
        color = Color.White,
        radius = 2.5.dp.toPx(),
        center = Offset(sparkX1, sparkY1),
    )
    drawCircle(
        color = auraColor,
        radius = 5.dp.toPx(),
        center = Offset(sparkX1, sparkY1),
        style = Stroke(width = 1.2.dp.toPx()),
    )

    val innerWidth = baseRadius * 1.22f
    val innerHeight = 34.dp.toPx()
    drawOval(
        color = auraColor.copy(alpha = 0.70f),
        topLeft = Offset(centerX - innerWidth / 2f, centerY - innerHeight / 2f),
        size = Size(innerWidth, innerHeight),
        style = Stroke(
            width = 1.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), -rotation * 2.2f),
        ),
    )

    val innerAngleRad = (-rotation * 2.2 + 120.0) * (PI / 180.0)
    val sparkX2 = (centerX + (innerWidth / 2f) * cos(innerAngleRad)).toFloat()
    val sparkY2 = (centerY + (innerHeight / 2f) * sin(innerAngleRad)).toFloat()
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = 2.2.dp.toPx(),
        center = Offset(sparkX2, sparkY2),
    )

    drawOval(
        color = auraColor.copy(alpha = 0.95f),
        topLeft = Offset(centerX - (baseRadius * 0.78f) / 2f, centerY - 9.dp.toPx()),
        size = Size(baseRadius * 0.78f, 18.dp.toPx()),
        style = Stroke(width = 1.4.dp.toPx()),
    )
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
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = activeAuraColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = targetTier.powerImpact,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold),
                    color = activeAuraColor,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            SupportSegmentedGauge(
                segments = targetTier.energySegments,
                activeColor = activeAuraColor,
            )
        }
    }
}

@Composable
private fun SupportSegmentedGauge(
    segments: Int,
    activeColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.50f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 1..5) {
            val isFilled = i <= segments
            val segmentColor by animateColorAsState(
                targetValue = if (isFilled) {
                    activeColor
                } else {
                    Color(0xFF1E1E26)
                },
                animationSpec = tween(250, easing = FastOutSlowInEasing),
                label = "gauge_segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(segmentColor),
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
        modifier = Modifier.fillMaxWidth(),
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
            Color(0xFF101016)
        },
        animationSpec = tween(200),
        label = "pill_background",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            activeColor
        } else {
            Color(0xFF22222E)
        },
        animationSpec = tween(200),
        label = "pill_border",
    )
    val durationTextColor by animateColorAsState(
        targetValue = if (isSelected) {
            getSupportButtonContentColor(activeColor)
        } else {
            Color.White
        },
        animationSpec = tween(200),
        label = "pill_duration_color",
    )
    val costTextColor by animateColorAsState(
        targetValue = if (isSelected) {
            getSupportButtonContentColor(activeColor).copy(alpha = 0.85f)
        } else {
            Color(0xFF8E8E9A)
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
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
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
private fun SupportMissionCard(
    activeColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0C0C14),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keep boxlore ad-free for everyone",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = "boxlore is completely free and ad-free. Contributions directly support our development and help keep it free for everyone.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9EA8),
                lineHeight = 18.sp,
            )
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
                .height(50.dp),
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "One-time contribution • No recurring subscription",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.medium),
            color = Color.White.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Directly keeps boxlore ad-free and free of cost for everyone.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
        )
    }
}

internal data class SupportTierCardData(
    val title: String,
    val powerImpact: String,
    val shortDuration: String,
    val energySegments: Int,
    val cost: String,
    @DrawableRes val iconRes: Int,
    val auraColor: Color,
    val isFeatured: Boolean = false,
)

internal val SUPPORT_TIER_CARDS = listOf(
    SupportTierCardData(
        title = "Micro Energy Cell",
        powerImpact = "Powers the boxlore servers for 3 hours",
        shortDuration = "3h",
        energySegments = 1,
        cost = "$0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
        auraColor = Color(0xFF2979FF),
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers the boxlore servers for 8 hours",
        shortDuration = "8h",
        energySegments = 2,
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
        auraColor = Color(0xFF00E676),
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers the boxlore servers for 1 full day",
        shortDuration = "1d",
        energySegments = 3,
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
        auraColor = Color(0xFFFFB300),
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers the boxlore servers for 3 full days",
        shortDuration = "3d",
        energySegments = 4,
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
        auraColor = Color(0xFF8B5CF6),
    ),
    SupportTierCardData(
        title = "Quantum Beacon",
        powerImpact = "Powers the boxlore servers for 1 full week",
        shortDuration = "7d",
        energySegments = 5,
        cost = "$17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        auraColor = Color(0xFFFF2D55),
        isFeatured = true,
    ),
)
