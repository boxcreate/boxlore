package cx.aswin.boxcast.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.designsystem.theme.m3Shimmer

/**
 * Skeletal Loader for Home Screen.
 * Uses Material 3 Shimmer effect (Gray Pulse) to indicate data loading.
 */
@Composable
fun HomeSkeleton(
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 16.dp,
        modifier = modifier.fillMaxSize()
    ) {
        // 1. Hero Skeleton
        item(span = StaggeredGridItemSpan.FullLine) {
            HeroSkeleton()
        }

        // 2. On The Rise Skeleton
        item(span = StaggeredGridItemSpan.FullLine) {
            RisingSkeleton()
        }

        // 3. Grid Skeleton
        GridSkeletonItems()
    }
}

@Composable
fun HeroSkeleton() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Header Text lines
        Spacer(modifier = Modifier.height(16.dp))
        SkeletonBlock(width = 120.dp, height = 20.dp, baseColor = baseColor, highlightColor = highlightColor) // "TOP IN..."
        Spacer(modifier = Modifier.height(12.dp))
        
        // Hero Card (Just Shimmer, No Shapes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(MaterialTheme.shapes.extraLarge)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.extraLarge)
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun RisingSkeleton() {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column {
        // Header handled by real component now
        Spacer(modifier = Modifier.height(12.dp))

        // Rail
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(4) { 
                Column {
                    // Cover Art Shimmer
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.large)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBlock(width = 100.dp, height = 16.dp, baseColor = baseColor, highlightColor = highlightColor)
                    Spacer(modifier = Modifier.height(4.dp))
                    SkeletonBlock(width = 60.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.GridSkeletonItems() {
    items(6) { index ->
        val isTall = index % 3 == 0
        GridSkeletonItem(isTall = isTall)
    }
}

@Composable
fun GridSkeletonItem(isTall: Boolean) {
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTall) 280.dp else 220.dp)
                .clip(MaterialTheme.shapes.large)
                .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large)
        )
        Spacer(modifier = Modifier.height(8.dp))
        SkeletonBlock(width = 80.dp, height = 16.dp, baseColor = baseColor, highlightColor = highlightColor)
        Spacer(modifier = Modifier.height(4.dp))
        SkeletonBlock(width = 50.dp, height = 12.dp, baseColor = baseColor, highlightColor = highlightColor)
    }
}

@Composable
fun SkeletonBlock(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp),
    baseColor: androidx.compose.ui.graphics.Color,
    highlightColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
                .m3Shimmer(baseColor, highlightColor, shape = shape)
    )
}
