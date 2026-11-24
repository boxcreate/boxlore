package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.BoxloreFilterChip
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R

internal const val FEEDBACK_MESSAGE_TAG = "feedback_message"
internal const val FEEDBACK_SEND_TAG = "feedback_send"
internal const val FEEDBACK_PILLS_TAG = "feedback_pills"
internal const val FEEDBACK_TYPING_BAR_TAG = "feedback_typing_bar"
internal const val FEEDBACK_PHOTOS_TAG = "feedback_photos"
internal const val FEEDBACK_TYPE_PILL_TAG = "feedback_type_pill"

/** The typing header's type pill never grows past this; longer names shorten with "…". */
internal val FEEDBACK_TYPE_PILL_MAX_WIDTH = 168.dp
internal const val FEEDBACK_PRIVACY_URL = "https://boxlore.in/privacy/#feedback"

/** The app's icon for the choices it knows; a speech bubble for any the server adds later. */
internal val FeedbackChoice.icon: ImageVector
    get() = when (known) {
        FeedbackCategory.FEATURE -> Icons.Rounded.Lightbulb
        FeedbackCategory.BUG -> Icons.Rounded.BugReport
        FeedbackCategory.AUDIO -> Icons.Rounded.Headphones
        FeedbackCategory.OTHER, null -> Icons.Rounded.ChatBubbleOutline
    }

/** boxlore's filter chip, as on Home and Explore: round, 48 dp, icon first, primary container when chosen. */
@Composable
internal fun FeedbackTypePill(choice: FeedbackChoice, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BoxloreFilterChip(
        selected = selected,
        onClick = onClick,
        label = choice.label.asString(),
        leadingIcon = choice.icon,
        enabled = enabled,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackTypePills(
    choices: List<FeedbackChoice>,
    selected: FeedbackChoice,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.testTag(FEEDBACK_PILLS_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { choice ->
            FeedbackTypePill(choice = choice, selected = choice.id == selected.id, enabled = enabled, onClick = { onSelect(choice.id) })
        }
    }
}

/** Compact type picker for the typing layout: the chosen pill, opening a menu of every choice. */
@Composable
internal fun FeedbackTypeMenuPill(choices: List<FeedbackChoice>, selected: FeedbackChoice, enabled: Boolean, onSelect: (String) -> Unit) {
    val menu = rememberBoxloreMenuState()
    Box {
        val a11yLabel = stringResource(R.string.feedback_type_change_description, selected.label.asString())
        // A 40dp pill in a 48dp touch area; a long type name shortens instead of widening the header.
        Surface(
            onClick = menu::open,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .testTag(FEEDBACK_TYPE_PILL_TAG)
                .minimumInteractiveComponentSize()
                .widthIn(max = FEEDBACK_TYPE_PILL_MAX_WIDTH)
                .heightIn(min = 40.dp)
                .semantics { contentDescription = a11yLabel },
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(selected.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    selected.label.asString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        val labels = choices.map { it.label.asString() }
        BoxloreDropdownMenu(menu) {
            choices.forEachIndexed { index, choice ->
                selectableItem(labels[index], selected = choice.id == selected.id, onClick = { onSelect(choice.id) }, leadingIcon = choice.icon)
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
    readOnly: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val over = value.length > maxChars
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
                val a11yLabel2 = stringResource(R.string.feedback_message_label)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = readOnly,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(FEEDBACK_MESSAGE_TAG)
                        .semantics { contentDescription = a11yLabel2 }
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                )
            }
            Text(
                text = "${value.length}/$maxChars",
                style = MaterialTheme.typography.labelSmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (over) GoogleSansWeight.semiBold else null,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
internal fun FeedbackPhotoRow(
    photos: List<PickedPhoto>,
    canAdd: Boolean,
    canRemove: Boolean,
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
        // Scrolls sideways, so every photo and its Remove stay reachable on narrow phones.
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedbackPhotoTiles(photos, canAdd, canRemove, onAdd, onRemove)
        }
        Text(pluralStringResource(R.plurals.feedback_photos_limit, maxPhotos, maxPhotos), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedbackPhotoTiles(photos: List<PickedPhoto>, canAdd: Boolean, canRemove: Boolean, onAdd: () -> Unit, onRemove: (PickedPhoto) -> Unit) {
    val a11yLabel3 = stringResource(R.string.feedback_add_photo)
    Surface(
        onClick = onAdd,
        enabled = canAdd,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .size(64.dp)
            .semantics { contentDescription = a11yLabel3 },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.feedback_add), style = MaterialTheme.typography.labelSmall, fontWeight = GoogleSansWeight.semiBold)
        }
    }
    photos.forEachIndexed { index, photo ->
        Box(Modifier.size(64.dp)) {
            AsyncImage(
                model = photo.uri,
                contentDescription = stringResource(R.string.feedback_photo_number, index + 1),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            val a11yLabel4 = stringResource(R.string.feedback_remove_photo, index + 1)
            Surface(
                onClick = { onRemove(photo) },
                enabled = canRemove,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(24.dp)
                    .semantics { contentDescription = a11yLabel4 },
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.padding(5.dp))
            }
        }
    }
}

@Composable
internal fun FeedbackDiagnosticsRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // The whole row is the switch: a tap anywhere toggles it, and screen readers hear one switch with its state.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feedback_include_diagnostics),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun FeedbackSendButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.feedback_send_feedback)) {
    Button(
        onClick = onClick,
        enabled = enabled && !sending,
        shape = CircleShape,
        modifier = modifier.testTag(FEEDBACK_SEND_TAG),
        colors = ButtonDefaults.buttonColors(),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (sending) {
            BoxLoreLoader.CircularWavy(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(if (sending) stringResource(R.string.feedback_sending) else label, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
    }
}

@Composable
internal fun FeedbackPrivacyLink(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onOpen, modifier = modifier.heightIn(min = 40.dp)) {
        Text(
            text = stringResource(R.string.feedback_how_we_handle),
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
            val a11yLabel6 = pluralStringResource(R.plurals.feedback_photos_attached_description, state.photos.size, state.photos.size)
            Surface(
                onClick = onAddPhoto,
                enabled = state.canAddPhotos,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = a11yLabel6 },
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
        BoxloreFilterChip(
            selected = state.attachDiagnostics,
            onClick = onToggleDiagnostics,
            label = stringResource(R.string.feedback_diagnostics),
            showCheckWhenSelected = true,
            enabled = !state.isSubmitting,
        )
        Spacer(Modifier.weight(1f))
        FeedbackSendButton(enabled = state.canSend, sending = state.isSubmitting, onClick = onSend, label = stringResource(R.string.feedback_send), modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
internal fun FeedbackIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description) }
}
