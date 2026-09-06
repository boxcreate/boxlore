package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
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
        placeholder = { Text("e.g. Daily Tech, Comedy, Deep Dives") },
        singleLine = true,
        leadingIcon = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(28.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = GenreIcons.folderIconOrFallback(iconKey),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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
        shape = RoundedCornerShape(16.dp),
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Suggestions from your library",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = GoogleSansWeight.medium,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Grid footprint",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = GoogleSansWeight.bold,
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "${selectedSize.dimensionsLabel} • ${selectedSize.title}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = GoogleSansWeight.bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FolderDisplaySize.entries.forEach { size ->
                FolderDisplaySizeCard(
                    size = size,
                    isSelected = selectedSize == size,
                    onClick = { onSizeSelected(size) },
                )
            }
        }
    }
}

@Composable
private fun FolderDisplaySizeCard(
    size: FolderDisplaySize,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier.width(76.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniGridFootprintDiagram(
                spanCols = size.spanCols,
                spanRows = size.spanRows,
                isSelected = isSelected,
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = size.dimensionsLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = size.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MiniGridFootprintDiagram(
    spanCols: Int,
    spanRows: Int,
    isSelected: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (r in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                for (c in 0 until 3) {
                    val isActive = c < spanCols && r < spanRows
                    val cellColor = when {
                        isActive && isSelected -> MaterialTheme.colorScheme.primary
                        isActive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    }
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(cellColor),
                    )
                }
            }
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

    val displayIcons = remember(suggestedIcons) {
        val suggestedKeys = suggestedIcons.map { it.key }.toSet()
        suggestedIcons + GenreIcons.all.filterNot { it.key in suggestedKeys }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Icon (optional)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
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
                        text = "Reset to default",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = GoogleSansWeight.medium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            } else {
                Text(
                    text = "Default folder icon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderDefaultIconTile(
                isSelected = selectedIconKey.isNullOrBlank(),
                onClick = { onSelectIcon(null) },
            )

            displayIcons.forEach { item ->
                val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                FolderIconTile(
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
private fun FolderDefaultIconTile(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = "Default folder icon",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FolderIconTile(
    item: GenreIconItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun FolderAutoSyncCard(
    autoSync: Boolean,
    onAutoSyncChange: (Boolean) -> Unit,
    linkedGenre: String,
    suggestedGenres: List<String> = emptyList(),
    onSelectLinkedGenre: ((String) -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Auto-sync with genre",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = GoogleSansWeight.bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (autoSync && linkedGenre.isNotBlank()) {
                                "Shows tagged '$linkedGenre' join automatically"
                            } else {
                                "Automatically adds matching subscribed shows"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Switch(
                    checked = autoSync,
                    onCheckedChange = onAutoSyncChange,
                )
            }

            if (autoSync && suggestedGenres.isNotEmpty() && onSelectLinkedGenre != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    suggestedGenres.forEach { genre ->
                        val isSelected = linkedGenre.equals(genre, ignoreCase = true)
                        val icon = GenreIcons.iconOrFallback(null, genre)
                        PillFilterChip(
                            label = genre,
                            icon = icon,
                            selected = isSelected,
                            onClick = { onSelectLinkedGenre(genre) },
                        )
                    }
                }
            }
        }
    }
}
