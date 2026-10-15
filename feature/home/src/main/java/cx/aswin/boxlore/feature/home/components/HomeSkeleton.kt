package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardSkeleton
import cx.aswin.boxlore.core.designsystem.components.FeedPosterSpacing
import cx.aswin.boxlore.core.designsystem.theme.m3Shimmer
import kotlin.math.roundToInt

/**
 * Skeletal Loader for Home Screen.
 * Uses Material 3 Shimmer effect (Gray Pulse) to indicate data loading.
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(HomeFeedSpacing.GridGap),
        verticalItemSpacing = 16.dp,
        modifier = modifier.fillMaxSize(),
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

/**
 * Mirrors the real hero carousel: one large card and the narrow peek beside it, at the carousel's
 * own widths (see [heroSkeletonGeometry]). The large card is a solid surface
 * carrying the card's own structure, so content settles in place instead of replacing a flat block.
 * Plain layouts only: nothing here subcomposes on a cold start.
 */
@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Layout(
        content = {
            HeroSkeletonCard(surface)
            Box(Modifier.clip(HomeHeroStyle.CardShape).background(surface))
        },
        modifier = modifier.fillMaxWidth().height(homeHeroHeight()).testTag("home_hero_skeleton"),
    ) { measurables, constraints ->
        val geometry = heroSkeletonGeometry(constraints.maxWidth, density)
        val height = constraints.maxHeight
        val card = measurables[0].measure(Constraints.fixed(geometry.cardWidth, height))
        val peek = measurables[1].measure(Constraints.fixed(geometry.peekWidth, height))
        layout(constraints.maxWidth, height) {
            card.placeRelative(0, 0)
            peek.placeRelative(geometry.cardWidth + geometry.gap, 0)
        }
    }
}

internal data class HeroSkeletonGeometry(val cardWidth: Int, val peekWidth: Int, val gap: Int)

/**
 * The carousel aims for a 320dp card after a 16dp gap and keeps its peek between 40dp and 56dp, so
 * narrow phones shrink the card and wide phones grow it.
 */
internal fun heroSkeletonGeometry(width: Int, density: Float): HeroSkeletonGeometry {
    fun px(value: androidx.compose.ui.unit.Dp) = (value.value * density).roundToInt()
    val gap = px(HeroSkeletonGap)
    val peek = (width - gap - px(HeroSkeletonPreferredCard)).coerceIn(px(HeroSkeletonMinPeek), px(HeroSkeletonMaxPeek))
    val card = (width - gap - peek).coerceAtLeast(0)
    return HeroSkeletonGeometry(cardWidth = card, peekWidth = (width - card - gap).coerceAtLeast(0), gap = gap)
}

private val HeroSkeletonGap = 16.dp
private val HeroSkeletonPreferredCard = 320.dp
private val HeroSkeletonMinPeek = 40.dp
private val HeroSkeletonMaxPeek = 56.dp

@Composable
private fun HeroSkeletonCard(surface: Color) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val line = RoundedCornerShape(6.dp)
    Column(Modifier.clip(HomeHeroStyle.CardShape).background(surface)) {
        // Header: context lines beside the square artwork, which sits flush in the top end corner.
        Row(Modifier.fillMaxWidth().aspectRatio(1f / 0.65f)) {
            Column(
                Modifier.weight(1f).padding(start = 16.dp, end = 12.dp, top = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.fillMaxWidth(0.85f).height(12.dp).m3Shimmer(base, highlight, line))
                Box(Modifier.fillMaxWidth(0.55f).height(12.dp).m3Shimmer(base, highlight, line))
            }
            Box(Modifier.fillMaxHeight().aspectRatio(1f).m3Shimmer(base, highlight, HomeHeroStyle.AttachedArtworkShape))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.fillMaxWidth(0.92f).height(22.dp).m3Shimmer(base, highlight, line))
            Box(Modifier.fillMaxWidth(0.66f).height(22.dp).m3Shimmer(base, highlight, line))
            Box(Modifier.padding(top = 3.dp).fillMaxWidth(0.42f).height(14.dp).m3Shimmer(base, highlight, line))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.weight(1f).height(48.dp).m3Shimmer(base, highlight, HeroSkeletonPlayShape))
            Box(Modifier.width(64.dp).height(48.dp).m3Shimmer(base, highlight, HeroSkeletonDetailsShape))
        }
    }
}

private val HeroSkeletonPlayShape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 8.dp, bottomEnd = 8.dp)
private val HeroSkeletonDetailsShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 26.dp, bottomEnd = 26.dp)

