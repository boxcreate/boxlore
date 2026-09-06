package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder

internal data class FolderEditFormState(
    val isEditing: Boolean,
    val canSave: Boolean,
    val nameText: String,
    val selectedIconKey: String?,
    val selectedDisplaySize: FolderDisplaySize,
    val autoSyncGenre: Boolean,
    val effectiveLinkedGenre: String?,
    val suggestedGenres: List<String>,
)

internal data class FolderEditFormActions(
    val onNameChange: (String) -> Unit,
    val onSelectIcon: (String?) -> Unit,
    val onSelectDisplaySize: (FolderDisplaySize) -> Unit,
    val onAutoSyncChange: (Boolean) -> Unit,
    val onSelectLinkedGenre: (String) -> Unit,
    val onSelectSuggestedGenre: (String) -> Unit,
    val onDone: () -> Unit,
    val onSave: () -> Unit,
    val onClose: () -> Unit,
    val onDelete: (() -> Unit)?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderEditSheet(
    initialFolder: SubscriptionFolder? = null,
    suggestedGenres: List<String> = emptyList(),
    onDismissRequest: () -> Unit,
    onSave: (name: String, icon: String?, displaySize: FolderDisplaySize, linkedGenre: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var nameText by remember(initialFolder) { mutableStateOf(initialFolder?.name ?: "") }
    var selectedIconKey by remember(initialFolder) { mutableStateOf(initialFolder?.icon) }
    var isIconManuallySelected by remember(initialFolder) {
        mutableStateOf(initialFolder?.icon != null)
    }
    var selectedDisplaySize by remember(initialFolder) {
        mutableStateOf(initialFolder?.displaySize ?: FolderDisplaySize.COMPACT)
    }
    var autoSyncGenre by remember(initialFolder) {
        mutableStateOf(initialFolder?.isGenreLinked ?: false)
    }
    var linkedGenreText by remember(initialFolder) {
        mutableStateOf(initialFolder?.linkedGenre ?: "")
    }

    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = initialFolder != null
    val canSave = nameText.trim().isNotEmpty()

    val effectiveLinkedGenre = if (autoSyncGenre) {
        linkedGenreText.trim().ifEmpty { nameText.trim() }
    } else {
        null
    }

    val formState = FolderEditFormState(
        isEditing = isEditing,
        canSave = canSave,
        nameText = nameText,
        selectedIconKey = selectedIconKey,
        selectedDisplaySize = selectedDisplaySize,
        autoSyncGenre = autoSyncGenre,
        effectiveLinkedGenre = effectiveLinkedGenre,
        suggestedGenres = suggestedGenres,
    )

    val formActions = FolderEditFormActions(
        onNameChange = { nameText = it },
        onSelectIcon = {
            selectedIconKey = it
            isIconManuallySelected = true
            focusManager.clearFocus()
        },
        onSelectDisplaySize = { selectedDisplaySize = it },
        onAutoSyncChange = { enabled ->
            autoSyncGenre = enabled
        },
        onSelectLinkedGenre = { genre ->
            linkedGenreText = if (linkedGenreText.equals(genre, ignoreCase = true)) "" else genre
        },
        onSelectSuggestedGenre = { genre ->
            nameText = genre
            if (!isIconManuallySelected) {
                val matchedIcon = GenreIcons.findIcon(genre) ?: GenreIcons.defaultGenreIcon(genre)
                val item = GenreIcons.all.firstOrNull { it.icon == matchedIcon }
                selectedIconKey = item?.key
            }
            focusManager.clearFocus()
        },
        onDone = { focusManager.clearFocus() },
        onSave = {
            focusManager.clearFocus()
            val finalName = nameText.trim()
            val finalIcon = selectedIconKey?.trim()?.takeIf { it.isNotEmpty() }
            val finalLinked = if (autoSyncGenre) {
                linkedGenreText.trim().ifEmpty { finalName }.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            onSave(finalName, finalIcon, selectedDisplaySize, finalLinked)
        },
        onClose = onDismissRequest,
        onDelete = onDelete,
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentWindowInsets = { WindowInsets.navigationBars },
        modifier = Modifier.imePadding(),
    ) {
        FolderEditSheetContent(
            state = formState,
            actions = formActions,
        )
    }
}

@Composable
internal fun FolderEditSheetContent(
    state: FolderEditFormState,
    actions: FolderEditFormActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        FolderEditTopBar(
            isEditing = state.isEditing,
            canSave = state.canSave,
            onClose = actions.onClose,
            onDelete = actions.onDelete,
            onSave = actions.onSave,
        )

        FolderEditLivePreview(
            name = state.nameText,
            iconKey = state.selectedIconKey,
            displaySize = state.selectedDisplaySize,
            linkedGenre = state.effectiveLinkedGenre,
        )

        FolderNameInputField(
            nameText = state.nameText,
            onNameChange = actions.onNameChange,
            iconKey = state.selectedIconKey,
            onDone = actions.onDone,
        )

        if (state.suggestedGenres.isNotEmpty()) {
            FolderQuickFillChipsRow(
                genres = state.suggestedGenres,
                onSelectGenre = actions.onSelectSuggestedGenre,
            )
        }

        FolderDisplaySizeSelector(
            selectedSize = state.selectedDisplaySize,
            onSizeSelected = actions.onSelectDisplaySize,
        )

        FolderAutoSyncToggle(
            autoSync = state.autoSyncGenre,
            onAutoSyncChange = actions.onAutoSyncChange,
            linkedGenre = state.effectiveLinkedGenre ?: state.nameText.trim(),
            suggestedGenres = state.suggestedGenres,
            onSelectLinkedGenre = actions.onSelectLinkedGenre,
        )

        FolderIconPickerSection(
            selectedIconKey = state.selectedIconKey,
            queryText = state.nameText,
            onSelectIcon = actions.onSelectIcon,
        )
    }
}
