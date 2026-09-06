package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ActionPillFilterChip
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.model.Podcast

/**
 * Explore-style genre pills (icons + short labels) reflecting standard and custom podcast genres.
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
    ) {
        if (onNewFolderClick != null) {
            item(key = "action_new_folder") {
                ActionPillFilterChip(
                    label = "New folder",
                    onClick = onNewFolderClick,
                    icon = Icons.Rounded.Add,
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
            PillFilterChip(
                label = genre.label,
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        onGenreChange("All")
                    } else {
                        onGenreChange(genre.value)
                    }
                },
                icon = genre.icon,
                trailingIcon = if (isSelected) Icons.Rounded.Close else null,
            )
        }
    }
}
