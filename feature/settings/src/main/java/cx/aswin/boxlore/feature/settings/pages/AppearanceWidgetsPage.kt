package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsInfoTip
import cx.aswin.boxlore.feature.settings.components.SettingsPicture
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsTileSection
import cx.aswin.boxlore.feature.settings.openAppPermissionSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Widgets: the colours every home screen widget takes, how Your Shows cuts its covers, then every
 * widget as it looks on the home screen ([preview]), each with a button that asks the home screen
 * app to add it.
 */
@Composable
internal fun AppearanceWidgetsPage(
    state: AppearanceUiState,
    actions: AppearanceActions,
    onBack: () -> Unit,
    preview: AppearanceWidgetPreview? = null,
) {
    TrackScreenView("appearance_widgets")
    SettingsScaffold(title = "Widgets", onBack = onBack) {
        SettingsGroup(
            title = "Colors",
            footer = "App theme matches your chosen colors and mode. System follows your phone’s mode and wallpaper colors.",
        ) {
            SettingsContent {
                ConnectedOptionSelector(
                    options = listOf(WidgetAppearance.APP to "App theme", WidgetAppearance.SYSTEM to "System"),
                    selected = WidgetAppearance.sanitize(state.currentWidgetAppearance),
                    onSelect = actions.onSetWidgetAppearance,
                )
            }
        }
        // With pictures, the cover choice sits in the Your Shows card, right under the picture it changes.
        val coverChoiceInGallery = preview != null && state.addableWidgets.any { it.usesShowsCoverStyle }
        if (!coverChoiceInGallery) {
            SettingsGroup(
                title = "Your Shows covers",
                footer = "Whole covers shows each cover uncropped. Shapes cuts covers into circles, arches and scallops.",
            ) {
                SettingsContent { ShowsCoverChoice(state, actions) }
            }
        }
        if (state.addableWidgets.isNotEmpty()) {
            // The home screen app is asked once, when the page opens: it only changes when the listener switches it.
            val canAdd = remember(actions.canAddWidgets) { actions.canAddWidgets() }
            when {
                preview != null -> WidgetGallery(state, actions, canAdd, preview)
                canAdd -> WidgetRows(state.addableWidgets, actions.onAddWidget)
                else -> SettingsInfoTip(ADD_WIDGETS_BY_HAND)
            }
        }
    }
}

@Composable
private fun ShowsCoverChoice(state: AppearanceUiState, actions: AppearanceActions) {
    ConnectedOptionSelector(
        options = listOf(WidgetShowsArtwork.ROUNDED to "Whole covers", WidgetShowsArtwork.SHAPES to "Shapes"),
        selected = WidgetShowsArtwork.sanitize(state.currentWidgetShowsArtwork),
        onSelect = actions.onSetWidgetShowsArtwork,
    )
}

/**
 * Every widget as a picture with its name. The Add buttons are there only when the home screen app
 * can add; a request the phone turns down without a word is answered in that widget's card.
 */
@Composable
private fun WidgetGallery(
    state: AppearanceUiState,
    actions: AppearanceActions,
    canAdd: Boolean,
    preview: AppearanceWidgetPreview,
) {
    val add = rememberAddWidgetRequest(actions.onAddWidget)
    SettingsTileSection(
        title = if (canAdd) "Add to home screen" else "boxlore widgets",
        footer = if (canAdd) ADD_WIDGETS_CONFIRM else null,
    ) {
        if (!canAdd) SettingsInfoTip(ADD_WIDGETS_BY_HAND)
        for (row in widgetGalleryRows(state.addableWidgets)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { widget ->
                    WidgetCard(
                        widget = widget,
                        sideBySide = row.size > 1,
                        onAdd = add.request.takeIf { canAdd },
                        unanswered = add.unansweredKey == widget.key,
                        preview = preview,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (widget.usesShowsCoverStyle) ShowsCoverChoice(state, actions)
                    }
                }
            }
        }
    }
}

/** Asks for a widget to be added, and knows which request the phone left unanswered. */
private class AddWidgetRequest(val request: (String) -> Unit, val unansweredKey: String?)

/**
 * The home screen app answers a request by opening its confirmation over the app, which pauses it.
 * Some phones turn the request down without showing anything. So when the app has not been paused
 * [ADD_WIDGET_ANSWER_MS] after a request, that request counts as unanswered, until another one is
 * answered.
 */
