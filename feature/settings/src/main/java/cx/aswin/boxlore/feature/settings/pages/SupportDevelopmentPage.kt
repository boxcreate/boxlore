package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        SupportHeroHeader()

        Text(
            text = "Power Tiers",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            text = "Choose how you would like to energize the project",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        SUPPORT_TIER_CARDS.forEach { tier ->
            SupportTierCard(tier = tier)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        SupportComingSoonFooter()
    }
}

@Composable
private fun SupportHeroHeader() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(64.dp),
                shadowElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Keep boxlore ad-free & open for everyone",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "boxlore is built with dedication to create a truly great podcast experience. No paywalls, no banner clutter, and zero audio ads—ever. Your support directly fuels ongoing development, cloud infrastructure, AI models, and bandwidth so boxlore stays open, beautiful, and free for all listeners.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                PillarBadge(icon = Icons.Rounded.Block, label = "100% Ad-Free")
                PillarBadge(icon = Icons.Rounded.AllInclusive, label = "Free Forever")
                PillarBadge(icon = Icons.Rounded.RocketLaunch, label = "Indie Built")
            }
        }
    }
}

@Composable
private fun SupportComingSoonFooter() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = "In-App Tipping Coming Soon",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Direct Google Play tipping will be available in the upcoming update. Thank you for listening with boxlore!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PillarBadge(
    icon: ImageVector,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
        cost = "\u20B925 / $0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers 8 Hours",
        description = "Provides dedicated power for an entire shift of backend processing.",
        cost = "\u20B969 / $0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers 1 Full Day",
        description = "Fully fuels an entire day of streaming, sync, and discovery.",
        cost = "\u20B9199 / $2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers 3 Full Days",
        description = "Substantial infrastructure support keeping databases running fast.",
        cost = "\u20B9599 / $6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
    ),
    SupportTierCardData(
        title = "Orbital Quantum Beacon",
        powerImpact = "Powers 1 Full Week",
        description = "Legendary patron tier keeping the whole global boxlore network online.",
        cost = "\u20B91,499 / $17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        isFeatured = true,
    ),
)

@Composable
private fun SupportTierCard(
    tier: SupportTierCardData,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tier.isFeatured) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 3D Rendered Asset Icon
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(72.dp),
                shadowElevation = 1.dp,
            ) {
                Box(
                    modifier = Modifier.padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = tier.iconRes),
                        contentDescription = tier.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (tier.isFeatured) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Text(
                            text = "SUPREME PATRON",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                Text(
                    text = tier.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = tier.powerImpact,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.semiBold),
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = tier.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Price pill badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = tier.cost,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = GoogleSansWeight.bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
