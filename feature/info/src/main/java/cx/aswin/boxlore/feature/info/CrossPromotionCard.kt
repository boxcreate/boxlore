package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.drawOutline
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.CrossPromotionConfidence
import cx.aswin.boxlore.core.model.ResolvedCrossPromotion

@Composable
fun CrossPromotionCard(crossPromotion: ResolvedCrossPromotion, onPodcastClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val podcast = crossPromotion.targetPodcast ?: return
    val shape = RoundedCornerShape(28.dp)
    Surface(
        modifier.fillMaxWidth().semantics { role = Role.Button }.expressiveClickable(shape = shape) { onPodcastClick(podcast.id) },
        shape = shape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.crossPromotionBackdrop(MaterialTheme.colorScheme.onTertiaryContainer).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OptimizedImage(
                podcast.imageUrl,
                contentDescription = null,
                proxyWidth = 320,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(if (crossPromotion.confidence == CrossPromotionConfidence.HIGH) R.string.episode_info_featured_show else R.string.episode_info_featured_show_possible),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(podcast.title, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (podcast.artist.isNotBlank()) {
                    Text(podcast.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.episode_info_explore_podcast), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Modifier.crossPromotionBackdrop(color: Color): Modifier {
    val upperShape = MaterialShapes.Puffy.toShape()
    val lowerShape = MaterialShapes.Cookie4Sided.toShape()
    return drawWithCache {
        val upperSize = 152.dp.toPx()
        val lowerSize = 116.dp.toPx()
        val upperOutline = upperShape.createOutline(Size(upperSize, upperSize), layoutDirection, this)
        val lowerOutline = lowerShape.createOutline(Size(lowerSize, lowerSize), layoutDirection, this)
        onDrawBehind {
            clipRect {
                scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
                    translate(left = size.width - upperSize * .55f, top = -upperSize * .4f) {
                        rotate(18f, pivot = Offset(upperSize / 2, upperSize / 2)) {
                            drawOutline(upperOutline, color, alpha = .04f)
                        }
                    }
                    translate(left = -lowerSize * .4f, top = size.height - lowerSize * .5f) {
                        rotate(-16f, pivot = Offset(lowerSize / 2, lowerSize / 2)) {
                            drawOutline(lowerOutline, color, alpha = .025f)
                        }
                    }
                }
            }
        }
    }
}
