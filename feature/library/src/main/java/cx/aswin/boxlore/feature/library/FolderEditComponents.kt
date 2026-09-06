package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.icon.GenreIconItem
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.FolderDisplaySize

@Composable
internal fun FolderEditTopBar(
    isEditing: Boolean,
    canSave: Boolean,
    onClose: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }

        Text(
            text = if (isEditing) "Edit folder" else "New folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isEditing && onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.medium,
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = canSave,
                shape = ExpressiveShapes.Pill,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (isEditing) "Save" else "Create",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        }
    }
}

@Composable
internal fun FolderEditLivePreview(
    name: String,
    iconKey: String?,
    displaySize: FolderDisplaySize,
    linkedGenre: String?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Live preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = GoogleSansWeight.bold,
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = when (displaySize) {
                            FolderDisplaySize.COMPACT -> "Compact 1×1"
                            FolderDisplaySize.FEATURED -> "Featured 2×2"
                            FolderDisplaySize.SHELF -> "Shelf 3×1"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = GoogleSansWeight.bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = GenreIcons.folderIconOrFallback(iconKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = name.trim().ifEmpty { "Folder name" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (name.trim().isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = GoogleSansWeight.bold,
                    )
                    if (!linkedGenre.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "Auto-syncs with $linkedGenre",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FolderNameInputField(
    nameText: String,
    onNameChange: (String) -> Unit,
    iconKey: String?,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = nameText,
        onValueChange = onNameChange,
        label = { Text("Folder name") },
        placeholder = { Text("e.g. Daily Tech, Comedy, Weekend Mix") },
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = GenreIcons.folderIconOrFallback(iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            if (nameText.isNotEmpty()) {
                IconButton(onClick = { onNameChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "Clear name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun FolderQuickFillChipsRow(
    genres: List<String>,
    onSelectGenre: (String) -> Unit,
) {
    if (genres.isEmpty()) return

    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Quick-fill from subscriptions",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = GoogleSansWeight.bold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.forEach { genre ->
                val icon = GenreIcons.iconOrFallback(null, genre)
                PillFilterChip(
                    label = genre,
                    icon = icon,
                    selected = false,
                    onClick = { onSelectGenre(genre) },
                )
            }
        }
    }
}

@Composable
internal fun FolderDisplaySizeSelector(
    selectedSize: FolderDisplaySize,
    onSizeSelected: (FolderDisplaySize) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Display size",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = GoogleSansWeight.bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FolderDisplaySizeOption(
                title = "Compact",
                subtitle = "1×1 grid",
                selected = selectedSize == FolderDisplaySize.COMPACT,
                onClick = { onSizeSelected(FolderDisplaySize.COMPACT) },
                modifier = Modifier.weight(1f),
            )
            FolderDisplaySizeOption(
                title = "Featured",
                subtitle = "2×2 spotlight",
                selected = selectedSize == FolderDisplaySize.FEATURED,
                onClick = { onSizeSelected(FolderDisplaySize.FEATURED) },
                modifier = Modifier.weight(1f),
            )
            FolderDisplaySizeOption(
                title = "Shelf",
                subtitle = "3×1 horizontal",
                selected = selectedSize == FolderDisplaySize.SHELF,
                onClick = { onSizeSelected(FolderDisplaySize.SHELF) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FolderDisplaySizeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.bold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun FolderAutoSyncToggle(
    autoSync: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    linkedGenre: String,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Auto-sync with genre",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (autoSync && linkedGenre.isNotBlank()) {
                        "Subscribed shows tagged '$linkedGenre' join automatically"
                    } else {
                        "Automatically add matching subscribed shows to this folder"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Switch(
                checked = autoSync,
                onCheckedChange = onAutoSyncChange,
            )
        }
    }
}

@Composable
internal fun FolderIconPickerSection(
    selectedIconKey: String?,
    queryText: String,
    onSelectIcon: (String?) -> Unit,
) {
    val suggestedIcons = remember(queryText) {
        GenreIcons.suggestIcons(queryText)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Folder icon (optional)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = GoogleSansWeight.bold,
            )

            if (!selectedIconKey.isNullOrBlank()) {
                TextButton(
                    onClick = { onSelectIcon(null) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Clear icon",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = GoogleSansWeight.medium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "Default folder icon",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (suggestedIcons.isNotEmpty()) {
            Text(
                text = "Suggested icons",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestedIcons.forEach { item ->
                    val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                    FolderIconChip(
                        item = item,
                        isSelected = isSelected,
                        onClick = {
                            onSelectIcon(if (isSelected) null else item.key)
                        },
                    )
                }
            }
        }

        Text(
            text = "All icons",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GenreIcons.all.forEach { item ->
                val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                FolderIconChip(
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

@Composable
private fun FolderIconChip(
    item: GenreIconItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(item.label) },
        leadingIcon = {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        trailingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
        shape = ExpressiveShapes.Pill,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
