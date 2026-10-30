package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.icon.GenreIcons
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** Top of a folder flow page: close or back, a title, an optional "Step 1 of 3" and step bars. */
@Composable
internal fun FolderFlowTopBar(
    title: String,
    onBack: () -> Unit,
    showClose: Boolean,
    step: Int? = null,
    stepCount: Int? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = if (showClose) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = if (showClose) "Close" else "Back",
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.semiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (step != null && stepCount != null && stepCount > 1) {
                Text(
                    text = "Step ${step + 1} of $stepCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action?.invoke()
        }
        if (step != null && stepCount != null && stepCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(stepCount) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
            }
        }
    }
}

/** Bottom bar of a folder flow page: what is picked so far, then the next action. */
@Composable
internal fun FolderFlowBottomBar(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 80.dp)
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.semiBold)
                }
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(text = primaryLabel)
            }
        }
    }
}

/** Name field with the folder's icon beside it; the helper says when the name is saved differently. */
@Composable
internal fun FolderNameField(
    name: String,
    onNameChange: (String) -> Unit,
    iconKey: String?,
    modifier: Modifier = Modifier,
    helper: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = GenreIcons.folderIconOrFallback(iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = "Name") },
            singleLine = true,
            supportingText = helper?.let { { Text(text = it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        )
    }
}

/** One plain line with the auto icon: how a genre folder keeps itself filled. */
@Composable
internal fun AutoAddNote(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AutoFolderMark(size = 18.dp, modifier = Modifier.padding(top = 1.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every folder icon, six to a row. */
@Composable
internal fun FolderIconGrid(
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GenreIcons.all.chunked(ICONS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val selected = item.key.equals(selectedKey, ignoreCase = true)
                    Surface(
                        onClick = { onSelect(item.key) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .semantics {
                                contentDescription = item.label
                                this.selected = selected
                                role = Role.RadioButton
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
                repeat(ICONS_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

private const val ICONS_PER_ROW = 6
