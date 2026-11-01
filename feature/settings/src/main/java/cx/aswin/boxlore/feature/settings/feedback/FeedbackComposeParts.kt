package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal const val FEEDBACK_MESSAGE_TAG = "feedback_message"
internal const val FEEDBACK_SEND_TAG = "feedback_send"
internal const val FEEDBACK_PILLS_TAG = "feedback_pills"
internal const val FEEDBACK_TYPING_BAR_TAG = "feedback_typing_bar"
internal const val FEEDBACK_PHOTOS_TAG = "feedback_photos"
internal const val FEEDBACK_PRIVACY_URL = "https://boxlore.in/privacy#feedback"

internal val FeedbackCategory.icon: ImageVector
    get() = when (this) {
        FeedbackCategory.FEATURE -> Icons.Rounded.Lightbulb
        FeedbackCategory.BUG -> Icons.Rounded.BugReport
        FeedbackCategory.AUDIO -> Icons.Rounded.Headphones
        FeedbackCategory.OTHER -> Icons.Rounded.ChatBubbleOutline
    }

/** The genre-chip look from Explore: round, 48 dp, icon first, primary container when chosen. */
@Composable
internal fun FeedbackTypePill(category: FeedbackCategory, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(category.label, fontWeight = GoogleSansWeight.semiBold) },
        leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = modifier.heightIn(min = 48.dp),
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackTypePills(selected: FeedbackCategory, onSelect: (FeedbackCategory) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.testTag(FEEDBACK_PILLS_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedbackCategory.entries.forEach { category ->
            FeedbackTypePill(category = category, selected = category == selected, onClick = { onSelect(category) })
        }
    }
}

/** Compact type picker for the typing layout: the chosen pill, opening a menu of the four types. */
@Composable
internal fun FeedbackTypeMenuPill(selected: FeedbackCategory, onSelect: (FeedbackCategory) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .semantics { contentDescription = "Type: ${selected.label}. Change" },
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(selected.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(selected.label, style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.semiBold)
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            FeedbackCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    leadingIcon = { Icon(category.icon, contentDescription = null) },
                    trailingIcon = if (category == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(category)
                        open = false
                    },
                )
            }
        }
    }
}

/** The roomy, softly filled message card; the caller decides its height. */
@Composable
internal fun FeedbackMessageCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxChars: Int,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
            Box(Modifier.weight(1f, fill = true).fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(FEEDBACK_MESSAGE_TAG)
                        .semantics { contentDescription = "Your message" }
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                )
            }
            Text(
                text = "${value.length}/$maxChars",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
internal fun FeedbackPhotoRow(
    photos: List<PickedPhoto>,
    canAdd: Boolean,
    maxPhotos: Int,
    onAdd: () -> Unit,
    onRemove: (PickedPhoto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(FEEDBACK_PHOTOS_TAG),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onAdd,
            enabled = canAdd,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(64.dp)
                .semantics { contentDescription = "Add a photo or screenshot" },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Add", style = MaterialTheme.typography.labelSmall, fontWeight = GoogleSansWeight.semiBold)
            }
        }
        photos.forEachIndexed { index, photo ->
            Box(Modifier.size(64.dp)) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = "Photo ${index + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                Surface(
                    onClick = { onRemove(photo) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(24.dp)
                        .semantics { contentDescription = "Remove photo ${index + 1}" },
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.padding(5.dp))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Up to $maxPhotos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun FeedbackDiagnosticsRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Include device diagnostics",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = "Include device diagnostics" },
        )
    }
}

@Composable
internal fun FeedbackSendButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = "Send feedback") {
    Button(
        onClick = onClick,
        enabled = enabled && !sending,
        shape = CircleShape,
        modifier = modifier.testTag(FEEDBACK_SEND_TAG),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (sending) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(if (sending) "Sending" else label, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
    }
}

@Composable
internal fun FeedbackPrivacyLink(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onOpen, modifier = modifier.heightIn(min = 40.dp)) {
        Text(
            text = "How we handle feedback",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = TextDecoration.Underline,
        )
    }
}

/** The bar that sits right on the keyboard while typing: photos, diagnostics, Send. */
@Composable
internal fun FeedbackTypingBar(
    state: FeedbackUiState,
    onAddPhoto: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag(FEEDBACK_TYPING_BAR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.meta.photosEnabled) {
            Surface(
                onClick = onAddPhoto,
                enabled = state.canAddPhotos,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Photos and screenshots, ${state.photos.size} attached" },
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    if (state.photos.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Text("${state.photos.size}", style = MaterialTheme.typography.labelLarge, fontWeight = GoogleSansWeight.semiBold)
                    }
                }
            }
        }
        FilterChip(
            selected = state.attachDiagnostics,
            onClick = onToggleDiagnostics,
            label = { Text("Diagnostics", fontWeight = GoogleSansWeight.semiBold) },
            leadingIcon = if (state.attachDiagnostics) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
            shape = CircleShape,
            border = null,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
        Spacer(Modifier.weight(1f))
        FeedbackSendButton(enabled = state.canSend, sending = state.isSubmitting, onClick = onSend, label = "Send", modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
internal fun FeedbackIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description) }
}