@Composable
fun RisingSkeleton() {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Column {
        // Header handled by real component now
        Spacer(modifier = Modifier.height(12.dp))

        // Rail
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(HomeFeedSpacing.RailItemGap),
        ) {
            items(4) {
                Box(
                    modifier =
                    Modifier
                        .fillParentMaxWidth(HomeFeedSpacing.RAIL_CARD_WIDTH_FRACTION)
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.large)
                        .m3Shimmer(baseColor, highlightColor, shape = MaterialTheme.shapes.large),
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

fun androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope.GridSkeletonItems() {
    items(6) {
        HomeMediaCardSkeleton()
    }
}

@Composable
fun GridSkeletonItem(modifier: Modifier = Modifier) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // Image area skeleton matching aspect ratio 1f
            Box(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
            )

            // Text area skeleton matching title-only FeedMediaCard grid foot
            Column(
                modifier =
                Modifier
                    .height(FeedPosterSpacing.textFootHeight(3) + HomeFeedSpacing.CardTextPadding * 2)
                    .padding(HomeFeedSpacing.CardTextPadding),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Title line 1
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp)),
                )
                // Title line 2
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
fun SkeletonBlock(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(4.dp),
    baseColor: androidx.compose.ui.graphics.Color,
    highlightColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier =
        Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .m3Shimmer(baseColor, highlightColor, shape = shape),
    )
}

@Composable
fun YourShowsSkeleton(
    subscribedCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Column(modifier = modifier.padding(bottom = 16.dp)) {
        // Section Header Row
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = 120.dp, height = 24.dp, baseColor = baseColor, highlightColor = highlightColor)
            }
            Box(
                modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .m3Shimmer(baseColor, highlightColor, shape = CircleShape),
            )
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(homeMixModuleColor())) {
            val count = if (subscribedCount == 0) 10 else subscribedCount + if (subscribedCount > 1) 1 else 0
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(YourShowsLayout.Padding),
            ) {
                val tileWidth = ((maxWidth - YourShowsLayout.Gap * 4) / 5).coerceAtMost(YourShowsLayout.TileWidth)
                Column(verticalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap)) {
                    val rows = if (subscribedCount == 0 || subscribedCount > 4) 2 else 1
                    repeat(rows) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(YourShowsLayout.Gap)) {
                            repeat((count - row * 5).coerceIn(0, 5)) { column ->
                                Column(
                                    modifier = Modifier.width(if (rows == 1) YourShowsLayout.TileWidth else tileWidth)
                                        .height(if (rows == 1) YourShowsLayout.TileHeight else tileWidth).padding(YourShowsLayout.TilePadding)
                                        .testTag("home_show_skeleton_${row}_$column"),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Box(
                                        Modifier.widthIn(max = YourShowsLayout.CoverSize).fillMaxWidth().aspectRatio(1f).m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(15.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            YourShowsDivider()
            Column(Modifier.padding(top = HomeMixLayout.TopPadding, bottom = HomeMixLayout.BottomPadding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = HomeMixLayout.ContentInset).height(HomeMixLayout.HeaderHeight), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(width = 132.dp, height = 34.dp, baseColor = baseColor, highlightColor = highlightColor)
                    SkeletonBlock(width = 88.dp, height = HomeMixLayout.HeaderHeight, baseColor = baseColor, highlightColor = highlightColor)
                }
                Spacer(Modifier.height(HomeMixLayout.HeaderGap))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val cardWidth = HomeMixLayout.railCardWidth(maxWidth)
                    // A plain row, clipped at the edge like the real rail, avoids a lazy layout on cold start.
                    Row(
                        Modifier.fillMaxWidth().clipToBounds().wrapContentWidth(Alignment.Start, unbounded = true)
                            .padding(horizontal = HomeMixLayout.ContentInset),
                        horizontalArrangement = Arrangement.spacedBy(HomeMixLayout.CardGap),
                    ) {
                        repeat(3) {
                            Box(
                                Modifier.width(cardWidth).height(homeMixCardHeight())
                                    .testTag("home_mix_skeleton_$it")
                                    .m3Shimmer(baseColor, highlightColor, shape = RoundedCornerShape(24.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One loading rail occupies the same keyed section as its eventual editorial row. */
@Composable
fun EditorialRowSkeleton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLast: Boolean = false,
    modifier: Modifier = Modifier,
    tone: HomeChildHeaderTone = HomeChildHeaderTone.PRIMARY,
) {
    Column(
        modifier = modifier.padding(bottom = if (isLast) 0.dp else HomeFeedSpacing.RelatedRailGap - HomeFeedSpacing.GridGap),
        verticalArrangement = Arrangement.spacedBy(HomeFeedSpacing.HeaderContentGap),
    ) {
        HomeChildSectionHeader(title = title, icon = icon, tone = tone)
        HomeDiscoveryRail { cardWidth ->
            items(4) {
                HomeMediaCardSkeleton(modifier = Modifier.width(cardWidth))
            }
        }
    }
}

@Composable
internal fun HomeMediaCardSkeleton(
    modifier: Modifier = Modifier,
    presentation: FeedMediaCardPresentation = FeedMediaCardPresentation.ExpressivePoster,
) {
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    FeedMediaCardSkeleton(modifier = modifier, presentation = presentation) { placeholderModifier ->
        Box(placeholderModifier.m3Shimmer(baseColor, highlightColor))
    }
}
