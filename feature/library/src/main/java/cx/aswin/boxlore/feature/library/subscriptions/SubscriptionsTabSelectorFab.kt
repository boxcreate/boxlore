package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

val SubscriptionsTabSelectorFabHeight = 44.dp

@Composable
fun SubscriptionsTabSelectorFab(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    badgeCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val totalWidth = 264.dp
    val padding = 4.dp
    val spacing = 4.dp
    val tabWidth = 126.dp
    val tabHeight = 36.dp

    val targetOffset = if (selectedTab == 0) 0.dp else tabWidth + spacing
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "subscriptions_tab_indicator_offset",
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.width(totalWidth),
    ) {
        Box(
            modifier = Modifier.padding(padding),
        ) {
            // Sliding selection pill indicator
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .height(tabHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                    ),
            )

            // Row containing the tab buttons on top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShowsTabButton(
                    isSelected = selectedTab == 0,
                    tabWidth = tabWidth,
                    tabHeight = tabHeight,
                    onClick = { onTabSelected(0) },
                )

                LatestTabButton(
                    isSelected = selectedTab == 1,
                    tabWidth = tabWidth,
                    tabHeight = tabHeight,
                    badgeCount = badgeCount,
                    onClick = { onTabSelected(1) },
                )
            }
        }
    }
}

@Composable
private fun ShowsTabButton(
    isSelected: Boolean,
    tabWidth: androidx.compose.ui.unit.Dp,
    tabHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val showsContentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "shows_content_color",
    )

    Box(
        modifier = Modifier
            .width(tabWidth)
            .height(tabHeight)
            .expressiveClickable(shape = CircleShape) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.GridView,
                contentDescription = null,
                tint = showsContentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Shows",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.bold,
                color = showsContentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun LatestTabButton(
    isSelected: Boolean,
    tabWidth: androidx.compose.ui.unit.Dp,
    tabHeight: androidx.compose.ui.unit.Dp,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val latestContentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "latest_content_color",
    )

    Box(
        modifier = Modifier
            .width(tabWidth)
            .height(tabHeight)
            .expressiveClickable(shape = CircleShape) {
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = latestContentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "New Eps",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.bold,
                color = latestContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LatestTabBadge(
                badgeCount = badgeCount,
                isSelected = isSelected,
            )
        }
    }
}

@Composable
private fun LatestTabBadge(
    badgeCount: Int,
    isSelected: Boolean,
) {
    if (badgeCount <= 0) return

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    val badgeText = if (badgeCount > 99) "99+" else badgeCount.toString()

    Spacer(modifier = Modifier.width(4.dp))
    Badge(
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
