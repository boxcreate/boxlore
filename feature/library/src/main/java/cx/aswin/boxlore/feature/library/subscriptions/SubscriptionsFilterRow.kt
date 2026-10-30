package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Podcast

/**
 * New folder, then the genres of the shows you follow, as Explore-style chips. Tapping the picked
 * genre again shows everything.
 */
@Composable
internal fun SubscriptionsFilterRow(
    selectedGenre: String,
    onGenreChange: (String) -> Unit,
    distinctGenres: List<String>,
    podcasts: List<Podcast> = emptyList(),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    onNewFolderClick: (() -> Unit)? = null,
) {
    val genreItems = remember(distinctGenres, podcasts) {
        distinctGenres
            .map { resolveSubscriptionGenreItem(it, podcasts) }
            .distinctBy { it.value.lowercase() }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNewFolderClick != null) {
            item(key = "action_new_folder") {
                CompactNewFolderChip(
                    onClick = onNewFolderClick,
                )
            }
        }
        items(genreItems, key = { it.value }) { genre ->
            val isSelected = !selectedGenre.equals("All", ignoreCase = true) &&
                selectedGenre.isNotBlank() &&
                (
                    selectedGenre.equals(genre.value, ignoreCase = true) ||
                        selectedGenre.equals(genre.label, ignoreCase = true)
                )
            SubscriptionChip(
                label = genre.label,
                selected = isSelected,
                onClick = { onGenreChange(if (isSelected) "All" else genre.value) },
                icon = genre.icon,
            )
        }
    }
}

/** New folder, first in the genre row: the same height and fill as the chips beside it. */
@Composable
internal fun CompactNewFolderChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.CreateNewFolder,
                contentDescription = "New folder",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * The genre chip used across Subscriptions. Mirrors Explore's Top tab chips (filled, no border,
 * 48dp, primary container when picked); no feature-to-feature dependency.
 */
@Composable
internal fun SubscriptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = containerColor,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** An action in a chip row (order, Add shows): the same fill and height as [SubscriptionChip]. */
@Composable
internal fun SubscriptionActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text = label, maxLines = 1) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        trailingIcon = trailingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = null,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
