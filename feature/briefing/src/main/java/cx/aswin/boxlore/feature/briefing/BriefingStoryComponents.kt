package cx.aswin.boxlore.feature.briefing

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButton
import cx.aswin.boxlore.core.designsystem.components.ExpressivePlayButtonState
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.SectionHeaderFontFamily
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Episode
import java.net.URI

internal fun formatChapterTime(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(java.util.Locale.US, "%d:%02d", mins, secs)
}

internal fun formatRemaining(totalSeconds: Long): String? {
    if (totalSeconds <= 0) return null
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
}

internal fun getDomainName(url: String): String {
    return try {
        val uri = URI(url)
        val domain = uri.host ?: ""
        if (domain.startsWith("www.")) domain.substring(4) else domain
    } catch (e: Exception) {
        url
    }
}


@Composable
internal fun CompactEpisodeChip(
    episode: Episode,
    isActiveCard: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActiveCard) MaterialTheme.colorScheme.surfaceContainerHighest
                else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            0.5.dp,
            if (isActiveCard) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .width(260.dp)
            .expressiveClickable(
                shape = RoundedCornerShape(16.dp),
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val imageUrl = episode.imageUrl?.takeIf { it.isNotEmpty() }
                ?: episode.podcastImageUrl?.takeIf { it.isNotEmpty() }
            OptimizedImage(
                url = imageUrl,
                proxyWidth = 120,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    ),
                    color = if (isActiveCard) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val podTitle = episode.podcastTitle
                if (!podTitle.isNullOrEmpty()) {
                    Text(
                        text = podTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isActiveCard) accentColor.copy(alpha = 0.85f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val infoText = buildString {
                    if (episode.duration > 0) {
                        append("${episode.duration / 60} min")
                    }
                    if (episode.publishedDate > 0) {
                        if (isNotEmpty()) append(" • ")
                        try {
                            val instant = java.time.Instant.ofEpochSecond(episode.publishedDate)
                            val date = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).toLocalDate()
                            append(date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")))
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                if (infoText.isNotEmpty()) {
                    Text(
                        text = infoText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isActiveCard) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
