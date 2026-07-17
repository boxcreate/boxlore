package cx.aswin.boxlore.feature.explore.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer

@Composable
fun ExploreSkeletonLoader() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header Skeleton
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.extraLarge)
            ) // Search Bar
            Spacer(modifier = Modifier.height(16.dp))
            
            // Category Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(32.dp)
                            .clip(MaterialTheme.shapes.small)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.small)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        // Grid Skeleton
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(150.dp),
            verticalItemSpacing = 16.dp,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            exploreSkeletonGridItems()
        }
    }
}

fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.exploreSkeletonGridItems() {
    // Featured Hero Row Skeleton
    item(span = StaggeredGridItemSpan.FullLine) {
        val bColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        val hColor = MaterialTheme.colorScheme.surfaceContainerHighest
        
        Column {
            // Header
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(24.dp)
                    .clip(MaterialTheme.shapes.small)
                .m3Shimmer(bColor, hColor, shape = MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // Row of cards
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(170.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                .m3Shimmer(bColor, hColor, shape = MaterialTheme.shapes.extraLarge)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Section Header
    item(span = StaggeredGridItemSpan.FullLine) {
        val bColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        val hColor = MaterialTheme.colorScheme.surfaceContainerHighest
        
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(24.dp)
                .clip(MaterialTheme.shapes.small)
                .m3Shimmer(bColor, hColor, shape = MaterialTheme.shapes.small)
        )
    }

    // Grid Items (Staggered variable height)
    items(6) { index ->
        val bColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        val hColor = MaterialTheme.colorScheme.surfaceContainerHighest
        val isTall = index % 3 == 0
        
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTall) 280.dp else 220.dp)
                    .clip(MaterialTheme.shapes.large)
                .m3Shimmer(bColor, hColor, shape = MaterialTheme.shapes.large)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                .m3Shimmer(bColor, hColor, shape = MaterialTheme.shapes.extraSmall)
            )
             Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                .m3Shimmer(bColor, hColor, shape = MaterialTheme.shapes.extraSmall)
            )
        }
    }
}
