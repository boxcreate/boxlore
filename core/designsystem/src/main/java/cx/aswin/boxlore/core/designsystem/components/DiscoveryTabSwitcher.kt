package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlin.math.abs

data class DiscoveryTab(val label: String, val icon: ImageVector? = null, val badgeCount: Int = 0)

val DiscoveryTabSwitcherMinimumHeight = 48.dp

/** Shared with page-owned overlay clearance, including enlarged system text. */
@Composable
fun discoveryTabSwitcherHeight(): Dp =
    (with(LocalDensity.current) { 20.sp.toDp() } + 20.dp).coerceAtLeast(DiscoveryTabSwitcherMinimumHeight)

/** Pure presentation: pages own selection, preferences, placement and navigation. */
@Composable
fun DiscoveryTabSwitcher(
    tabs: List<DiscoveryTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
) {
    require(tabs.isNotEmpty())
    val selected = selectedIndex.coerceIn(tabs.indices)
    val height = discoveryTabSwitcherHeight()
    DiscoveryExpressiveTheme {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (floating) 6.dp else 0.dp,
            modifier = modifier.then(if (floating) Modifier.width(280.dp) else Modifier.fillMaxWidth()).height(height),
        ) {
            BoxWithConstraints {
                val tabWidth = maxWidth / tabs.size
                val position by animateFloatAsState(
                    targetValue = selected.toFloat(),
                    animationSpec = spring(dampingRatio = 1f, stiffness = 450f),
                    label = "discovery_tab_selection",
                )
                Box(
                    Modifier.offset(x = tabWidth * position).width(tabWidth).fillMaxHeight().testTag("discovery_tab_indicator")
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                )
                Row(Modifier.fillMaxSize().selectableGroup()) {
                    tabs.forEachIndexed { index, tab ->
                        DiscoveryTabItem(
                            tab = tab,
                            selected = index == selected,
                            coverage = (1f - abs(position - index)).coerceIn(0f, 1f),
                            onClick = { onTabSelected(index) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryTabItem(tab: DiscoveryTab, selected: Boolean, coverage: Float, onClick: () -> Unit, modifier: Modifier) {
    val content = lerp(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onPrimaryContainer, coverage)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier.clip(CircleShape).selectable(
            selected = selected,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        )
            .semantics(mergeDescendants = true) { }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        tab.icon?.let {
            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            tab.label,
            modifier = Modifier.weight(1f, fill = false),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (tab.badgeCount > 0) {
            Spacer(Modifier.width(4.dp))
            Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Text(if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString())
            }
        }
    }
}
