package cx.aswin.boxlore.feature.library.playlists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.library.R
import cx.aswin.boxlore.feature.library.subscriptions.resolveSubscriptionGenreItem

/** A suggestion as a solid colour block: its icon on an expressive cookie, the shows' covers, a name and count. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SuggestionCard(suggestion: PlaylistSuggestion, name: String, tone: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    val icon = GenreIcons.findIcon(suggestion.iconKey) ?: when (suggestion.kind) {
        PlaylistSuggestion.Kind.GENRE -> resolveSubscriptionGenreItem(suggestion.label.orEmpty()).icon
        else -> GenreIcons.defaultFolderIcon()
    }
    val count = PlaylistCopy.showCount(suggestion.podcastIds.size).asString()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag("playlist_suggestion_${suggestion.key}"),
    ) {
        Column(Modifier.heightIn(min = 164.dp).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(52.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(content),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = container, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.weight(1f))
                CoverStack(suggestion.covers, ring = container)
            }
            Spacer(Modifier.weight(1f).heightIn(min = 20.dp))
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Up to three covers overlapping in a row, each ringed in the card's colour. */
@Composable
private fun CoverStack(covers: List<String>, ring: Color, size: Dp = 30.dp) {
    val shown = covers.take(StackCovers)
    Box(Modifier.size(width = size + (size * 0.6f) * (shown.size - 1).coerceAtLeast(0), height = size)) {
        shown.forEachIndexed { index, url ->
            OptimizedImage(
                url = url,
                contentDescription = null,
                proxyWidth = 96,
                contentScale = ContentScale.Crop,
                showLoadingPlaceholder = false,
                modifier = Modifier
                    .offset(x = size * 0.6f * index)
                    .size(size)
                    .clip(CircleShape)
                    .border(2.dp, ring, CircleShape),
            )
        }
    }
}

@Composable
internal fun PickOwnCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("playlist_pick_own"),
    ) {
        Row(Modifier.heightIn(min = 76.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.library_playlist_start_own), style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                Text(stringResource(R.string.library_playlist_start_own_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private const val StackCovers = 3
