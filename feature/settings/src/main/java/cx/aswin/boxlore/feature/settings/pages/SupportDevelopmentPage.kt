package cx.aswin.boxlore.feature.settings.pages

import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private val StalinistOneFontFamily = FontFamily(
    Font(R.font.stalinist_one, FontWeight.Normal),
)

@Composable
internal fun SupportDevelopmentPage(
    onBack: () -> Unit,
    totalListeningHours: Long? = null,
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
        animationSpec = tween(200, easing = FastOutSlowInEasing),
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
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CosmicAtmosphereCanvas(
            auraColor = activeAuraColor,
            pulse = plasmaPulse,
            particleProgress = particleProgress,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .align(Alignment.TopCenter),
        )

        Scaffold(
            topBar = {
                SupportTopAppBar(onBack = onBack)
            },
            containerColor = Color.Transparent,
        ) { innerPadding ->
            SupportPageContent(
                pagerState = pagerState,
                activeTier = activeTier,
                activeAuraColor = activeAuraColor,
                totalListeningHours = totalListeningHours,
                onSelectTier = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SupportPageContent(
    pagerState: PagerState,
    activeTier: SupportTierCardData,
    activeAuraColor: Color,
    totalListeningHours: Long?,
    onSelectTier: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SupportArtifactStage(
            pagerState = pagerState,
            tiers = SUPPORT_TIER_CARDS,
            activeAuraColor = activeAuraColor,
        )

        Spacer(modifier = Modifier.weight(1f))

        SupportTierPicker(
            selectedIndex = pagerState.currentPage,
            tiers = SUPPORT_TIER_CARDS,
            onSelect = onSelectTier,
        )

        Spacer(modifier = Modifier.height(14.dp))

        SupportCtaSection(
            tier = activeTier,
            activeColor = activeAuraColor,
        )

        Spacer(modifier = Modifier.weight(1f))

        SupportMissionCard(
            activeColor = activeAuraColor,
            totalListeningHours = totalListeningHours,
        )

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportTopAppBar(
    onBack: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "Support boxlore",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = GoogleSansWeight.bold,
                    fontSize = 18.sp,
                    letterSpacing = 0.3.sp,
                ),
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
        actions = {
            Spacer(modifier = Modifier.width(48.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SupportLightningAtmosphere(
    tier: SupportTierCardData,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val tierPowerFraction = ((tier.energySegments - 1) / 4f).coerceIn(0f, 1f)
    val lightningScale by animateFloatAsState(
        targetValue = 0.78f + (0.54f * tierPowerFraction),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lightning_scale",
    )
    val lightningSpeed by animateFloatAsState(
        targetValue = 0.80f + (0.65f * tierPowerFraction),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lightning_speed",
    )
    val lightningAlpha by animateFloatAsState(
        targetValue = 0.20f + (0.32f * tierPowerFraction),
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "lightning_alpha",
    )

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.lightning_ambient),
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        speed = lightningSpeed,
        iterations = LottieConstants.IterateForever,
    )
    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR_FILTER,
            value = PorterDuffColorFilter(activeColor.toArgb(), PorterDuff.Mode.SRC_ATOP),
            keyPath = arrayOf("**"),
        ),
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        dynamicProperties = dynamicProperties,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .graphicsLayer {
                scaleX = lightningScale
                scaleY = lightningScale
                alpha = lightningAlpha
            },
    )
}

@Composable
private fun CosmicAtmosphereCanvas(
    auraColor: Color,
    pulse: Float,
    particleProgress: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val baseRadius = size.width * 0.48f

        // Rich vertical atmospheric wash radiating into deep black from top
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.40f * pulse),
                    auraColor.copy(alpha = 0.18f * pulse),
                    auraColor.copy(alpha = 0.04f * pulse),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = size.height * 0.85f,
            ),
        )

        // Focused radiant epicenter bloom behind stage
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.32f * pulse),
                    auraColor.copy(alpha = 0.08f * pulse),
                    Color.Transparent,
                ),
                center = Offset(centerX, size.height * 0.45f),
                radius = baseRadius * 1.30f,
            ),
            center = Offset(centerX, size.height * 0.45f),
            radius = baseRadius * 1.30f,
        )

        // Energy particles emerging from items and floating UPWARDS
        val particleCount = 20
        val startY = size.height * 0.65f
        val riseDistance = size.height * 0.55f

        for (i in 0 until particleCount) {
            val seed = (i * 73.17f) % 360f
            val rad = seed * (PI.toFloat() / 180f)
            val progress = (particleProgress + i.toFloat() / particleCount) % 1f
            val spreadFactor = 0.35f + (0.65f * progress)
            val spreadX = (baseRadius * 0.85f * spreadFactor) * cos(rad)
            val wobble = sin(progress * 2.5f * PI.toFloat() + seed) * 12.dp.toPx()
            val px = centerX + spreadX + wobble
            val py = startY - (progress * riseDistance)
            val alpha = sin(progress * PI.toFloat()).coerceIn(0f, 1f) * 0.85f

            if (alpha > 0.02f) {
                drawCircle(
                    color = auraColor.copy(alpha = alpha * 0.50f),
                    radius = (2.2.dp + (1.2.dp * (1f - progress))).toPx(),
                    center = Offset(px, py),
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.95f),
                    radius = (1.0.dp + (0.4.dp * (1f - progress))).toPx(),
                    center = Offset(px, py),
                )
            }
        }
    }
}

