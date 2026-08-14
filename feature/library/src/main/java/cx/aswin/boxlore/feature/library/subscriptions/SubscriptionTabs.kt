package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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

        Surface(
            modifier = Modifier
                .width(tabWidth)
                .height(36.dp)
                .offset(x = indicatorOffset),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {}

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
                fontWeight = if (isSelected) GoogleSansWeight.bold else GoogleSansWeight.medium,
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

internal fun showsSortLabel(sort: SubscriptionSort): String = when (sort) {
    SubscriptionSort.SmartRank -> "Smart"
    SubscriptionSort.RecentlyUpdated -> "Updated"
    SubscriptionSort.Alphabetical -> "A–Z"
    SubscriptionSort.MostListened -> "Listened"
    SubscriptionSort.Manual -> "Manual"
}

internal fun latestSortLabel(useSmartRank: Boolean): String =
    if (useSmartRank) "Smart" else "Chronological"

/**
 * Explore-style genre pills only (icons + short labels). Sort / hide-played live in the top bar.
 */
@Composable
internal fun SubscriptionGenreChips(
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    distinctGenres: List<String>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val genreItems = remember(distinctGenres) {
        distinctGenres
            .map { resolveSubscriptionGenreItem(it) }
            .distinctBy { it.value.lowercase() }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            PillFilterChip(
                label = "All",
                selected = selectedGenre == "All",
                onClick = { onGenreChange("All") },
                icon = AllGenreIcon,
            )
        }
        items(genreItems, key = { it.value }) { genre ->
            PillFilterChip(
                label = genre.label,
                selected = selectedGenre.equals(genre.value, ignoreCase = true) ||
                    selectedGenre.equals(genre.label, ignoreCase = true),
                onClick = { onGenreChange(genre.value) },
                icon = genre.icon,
            )
        }
    }
}

@Composable
internal fun ShowsSortMenuItems(
    currentSort: SubscriptionSort,
    onSortChange: (SubscriptionSort) -> Unit,
    onDismiss: () -> Unit
) {
    ShowsSortOption(
        label = "Smart Sort",
        selected = currentSort == SubscriptionSort.SmartRank,
        onClick = {
            onSortChange(SubscriptionSort.SmartRank)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "Recently Updated",
        selected = currentSort == SubscriptionSort.RecentlyUpdated,
        onClick = {
            onSortChange(SubscriptionSort.RecentlyUpdated)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "A-Z",
        selected = currentSort == SubscriptionSort.Alphabetical,
        onClick = {
            onSortChange(SubscriptionSort.Alphabetical)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "Most Listened",
        selected = currentSort == SubscriptionSort.MostListened,
        onClick = {
            onSortChange(SubscriptionSort.MostListened)
            onDismiss()
        }
    )
    ShowsSortOption(
        label = "Manual",
        selected = currentSort == SubscriptionSort.Manual,
        onClick = {
            onSortChange(SubscriptionSort.Manual)
            onDismiss()
        }
    )
}

@Composable
private fun ShowsSortOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
}

@Composable
internal fun LatestSortMenuItems(
    useSmartRank: Boolean,
    onUseSmartRankChange: (Boolean) -> Unit,
    hideCompleted: Boolean,
    onHideCompletedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = { Text("Smart Sort") },
        onClick = {
            onUseSmartRankChange(true)
            onDismiss()
        },
        trailingIcon = {
            if (useSmartRank) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
    DropdownMenuItem(
        text = { Text("Chronological") },
        onClick = {
            onUseSmartRankChange(false)
            onDismiss()
        },
        trailingIcon = {
            if (!useSmartRank) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
    DropdownMenuItem(
        text = { Text("Hide played episodes") },
        onClick = {
            onHideCompletedChange(!hideCompleted)
            onDismiss()
        },
        trailingIcon = {
            if (hideCompleted) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    )
}
