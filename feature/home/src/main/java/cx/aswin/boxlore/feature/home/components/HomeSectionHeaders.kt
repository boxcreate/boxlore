package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.content.ContentDaypart
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily

enum class HomeChildHeaderTone {
    PRIMARY,
    TERTIARY,
}

/** Chapter identity belongs to the opening and browse action, never a feed-wide panel. */
enum class HomeDiscoveryChapter {
    LIBRARY,
    PERSONAL,
    MOMENT,
    EXPLORE,
}

@Composable
fun HomeTopLevelSectionHeader(
    title: String,
    seeAllContentDescription: String,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    chapter: HomeDiscoveryChapter = HomeDiscoveryChapter.PERSONAL,
    daypart: ContentDaypart? = null,
) {
    var fullyVisible by remember { mutableStateOf(false) }
    val visibilityModifier = if (daypart != null) {
        Modifier.onGloballyPositioned { fullyVisible = it.isFullyVisibleInRoot() }
    } else {
        Modifier
    }
    DiscoveryExpressiveTheme {
        Row(
            modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).then(visibilityModifier),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    daypart?.let { HomeDaypartMark(it, fullyVisible) }
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f).semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = rememberSectionHeaderFontFamily(),
                            fontWeight = GoogleSansWeight.semiBold,
                        ),
                    )
                }
                subtitle?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HomeChapterBrowseButton(chapter, seeAllContentDescription, onSeeAllClick)
        }
    }
}

private fun LayoutCoordinates.isFullyVisibleInRoot(): Boolean {
    if (!isAttached) return false
    val root = findRootCoordinates()
    val fullBounds = root.localBoundingBoxOf(this, clipBounds = false)
    val viewport = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
    val visibleBounds = root.localBoundingBoxOf(this, clipBounds = true).intersect(viewport)
    // Compare the whole header with its ancestor-clipped bounds, allowing subpixel rounding.
    return fullBounds.width > 0f &&
        fullBounds.height > 0f &&
        visibleBounds.left <= fullBounds.left + 0.5f &&
        visibleBounds.top <= fullBounds.top + 0.5f &&
        visibleBounds.right >= fullBounds.right - 0.5f &&
        visibleBounds.bottom >= fullBounds.bottom - 0.5f
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeChapterBrowseButton(
    chapter: HomeDiscoveryChapter,
    description: String,
    onClick: () -> Unit,
    icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward,
) {
    val shape = when (chapter) {
        HomeDiscoveryChapter.LIBRARY -> MaterialShapes.Pill.toShape()
        HomeDiscoveryChapter.PERSONAL -> MaterialShapes.Cookie4Sided.toShape()
        HomeDiscoveryChapter.MOMENT -> MaterialShapes.Sunny.toShape()
        HomeDiscoveryChapter.EXPLORE -> MaterialShapes.Cookie6Sided.toShape()
    }
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shapes = IconButtonDefaults.shapes(shape = shape, pressedShape = shape),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun HomeChildSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tone: HomeChildHeaderTone = HomeChildHeaderTone.PRIMARY,
) {
    val contentColor =
        when (tone) {
            HomeChildHeaderTone.PRIMARY -> MaterialTheme.colorScheme.primary
            HomeChildHeaderTone.TERTIARY -> MaterialTheme.colorScheme.tertiary
        }
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = contentColor)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.semiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