@Composable
private fun SupportArtifactStage(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "unit_levitation_transition")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "unit_levitation",
    )
    val platformRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "platform_rotation",
    )
    val platformPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "platform_pulse",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center,
        ) {
            SupportLightningAtmosphere(
                tier = tiers[pagerState.currentPage],
                activeColor = activeAuraColor,
                modifier = Modifier.requiredSize(440.dp),
            )

            // Static Stalinist codename backdrop behind platform & floating items
            ItemBackdropCodename(
                tier = tiers[pagerState.currentPage],
                modifier = Modifier.fillMaxSize(),
            )

            val pagePosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 4f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTransformingPlatform(
                    pagePosition = pagePosition,
                    rotation = platformRotation,
                    pulse = platformPulse,
                    tiers = tiers,
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val tier = tiers[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val signedOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val clampedOffset = pageOffset.coerceIn(0f, 1f)
                val levitationWeight = 1f - clampedOffset

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            cameraDistance = 14f * density
                            rotationY = -26f * signedOffset.coerceIn(-1f, 1f)
                            rotationZ = -4.5f * signedOffset.coerceIn(-1f, 1f)
                            translationX = signedOffset * -16.dp.toPx()
                            translationY = (clampedOffset * 14.dp.toPx()) + (floatOffset.dp.toPx() * levitationWeight)
                            val scale = lerp(1f, 0.72f, clampedOffset)
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.22f, clampedOffset)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = tier.iconRes),
                        contentDescription = tier.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(170.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        SupportStageTierInfo(
            selectedIndex = pagerState.currentPage,
            tiers = tiers,
        )
    }
}

@Composable
private fun ItemBackdropCodename(
    tier: SupportTierCardData,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = tier,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "backdrop_codename_crossfade",
        modifier = modifier,
    ) { currentTier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = currentTier.codenameLine1,
                fontFamily = StalinistOneFontFamily,
                fontSize = 42.sp,
                lineHeight = 44.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Start,
                color = currentTier.auraColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
            )
            Text(
                text = currentTier.codenameLine2,
                fontFamily = StalinistOneFontFamily,
                fontSize = 52.sp,
                lineHeight = 54.sp,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.End,
                color = currentTier.auraColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
            )
        }
    }
}

