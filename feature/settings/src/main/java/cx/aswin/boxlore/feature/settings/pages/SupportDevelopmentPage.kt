package cx.aswin.boxlore.feature.settings.pages

import android.app.Activity
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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

    LaunchedEffect(Unit) {
        AnalyticsHelper.trackSupportPageViewed()
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { page ->
                val tier = SUPPORT_TIER_CARDS[page]
                AnalyticsHelper.trackSupportTierToggled(
                    tierTitle = tier.title,
                    codename = "${tier.codenameLine1} ${tier.codenameLine2}",
                    amount = tier.cost,
                    isLoreInspect = false,
                )
            }
    }

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
            onDonateClick = { tier ->
                AnalyticsHelper.trackSupportDonateClicked(
                    tierTitle = tier.title,
                    codename = "${tier.codenameLine1} ${tier.codenameLine2}",
                    amount = tier.cost,
                )
            },
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
private fun SupportArtifactStage(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
) {
    val haptic = LocalHapticFeedback.current
    var isStoryMode by remember(pagerState.currentPage) { mutableStateOf(false) }
    val storyProgress by animateFloatAsState(
        targetValue = if (isStoryMode) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "story_progress",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StageDisplayBox(
            pagerState = pagerState,
            tiers = tiers,
            activeAuraColor = activeAuraColor,
            storyProgress = storyProgress,
            onStageClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val nextStoryMode = !isStoryMode
                isStoryMode = nextStoryMode
                val currentTier = tiers[pagerState.currentPage]
                AnalyticsHelper.trackSupportTierToggled(
                    tierTitle = currentTier.title,
                    codename = "${currentTier.codenameLine1} ${currentTier.codenameLine2}",
                    amount = currentTier.cost,
                    isLoreInspect = nextStoryMode,
                )
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        SupportStageTierInfo(
            selectedIndex = pagerState.currentPage,
            tiers = tiers,
        )
    }
}

@Composable
private fun StageDisplayBox(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    activeAuraColor: Color,
    storyProgress: Float,
    onStageClick: () -> Unit,
    modifier: Modifier = Modifier,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onStageClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SupportLightningAtmosphere(
            tier = tiers[pagerState.currentPage],
            activeColor = activeAuraColor,
            modifier = Modifier.requiredSize(440.dp),
        )

        val pagePosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 4f)

        // Transforming Stalinist codename backdrop behind platform & floating items
        ItemBackdropCodename(
            pagePosition = pagePosition,
            tiers = tiers,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - storyProgress
                },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTransformingPlatform(
                pagePosition = pagePosition,
                rotation = platformRotation,
                pulse = platformPulse,
                tiers = tiers,
                storyProgress = storyProgress,
            )
        }

        SupportArtifactPager(
            pagerState = pagerState,
            tiers = tiers,
            storyProgress = storyProgress,
            floatOffset = floatOffset,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SupportArtifactPager(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
    storyProgress: Float,
    floatOffset: Float,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
    ) { page ->
        val tier = tiers[page]
        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
        val signedOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val clampedOffset = pageOffset.coerceIn(0f, 1f)
        val levitationWeight = 1f - clampedOffset

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (storyProgress < 0.99f) {
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
                            alpha = lerp(1f, 0.22f, clampedOffset) * (1f - storyProgress)
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

            if (storyProgress > 0.01f) {
                ArtifactLoreStoryContent(
                    tier = tier,
                    storyProgress = storyProgress,
                    clampedOffset = clampedOffset,
                )
            }
        }
    }
}

@Composable
private fun ArtifactLoreStoryContent(
    tier: SupportTierCardData,
    storyProgress: Float,
    clampedOffset: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .graphicsLayer {
                alpha = storyProgress * (1f - clampedOffset)
                val s = 0.93f + (0.07f * storyProgress)
                scaleX = s
                scaleY = s
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(tier.auraColor, CircleShape),
            )
            Text(
                text = "${tier.codenameLine1} ${tier.codenameLine2}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = GoogleSansWeight.bold,
                    letterSpacing = 2.sp,
                    fontSize = 11.sp,
                ),
                color = tier.auraColor,
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(tier.auraColor, CircleShape),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = tier.loreDescription,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = GoogleSansWeight.medium,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.3.sp,
            ),
            color = Color.White.copy(alpha = 0.95f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "TAP TO RETURN",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = GoogleSansWeight.semiBold,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
            ),
            color = Color.White.copy(alpha = 0.40f * storyProgress),
        )
    }
}

@Composable
private fun ItemBackdropCodename(
    pagePosition: Float,
    tiers: List<SupportTierCardData>,
    modifier: Modifier = Modifier,
) {
    val leftIndex = pagePosition.toInt().coerceIn(0, tiers.lastIndex)
    val rightIndex = (leftIndex + 1).coerceAtMost(tiers.lastIndex)
    val fraction = (pagePosition - leftIndex).coerceIn(0f, 1f)

    Box(modifier = modifier) {
        if (fraction < 0.01f) {
            SingleTierCodename(
                tier = tiers[leftIndex],
                alpha = 1f,
                scale = 1f,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fraction > 0.99f) {
            SingleTierCodename(
                tier = tiers[rightIndex],
                alpha = 1f,
                scale = 1f,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val leftAlpha = (1f - fraction).coerceIn(0f, 1f)
            val rightAlpha = fraction.coerceIn(0f, 1f)
            val leftScale = 1f - (0.08f * fraction)
            val rightScale = 0.92f + (0.08f * fraction)

            SingleTierCodename(
                tier = tiers[leftIndex],
                alpha = leftAlpha,
                scale = leftScale,
                modifier = Modifier.fillMaxSize(),
            )
            SingleTierCodename(
                tier = tiers[rightIndex],
                alpha = rightAlpha,
                scale = rightScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SingleTierCodename(
    tier: SupportTierCardData,
    alpha: Float,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    if (alpha <= 0.01f) return

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = tier.codenameLine1,
            fontFamily = StalinistOneFontFamily,
            fontSize = 42.sp,
            lineHeight = 44.sp,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Start,
            color = tier.auraColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        )
        Text(
            text = tier.codenameLine2,
            fontFamily = StalinistOneFontFamily,
            fontSize = 52.sp,
            lineHeight = 54.sp,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.End,
            color = tier.auraColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp),
        )
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

internal fun buildSupportMissionAnnotatedString(totalListeningHours: Long?): AnnotatedString =
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

internal fun getSupportMissionText(totalListeningHours: Long?): String =
    buildSupportMissionAnnotatedString(totalListeningHours).text

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
                buildSupportMissionAnnotatedString(totalListeningHours)
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
