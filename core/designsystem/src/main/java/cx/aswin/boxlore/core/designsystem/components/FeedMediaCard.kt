package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/**
 * Layout density for discovery poster cards.
 * Title-only posters reserve a fixed foot ([Rail]: 2 lines, [Grid]: 3) and vertically
 * center shorter titles so rows stay equal height.
 */
enum class FeedMediaCardDensity {
    Rail,
    Grid,
}

@Composable
@Suppress("LongParameterList")
fun FeedMediaCard(
    imageUrl: String,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2,
    imageBadge: @Composable (BoxScope.() -> Unit)? = null,
    imageOverlay: @Composable (BoxScope.() -> Unit)? = null,
) {
    val lines = titleMaxLines.coerceAtLeast(1)
    val showSubtitle = !subtitle.isNullOrBlank()

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.expressiveClickable(onClick = onClick),
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            ) {
                OptimizedImage(
                    url = imageUrl,
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                )

                if (imageBadge != null) {
                    imageBadge()
                }

                if (imageOverlay != null) {
                    imageOverlay()
                }
            }

            if (!showSubtitle) {
                // Equal-height posters: reserve [lines] title lines; vertically center shorter titles.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                FeedPosterSpacing.textFootHeight(lines) +
                                    FeedPosterSpacing.CardTextPadding * 2,
                            )
                            .padding(FeedPosterSpacing.CardTextPadding),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(FeedPosterSpacing.CardTextPadding),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