@Composable
private fun SupportStageTierInfo(
    selectedIndex: Int,
    tiers: List<SupportTierCardData>,
) {
    val currentTier = tiers[selectedIndex]

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { width -> width / 3 } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { width -> -width / 3 } + fadeOut(tween(160)),
                        )
                } else {
                    (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { width -> -width / 3 } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { width -> width / 3 } + fadeOut(tween(160)),
                        )
                }
            },
            label = "tier_stage_details",
        ) { index ->
            val targetTier = tiers[index]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (targetTier.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = targetTier.auraColor,
                        modifier = Modifier.padding(bottom = 5.dp),
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

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = targetTier.auraColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = targetTier.powerImpact,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold),
                        color = targetTier.auraColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Position indicator bars stay static below item name & power impact text
        SupportSegmentedGauge(
            segments = currentTier.energySegments,
            activeColor = currentTier.auraColor,
        )
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
                animationSpec = tween(100, easing = FastOutSlowInEasing),
                label = "gauge_segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(segmentColor),
            )
        }
    }
}

internal fun getSupportMissionText(totalListeningHours: Long?): String =
    if (totalListeningHours != null && totalListeningHours >= 5L) {
        "Software that helps people learn, listen, and explore should simply exist without a catch. " +
            "If boxlore has been a great companion across your $totalListeningHours hours of listening, " +
            "backing a power cell helps keep the app free and ad-free for everyone."
    } else {
        "Software that helps people learn, listen, and explore should simply exist without a catch. " +
            "No ads, no paywalls. Backing a power cell helps fund our servers and keep boxlore open for everyone."
    }

@Composable
private fun SupportMissionCard(
    activeColor: Color,
    totalListeningHours: Long?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0D0D14),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.22f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(activeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = "A QUIET COMPANION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = GoogleSansWeight.bold,
                        letterSpacing = 1.1.sp,
                        fontSize = 10.sp,
                    ),
                    color = activeColor,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Keeping boxlore fast, open, and ad-free",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = GoogleSansWeight.bold,
                    fontSize = 16.5.sp,
                    letterSpacing = (-0.2).sp,
                ),
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(8.dp))

            val missionText = remember(totalListeningHours) {
                buildAnnotatedString {
                    append("Software that helps people learn, listen, and explore should simply exist without a catch. ")
                    if (totalListeningHours != null && totalListeningHours >= 5L) {
                        append("If boxlore has been a great companion across your ")
                        withStyle(
                            SpanStyle(
                                fontWeight = GoogleSansWeight.bold,
                                color = Color.White,
                            ),
                        ) {
                            append("$totalListeningHours hours")
                        }
                        append(" of listening, backing a power cell helps keep the app free and ad-free for everyone.")
                    } else {
                        append("No ads, no paywalls. Backing a power cell helps fund our servers and keep boxlore open for everyone.")
                    }
                }
            }

            Text(
                text = missionText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                color = Color(0xFFA6A6B4),
            )
        }
    }
}

internal data class SupportTierCardData(
    val title: String,
    val shortDuration: String,
    val powerImpact: String,
    val energySegments: Int,
    val cost: String,
    @DrawableRes val iconRes: Int,
    val auraColor: Color,
    val isFeatured: Boolean = false,
    val codenameLine1: String,
    val codenameLine2: String,
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
        codenameLine1 = "PROTOCOL",
        codenameLine2 = "ONE",
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers the boxlore servers for 8 hours",
        shortDuration = "8h",
        energySegments = 2,
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
        auraColor = Color(0xFF00E676),
        codenameLine1 = "PROJECT",
        codenameLine2 = "FLUX",
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers the boxlore servers for 1 full day",
        shortDuration = "1d",
        energySegments = 3,
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
        auraColor = Color(0xFFFFB300),
        codenameLine1 = "GOLDEN",
        codenameLine2 = "RELAY",
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers the boxlore servers for 3 full days",
        shortDuration = "3d",
        energySegments = 4,
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
        auraColor = Color(0xFF8B5CF6),
        codenameLine1 = "DARK",
        codenameLine2 = "NEXUS",
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
        codenameLine1 = "ORBIT",
        codenameLine2 = "PRIME",
    ),
)
