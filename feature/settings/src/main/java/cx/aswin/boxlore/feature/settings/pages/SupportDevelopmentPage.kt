package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

@Composable
internal fun SupportDevelopmentPage(
    onBack: () -> Unit,
) {
    SettingsScaffold(
        title = "Support us",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SupportIntroSection()

            Text(
                text = "Power Tiers",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            SUPPORT_TIER_CARDS.forEach { tier ->
                SupportTierCard(tier = tier)
            }
        }
    }
}

@Composable
private fun SupportIntroSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Keep boxlore ad-free for everyone",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = "boxlore is built with dedication to remain completely free of ads and paywalls for listeners worldwide. Your support directly powers continuous app development, cloud infrastructure, AI intelligence, and bandwidth.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
    }
}

internal data class SupportTierCardData(
    val title: String,
    val powerImpact: String,
    val description: String,
    val cost: String,
    @DrawableRes val iconRes: Int,
    val isFeatured: Boolean = false,
)

internal val SUPPORT_TIER_CARDS = listOf(
    SupportTierCardData(
        title = "Micro Energy Cell",
        powerImpact = "Powers 3 Hours",
        description = "A swift boost to keep indexing and cloud sync buzzing.",
        cost = "$0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers 8 Hours",
        description = "Provides dedicated power for an entire shift of backend processing.",
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers 1 Full Day",
        description = "Fully fuels an entire day of streaming, sync, and discovery.",
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers 3 Full Days",
        description = "Substantial infrastructure support keeping databases running fast.",
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
    ),
    SupportTierCardData(
        title = "Orbital Quantum Beacon",
        powerImpact = "Powers 1 Full Week",
        description = "Legendary patron tier keeping the whole global boxlore network online.",
        cost = "$17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        isFeatured = true,
    ),
)

@Composable
private fun SupportTierCard(
    tier: SupportTierCardData,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tier.isFeatured) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (tier.isFeatured) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            SupportTierHeader(tier = tier)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = tier.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SupportTierActionButton(tier = tier)
        }
    }
}

@Composable
private fun SupportTierHeader(
    tier: SupportTierCardData,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = tier.iconRes),
            contentDescription = tier.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 68.dp, height = 76.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            if (tier.isFeatured) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Text(
                        text = "SUPREME PATRON",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text = tier.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = tier.powerImpact,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = GoogleSansWeight.semiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportTierActionButton(
    tier: SupportTierCardData,
) {
    if (tier.isFeatured) {
        Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Support ${tier.cost}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
            )
        }
    } else {
        FilledTonalButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Support ${tier.cost}",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
            )
        }
    }
}
