package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@Composable
internal fun SupportDevelopmentPage(
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = 2) { SUPPORT_TIER_CARDS.size }
    val coroutineScope = rememberCoroutineScope()
    val activeTier = SUPPORT_TIER_CARDS[pagerState.currentPage]

    SettingsScaffold(
        title = "Support us",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SupportIntroHeader()

            SupportStage(
                pagerState = pagerState,
                tiers = SUPPORT_TIER_CARDS,
            )

            SupportDialSelector(
                selectedIndex = pagerState.currentPage,
                tiers = SUPPORT_TIER_CARDS,
                onSelect = { page ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(page)
                    }
                },
            )

            SupportSpecCard(tier = activeTier)

            SupportCtaButton(tier = activeTier)
        }
    }
}

@Composable
private fun SupportIntroHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "COMMUNITY POWER GRID",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
            )
        }

        Text(
            text = "Keep boxlore open & ad-free",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "100% listener supported. Zero ads, zero tracking walls. Deploy an energy unit to fuel our servers, AI compute, and development.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun SupportStage(
    pagerState: PagerState,
    tiers: List<SupportTierCardData>,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            ) { page ->
                val tier = tiers[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val scale = lerp(1f, 0.82f, pageOffset.coerceIn(0f, 1f))
                            scaleX = scale
                            scaleY = scale
                            alpha = lerp(1f, 0.4f, pageOffset.coerceIn(0f, 1f))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = tier.iconRes),
                        contentDescription = tier.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 170.dp, height = 190.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentTier = tiers[pagerState.currentPage]

            AnimatedContent(
                targetState = currentTier,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "tier_stage_details",
            ) { tier ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (tier.isFeatured) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp),
                        ) {
                            Text(
                                text = "SUPREME PATRON UNIT",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }

                    Text(
                        text = tier.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SupportSegmentedGauge(
                        segments = tier.energySegments,
                        powerImpact = tier.powerImpact,
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportSegmentedGauge(
    segments: Int,
    powerImpact: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (i in 1..5) {
                val isFilled = i <= segments
                val color by animateColorAsState(
                    targetValue = if (isFilled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    label = "gauge_segment",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = powerImpact,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SupportDialSelector(
    selectedIndex: Int,
    tiers: List<SupportTierCardData>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tiers.forEachIndexed { index, tier ->
            val isSelected = index == selectedIndex
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                animationSpec = tween(200),
                label = "dial_container",
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(200),
                label = "dial_content",
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(index) },
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tier.shortDuration,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
                        color = contentColor,
                    )
                    Text(
                        text = tier.cost,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportSpecCard(
    tier: SupportTierCardData,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "INFRASTRUCTURE IMPACT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = tier.scope,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = tier.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun SupportCtaButton(
    tier: SupportTierCardData,
) {
    Button(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (tier.isFeatured) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (tier.isFeatured) Icons.Rounded.Favorite else Icons.Rounded.Bolt,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Deploy ${tier.title} • ${tier.cost}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
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
    val cost: String,
    @DrawableRes val iconRes: Int,
    val isFeatured: Boolean = false,
)

internal val SUPPORT_TIER_CARDS = listOf(
    SupportTierCardData(
        title = "Micro Energy Cell",
        powerImpact = "Powers 3 Hours",
        shortDuration = "3h",
        energySegments = 1,
        scope = "Sync & Feed Ingestion",
        description = "A swift power boost keeping background podcast feeds, sync triggers, and catalog lookups buzzing.",
        cost = "$0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers 8 Hours",
        shortDuration = "8h",
        energySegments = 2,
        scope = "Catalog Caching",
        description = "Dedicated operational power fueling an entire shift of episode metadata and search caching.",
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers 1 Full Day",
        shortDuration = "1d",
        energySegments = 3,
        scope = "AI & Vector Search",
        description = "Full 24-hour infrastructure capacity powering AI transcript models, search vectors, and streaming nodes.",
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers 3 Full Days",
        shortDuration = "3d",
        energySegments = 4,
        scope = "Database Cluster",
        description = "High-load computational power dedicated to database cluster reliability and fast episode downloads.",
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
    ),
    SupportTierCardData(
        title = "Orbital Quantum Beacon",
        powerImpact = "Powers 1 Full Week",
        shortDuration = "7d",
        energySegments = 5,
        scope = "Global Grid",
        description = "Supreme patron beacon: keeps the entire global boxlore infrastructure open, ad-free, and blazing fast for a full week.",
        cost = "$17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        isFeatured = true,
    ),
)
