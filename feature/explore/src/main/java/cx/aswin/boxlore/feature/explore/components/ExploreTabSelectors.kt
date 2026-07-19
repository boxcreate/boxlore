package cx.aswin.boxlore.feature.explore.components

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
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.explore.SearchTab

@Composable
fun ExploreTabSelectorFab(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalWidth = 240.dp
    val padding = 4.dp
    val spacing = 4.dp
    
    // totalWidth (240) - 2 * padding (8) - spacing (4) = 228
    // 228 / 2 = 114.dp per tab
    val tabWidth = 114.dp
    val tabHeight = 36.dp

    val targetOffset = if (selectedTab == 1) 0.dp else tabWidth + spacing
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.65f, // Premium bouncy feel
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tab_indicator_offset"
    )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.width(totalWidth)
    ) {
        Box(
            modifier = Modifier.padding(padding)
        ) {
            // Sliding selection pill indicator
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .height(tabHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )

            // Row containing the tab buttons on top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "For You" Tab (index 1)
                val isForYouSelected = selectedTab == 1
                val forYouContentColor by animateColorAsState(
                    targetValue = if (isForYouSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "foryou_content"
                )
                
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(tabHeight)
                        .expressiveClickable(shape = CircleShape) {
                            onTabSelected(1)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = forYouContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "For You",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = forYouContentColor
                        )
                    }
                }

                // "Top" Tab (index 0)
                val isTopSelected = selectedTab == 0
                val topContentColor by animateColorAsState(
                    targetValue = if (isTopSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "top_content"
                )

                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .height(tabHeight)
                        .expressiveClickable(shape = CircleShape) {
                            onTabSelected(0)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = topContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Top",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = topContentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTabSelector(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalWidth = 240.dp
    val padding = 4.dp
    val spacing = 4.dp
    val tabWidth = 114.dp
    val tabHeight = 36.dp

    val targetOffset = if (selectedTab == SearchTab.EPISODES) tabWidth + spacing else 0.dp
    val animatedOffset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = 0.65f, // Premium bouncy feel
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "search_tab_offset"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.width(totalWidth)
        ) {
            Box(
                modifier = Modifier.padding(padding)
            ) {
                // Sliding selection pill indicator
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(tabWidth)
                        .height(tabHeight)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )

                // Row containing the tab buttons on top
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Shows" Tab (index 0 / SearchTab.SHOWS)
                    val isShowsSelected = selectedTab == SearchTab.SHOWS
                    val showsContentColor by animateColorAsState(
                        targetValue = if (isShowsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "shows_content"
                    )
                    
                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(tabHeight)
                            .expressiveClickable(shape = CircleShape) {
                                onTabSelected(SearchTab.SHOWS)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Shows",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = showsContentColor
                        )
                    }

                    // "Episodes (AI)" Tab (index 1 / SearchTab.EPISODES)
                    val isEpisodesSelected = selectedTab == SearchTab.EPISODES
                    val episodesContentColor by animateColorAsState(
                        targetValue = if (isEpisodesSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "episodes_content"
                    )

                    Box(
                        modifier = Modifier
                            .width(tabWidth)
                            .height(tabHeight)
                            .expressiveClickable(shape = CircleShape) {
                                onTabSelected(SearchTab.EPISODES)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = episodesContentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = episodesContentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
