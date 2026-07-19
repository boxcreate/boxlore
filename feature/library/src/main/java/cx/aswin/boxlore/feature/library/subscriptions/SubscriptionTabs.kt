package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.library.SubscriptionSort

@Composable
internal fun ExpressiveTabSwitcher(
    tabs: List<String>,
    selectedIndex: Int,
    badge: Map<Int, Int> = emptyMap(),
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Transparent)
            .padding(2.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 400f
            ),
            label = "indicatorOffset"
        )
        
        // Bouncy Sliding Indicator (More compact)
        Surface(
            modifier = Modifier
                .width(tabWidth)
                .height(36.dp)
                .offset(x = indicatorOffset),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {}
        
        // Tab Content
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, label ->
                TabItemContent(
                    index = index,
                    label = label,
                    isSelected = index == selectedIndex,
                    badgeCount = badge[index],
                    onTabSelected = onTabSelected
                )
            }
        }
    }
}

@Composable
internal fun RowScope.TabItemContent(
    index: Int,
    label: String,
    isSelected: Boolean,
    badgeCount: Int?,
    onTabSelected: (Int) -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabText"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onTabSelected(index) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
            if (badgeCount != null && badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Badge(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                  else MaterialTheme.colorScheme.surface
                ) {
                    Text("$badgeCount")
                }
            }
        }
    }
}

@Composable
internal fun SubscriptionSortChips(
    currentSort: SubscriptionSort,
    onSortChange: (SubscriptionSort) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.SmartRank,
                onClick = { onSortChange(SubscriptionSort.SmartRank) },
                label = { Text("Smart Rank") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.SmartRank,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.RecentlyUpdated,
                onClick = { onSortChange(SubscriptionSort.RecentlyUpdated) },
                label = { Text("Recently Updated") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.RecentlyUpdated,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.Alphabetical,
                onClick = { onSortChange(SubscriptionSort.Alphabetical) },
                label = { Text("A-Z") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.Alphabetical,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        item {
            FilterChip(
                selected = currentSort == SubscriptionSort.MostListened,
                onClick = { onSortChange(SubscriptionSort.MostListened) },
                label = { Text("Most Listened") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = currentSort == SubscriptionSort.MostListened,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
internal fun SubscriptionGenreChips(
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    distinctGenres: List<String>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedGenre == "All",
                onClick = { onGenreChange("All") },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedGenre == "All",
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        items(distinctGenres) { genre ->
            FilterChip(
                selected = selectedGenre == genre,
                onClick = { onGenreChange(genre) },
                label = { Text(genre) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedGenre == genre,
                    borderColor = Color.Transparent,
                    selectedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

