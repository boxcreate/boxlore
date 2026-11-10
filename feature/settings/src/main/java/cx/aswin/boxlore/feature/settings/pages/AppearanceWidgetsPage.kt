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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
        SettingsGroup(
            title = "Your Shows covers",
            footer = "Whole covers shows each cover uncropped. Shapes cuts covers into circles, arches and scallops.",
        ) {
            SettingsContent {
                ConnectedOptionSelector(
                    options = listOf(WidgetShowsArtwork.ROUNDED to "Whole covers", WidgetShowsArtwork.SHAPES to "Shapes"),
                    selected = WidgetShowsArtwork.sanitize(state.currentWidgetShowsArtwork),
                    onSelect = actions.onSetWidgetShowsArtwork,
                )
            }
        }
        if (state.addableWidgets.isNotEmpty()) {
            // The home screen app is asked once, when the page opens: it only changes when the listener switches it.
            val canAdd = remember(actions.canAddWidgets) { actions.canAddWidgets() }
            when {
                preview != null -> WidgetGallery(state.addableWidgets, canAdd, actions.onAddWidget, preview)
                canAdd -> WidgetRows(state.addableWidgets, actions.onAddWidget)
                else -> SettingsInfoTip(ADD_WIDGETS_BY_HAND)
            }
        }
    }
}

/** Every widget as a picture with its name. The Add buttons are there only when the home screen app can add. */
@Composable
private fun WidgetGallery(
    widgets: List<AddableWidget>,
    canAdd: Boolean,
    onAdd: (String) -> Unit,
    preview: AppearanceWidgetPreview,
) {
    SettingsTileSection(
        title = if (canAdd) "Add to home screen" else "boxlore widgets",
        footer = if (canAdd) ADD_WIDGETS_CONFIRM else null,
    ) {
        if (!canAdd) SettingsInfoTip(ADD_WIDGETS_BY_HAND)
        for (row in widgetGalleryRows(widgets)) {
            if (row.size == 1) {
                WidgetCard(row.single(), sideBySide = false, onAdd = onAdd.takeIf { canAdd }, preview = preview)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { widget ->
                        WidgetCard(widget, sideBySide = true, onAdd = onAdd.takeIf { canAdd }, preview = preview, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * One widget: its picture on a tonal stage that stands in for the wallpaper, then its name, what it
 * shows and the Add button. Wide widgets put the button beside the text; a small widget sharing its
 * row with another puts it underneath, where it still has room at large text sizes.
 */
@Composable
private fun WidgetCard(
    widget: AddableWidget,
    sideBySide: Boolean,
    onAdd: ((String) -> Unit)?,
    preview: AppearanceWidgetPreview,
    modifier: Modifier = Modifier,
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
        val next = widgets.getOrNull(index + 1)
        val pair = widget.previewWidthDp <= SMALL_WIDGET_MAX_WIDTH_DP && next != null && next.previewWidthDp <= SMALL_WIDGET_MAX_WIDTH_DP
        rows += if (pair) listOf(widget, next!!) else listOf(widget)
        index += if (pair) 2 else 1
    }
    return rows
}

private const val SMALL_WIDGET_MAX_WIDTH_DP = 200

internal const val ADD_WIDGETS_CONFIRM = "Your home screen app asks you to confirm."

internal const val ADD_WIDGETS_BY_HAND =
    "To add a boxlore widget, touch and hold an empty spot on your home screen, then choose Widgets."
