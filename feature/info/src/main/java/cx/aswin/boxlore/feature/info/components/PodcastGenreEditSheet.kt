package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.icon.GenreIconItem
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

private val STANDARD_SUGGESTIONS = listOf(
    "Tech",
    "News",
    "Comedy",
    "True Crime",
    "Business",
    "Society",
    "Science",
    "Music",
    "Sports",
    "History",
    "Education",
    "Health",
    "Fiction",
    "Favorites",
    "Deep Dives",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastGenreEditSheet(
    catalogGenre: String,
    customGenre: String?,
    customGenreIcon: String?,
    onDismissRequest: () -> Unit,
    onSave: (customGenre: String?, customGenreIcon: String?) -> Unit,
) {
    var genreText by remember(customGenre) { mutableStateOf(customGenre ?: "") }
    var selectedIconKey by remember(customGenreIcon) { mutableStateOf(customGenreIcon) }

    val effectiveGenre = genreText.trim().ifEmpty { catalogGenre.ifEmpty { "Podcast" } }
    val effectiveIcon: ImageVector = GenreIcons.findIcon(selectedIconKey)
        ?: GenreIcons.defaultGenreIcon(effectiveGenre)

    val hasCustomizations = !customGenre.isNullOrBlank() || !customGenreIcon.isNullOrBlank()
    val isDirty = genreText.trim() != (customGenre ?: "") || selectedIconKey != customGenreIcon

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Change tag / genre",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
            )

            Text(
                text = "Personalize the tag and icon for this podcast in your library and filter chips.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GenreEditPreviewCard(
                catalogGenre = catalogGenre,
                genreText = genreText,
                effectiveGenre = effectiveGenre,
                effectiveIcon = effectiveIcon,
            )

            OutlinedTextField(
                value = genreText,
                onValueChange = { genreText = it },
                label = { Text("Tag or genre name") },
                placeholder = { Text(catalogGenre.ifEmpty { "e.g. Deep Dives, Favorites" }) },
                singleLine = true,
                trailingIcon = {
                    if (genreText.isNotEmpty()) {
                        IconButton(onClick = { genreText = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear tag input",
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            GenreEditSuggestionsRow(
                genreText = genreText,
                onSelectSuggestion = { suggestion ->
                    genreText = suggestion
                    if (selectedIconKey == null) {
                        val matchedIcon = GenreIcons.findIcon(suggestion)
                        if (matchedIcon != null) {
                            selectedIconKey = suggestion.lowercase()
                        }
                    }
                },
            )

            HorizontalDivider()

            GenreEditIconPicker(
                selectedIconKey = selectedIconKey,
                onSelectIcon = { selectedIconKey = it },
            )

            Spacer(modifier = Modifier.height(8.dp))

            GenreEditActionButtons(
                canReset = hasCustomizations || genreText.isNotBlank() || selectedIconKey != null,
                canSave = isDirty || (genreText.isNotBlank() && !hasCustomizations),
                onReset = {
                    onSave(null, null)
                    onDismissRequest()
                },
                onSave = {
                    val finalGenre = genreText.trim().takeIf { it.isNotEmpty() }
                    onSave(finalGenre, selectedIconKey)
                    onDismissRequest()
                },
            )
        }
    }
}

@Composable
private fun GenreEditPreviewCard(
    catalogGenre: String,
    genreText: String,
    effectiveGenre: String,
    effectiveIcon: ImageVector,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = GoogleSansWeight.bold,
                )
                if (catalogGenre.isNotBlank() && genreText.isNotBlank()) {
                    Text(
                        text = "Default: $catalogGenre",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Surface(
                shape = ExpressiveShapes.Pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = effectiveIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = effectiveGenre,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = GoogleSansWeight.bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreEditSuggestionsRow(
    genreText: String,
    onSelectSuggestion: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = GoogleSansWeight.bold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            STANDARD_SUGGESTIONS.forEach { suggestion ->
                val isSelected = genreText.equals(suggestion, ignoreCase = true)
                PillFilterChip(
                    label = suggestion,
                    selected = isSelected,
                    onClick = { onSelectSuggestion(suggestion) },
                )
            }
        }
    }
}

@Composable
private fun GenreEditIconPicker(
    selectedIconKey: String?,
    onSelectIcon: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Choose an icon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = GoogleSansWeight.bold,
            )
            if (selectedIconKey != null) {
                Text(
                    text = "Clear icon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = GoogleSansWeight.medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSelectIcon(null) }
                        .padding(4.dp),
                )
            }
        }

        val chunkedIcons = remember { GenreIcons.all.chunked(6) }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chunkedIcons.forEach { rowIcons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    rowIcons.forEach { item ->
                        val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                        GenreIconCell(
                            item = item,
                            isSelected = isSelected,
                            onClick = {
                                onSelectIcon(if (isSelected) null else item.key)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreEditActionButtons(
    canReset: Boolean,
    canSave: Boolean,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onReset,
            enabled = canReset,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Reset to default",
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Button(
            onClick = onSave,
            enabled = canSave,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = "Save",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.bold,
            )
        }
    }
}

@Composable
private fun GenreIconCell(
    item: GenreIconItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
        }
    }
}