@Composable
private fun rememberAddWidgetRequest(onAdd: (String) -> Unit): AddWidgetRequest {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var unansweredKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pausedSinceRequest = remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) pausedSinceRequest.value = true }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val request: (String) -> Unit =
        remember(onAdd) {
            { key ->
                pausedSinceRequest.value = false
                onAdd(key)
                scope.launch {
                    delay(ADD_WIDGET_ANSWER_MS)
                    unansweredKey = key.takeUnless { pausedSinceRequest.value }
                }
            }
        }
    return AddWidgetRequest(request, unansweredKey)
}

/**
 * One widget: its picture on a tonal stage that stands in for the wallpaper, any [options] that
 * change that picture, then its name, what it shows and the Add button. Wide widgets put the button
 * beside the text; a small widget sharing its row with another puts it underneath, where it still
 * has room at large text sizes. An [unanswered] request explains itself under the button.
 */
@Composable
private fun WidgetCard(
    widget: AddableWidget,
    sideBySide: Boolean,
    onAdd: ((String) -> Unit)?,
    unanswered: Boolean,
    preview: AppearanceWidgetPreview,
    modifier: Modifier = Modifier,
    options: @Composable () -> Unit = {},
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.outlineVariant).padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                SettingsPicture(Modifier.widthIn(max = widget.previewWidthDp.dp).fillMaxWidth().aspectRatio(widget.previewWidthDp.toFloat() / widget.previewHeightDp)) {
                    preview(widget, Modifier.matchParentSize())
                }
            }
            options()
            if (sideBySide) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCardText(widget)
                    if (onAdd != null) AddWidgetButton(widget, onAdd, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    WidgetCardText(widget, Modifier.weight(1f))
                    if (onAdd != null) AddWidgetButton(widget, onAdd)
                }
            }
            if (unanswered) AddWidgetUnanswered()
        }
    }
}

/** What to do when the phone did not open its confirmation: allow it in the phone's settings, or add by hand. */
@Composable
private fun AddWidgetUnanswered() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SettingsInfoTip(ADD_WIDGET_UNANSWERED)
        TextButton(onClick = { openAppPermissionSettings(context) }, modifier = Modifier.align(Alignment.End)) {
            Text("Open app settings")
        }
    }
}

@Composable
private fun WidgetCardText(widget: AddableWidget, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(widget.name, style = MaterialTheme.typography.titleSmall, fontWeight = GoogleSansWeight.bold, color = MaterialTheme.colorScheme.onSurface)
        Text(widget.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddWidgetButton(widget: AddableWidget, onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = "Add ${widget.name} to home screen"
    FilledTonalButton(
        onClick = { onAdd(widget.key) },
        modifier = modifier.semantics { contentDescription = label },
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text("Add", modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

/** Without pictures: one row per widget that asks the home screen app to add it. */
@Composable
private fun WidgetRows(widgets: List<AddableWidget>, onAdd: (String) -> Unit) {
    SettingsGroup(title = "Add to home screen", footer = ADD_WIDGETS_CONFIRM) {
        widgets.forEachIndexed { index, widget ->
            if (index > 0) SettingsDivider()
            SettingsActionRow(
                title = widget.name,
                supportingText = widget.description,
                icon = Icons.Rounded.Add,
                onClick = { onAdd(widget.key) },
            )
        }
    }
}

/**
 * The gallery's rows: a small widget shares its row with the small widget after it, so the two round
 * players sit side by side; every other widget has a row to itself.
 */
internal fun widgetGalleryRows(widgets: List<AddableWidget>): List<List<AddableWidget>> {
    val rows = mutableListOf<List<AddableWidget>>()
    var index = 0
    while (index < widgets.size) {
        val widget = widgets[index]
        val partner = widgets.getOrNull(index + 1)?.takeIf { widget.isSmall && it.isSmall }
        rows += listOfNotNull(widget, partner)
        index += if (partner != null) 2 else 1
    }
    return rows
}

private val AddableWidget.isSmall: Boolean get() = previewWidthDp <= SMALL_WIDGET_MAX_WIDTH_DP

private const val SMALL_WIDGET_MAX_WIDTH_DP = 200

/** How long the home screen app has to open its confirmation before a request counts as unanswered. */
internal const val ADD_WIDGET_ANSWER_MS = 1_500L

internal const val ADD_WIDGET_UNANSWERED =
    "Your phone didn’t let boxlore add it. Turn on Home screen shortcuts for boxlore in your phone’s app settings, then try again. " +
        "Or touch and hold an empty spot on your home screen and choose Widgets."

internal const val ADD_WIDGETS_CONFIRM = "Your home screen app asks you to confirm."

internal const val ADD_WIDGETS_BY_HAND =
    "To add a boxlore widget, touch and hold an empty spot on your home screen, then choose Widgets."
