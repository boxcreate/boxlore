package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.PillFilterChip
import cx.aswin.boxlore.core.designsystem.icon.GenreIconItem
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

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
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val effectiveGenre = genreText.trim().ifEmpty { catalogGenre.ifEmpty { "Podcast" } }
    val effectiveIcon: ImageVector = GenreIcons.findIcon(selectedIconKey)
        ?: GenreIcons.defaultGenreIcon(effectiveGenre)

    val hasCustomizations = !customGenre.isNullOrBlank() || !customGenreIcon.isNullOrBlank()
    val isDirty = genreText.trim() != (customGenre ?: "") || selectedIconKey != customGenreIcon
    val canReset = hasCustomizations || genreText.isNotBlank() || selectedIconKey != null
    val canSave = isDirty || (genreText.isNotBlank() && !hasCustomizations)

    val suggestedIcons = remember(genreText) {
        findSuggestedIcons(genreText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.navigationBars },
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GenreEditTopBar(
                onClose = onDismissRequest,
                canReset = canReset,
                onReset = {
                    focusManager.clearFocus()
                    onSave(null, null)
                    onDismissRequest()
                },
                canSave = canSave,
                onSave = {
                    focusManager.clearFocus()
                    val finalGenre = genreText.trim().takeIf { it.isNotEmpty() }
                    onSave(finalGenre, selectedIconKey)
                    onDismissRequest()
                },
            )

            GenreEditTagCard(
                genreText = genreText,
                catalogGenre = catalogGenre,
                effectiveIcon = effectiveIcon,
                onValueChange = { genreText = it },
                onDone = { focusManager.clearFocus() },
            )

            GenreEditSuggestionsRow(
                genreText = genreText,
                onSelectSuggestion = { suggestion ->
                    genreText = suggestion.name
                    selectedIconKey = suggestion.iconKey
                    focusManager.clearFocus()
                },
            )

            if (suggestedIcons.isNotEmpty()) {
                GenreEditSuggestedIconsRow(
                    suggestedIcons = suggestedIcons,
                    selectedIconKey = selectedIconKey,
                    onSelectIcon = { key ->
                        focusManager.clearFocus()
                        selectedIconKey = if (selectedIconKey.equals(key, ignoreCase = true)) null else key
                    },
                )
            }

            GenreEditAllIconsGrid(
                selectedIconKey = selectedIconKey,
                onSelectIcon = { key ->
                    focusManager.clearFocus()
                    selectedIconKey = if (selectedIconKey.equals(key, ignoreCase = true)) null else key
                },
            )
        }
    }
}

@Composable
private fun GenreEditTopBar(
    onClose: () -> Unit,
    canReset: Boolean,
    onReset: () -> Unit,
    canSave: Boolean,
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
            text = "Tag & Icon",
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
            if (canReset) {
                TextButton(
                    onClick = onReset,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Reset",
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
                    text = "Save",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = GoogleSansWeight.bold,
                )
            }
        }
    }
}

@Composable
private fun GenreEditTagCard(
    genreText: String,
    catalogGenre: String,
    effectiveIcon: ImageVector,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = effectiveIcon,
                            contentDescription = "Selected tag icon",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TAG NAME",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = GoogleSansWeight.bold,
                    )
                    BasicTextField(
                        value = genreText,
                        onValueChange = onValueChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = GoogleSansWeight.bold,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onDone() }),
                        decorationBox = { innerTextField ->
                            if (genreText.isEmpty()) {
                                Text(
                                    text = catalogGenre.ifEmpty { "e.g. Deep Dives, Favorites" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    )
                }

                if (genreText.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = "Clear tag input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        if (catalogGenre.isNotBlank() && genreText.isNotBlank()) {
            Text(
                text = "Default catalog genre: $catalogGenre",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun GenreEditSuggestionsRow(
    genreText: String,
    onSelectSuggestion: (GenreSuggestion) -> Unit,
) {
    val suggestions = remember(genreText) {
        filterGenreSuggestions(genreText)
    }

    if (suggestions.isEmpty()) return

    val scrollState = rememberScrollState()
    LaunchedEffect(genreText) {
        scrollState.scrollTo(0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (genreText.isBlank()) "Quick suggestions" else "Matching tags (${suggestions.size})",
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
            suggestions.forEach { suggestion ->
                val isSelected = genreText.equals(suggestion.name, ignoreCase = true)
                PillFilterChip(
                    label = suggestion.name,
                    icon = suggestion.icon,
                    selected = isSelected,
                    onClick = { onSelectSuggestion(suggestion) },
                )
            }
        }
    }
}

@Composable
private fun GenreEditSuggestedIconsRow(
    suggestedIcons: List<GenreIconItem>,
    selectedIconKey: String?,
    onSelectIcon: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Suggested icons",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = GoogleSansWeight.bold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            suggestedIcons.forEach { item ->
                val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                GenreIconCell(
                    item = item,
                    isSelected = isSelected,
                    onClick = { onSelectIcon(item.key) },
                )
            }
        }
    }
}

@Composable
private fun GenreEditAllIconsGrid(
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
                text = "All icons",
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

        val (row1, row2) = remember {
            val half = (GenreIcons.all.size + 1) / 2
            GenreIcons.all.take(half) to GenreIcons.all.drop(half)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row1.forEach { item ->
                    val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                    GenreIconCell(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onSelectIcon(if (isSelected) null else item.key) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row2.forEach { item ->
                    val isSelected = selectedIconKey.equals(item.key, ignoreCase = true)
                    GenreIconCell(
                        item = item,
                        isSelected = isSelected,
                        onClick = { onSelectIcon(if (isSelected) null else item.key) },
                    )
                }
            }
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

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.size(44.dp),
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
