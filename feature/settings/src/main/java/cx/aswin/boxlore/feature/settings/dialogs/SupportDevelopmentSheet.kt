package cx.aswin.boxlore.feature.settings.dialogs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupportDevelopmentSheet(
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Support boxlore",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Independent, 100% ad-free & tracker-free",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Why we ask for support",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = GoogleSansWeight.semiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "boxlore runs completely independent without corporate tracking or invasive ads. Keeping the search indexing, databases, and multi-device cloud sync servers online costs approximately \u20B96,000 (~$72 USD) every month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Upcoming Server Power Tiers",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.semiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Start),
            )

            Spacer(modifier = Modifier.height(10.dp))

            SUPPORT_TIERS.forEach { tier ->
                SupportTierPreviewRow(
                    tier = tier,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Direct tipping via Google Play is coming in the next update. Thank you for listening with boxlore!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Got It")
            }
        }
    }
}

internal data class SupportTierItem(
    val duration: String,
    val title: String,
    val cost: String,
    val icon: ImageVector,
)

internal val SUPPORT_TIERS = listOf(
    SupportTierItem(
        duration = "3 Hours",
        title = "Micro Energy Cell",
        cost = "\u20B925 / $0.49",
        icon = Icons.Rounded.Bolt,
    ),
    SupportTierItem(
        duration = "8 Hours",
        title = "Field Battery Pack",
        cost = "\u20B969 / $0.99",
        icon = Icons.Rounded.BatteryChargingFull,
    ),
    SupportTierItem(
        duration = "1 Day",
        title = "Power Station",
        cost = "\u20B9199 / $2.49",
        icon = Icons.Rounded.Storage,
    ),
    SupportTierItem(
        duration = "3 Days",
        title = "Server Tower",
        cost = "\u20B9599 / $6.99",
        icon = Icons.Rounded.CloudSync,
    ),
    SupportTierItem(
        duration = "1 Week",
        title = "Orbital Quantum Beacon",
        cost = "\u20B91,499 / $17.99",
        icon = Icons.Rounded.Star,
    ),
)

@Composable
private fun SupportTierPreviewRow(
    tier: SupportTierItem,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = tier.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = tier.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = GoogleSansWeight.semiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Powers ${tier.duration} of server time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = tier.cost,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
