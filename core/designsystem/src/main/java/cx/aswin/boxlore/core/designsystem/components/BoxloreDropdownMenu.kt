package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldSafePopupPositionProvider
import cx.aswin.boxlore.core.designsystem.layout.rememberBoxloreFoldSafePopupPositionProvider

/**
 * Whether a [BoxloreDropdownMenu] is open. It is saved, so an open menu stays open when a fold or
 * unfold recreates the screen. Choosing an item or a choice closes it; a checkable row leaves it open.
 */
@Stable
class BoxloreMenuState internal constructor(expanded: Boolean) {
    var expanded by mutableStateOf(expanded)
        private set

    fun open() {
        expanded = true
    }

    fun dismiss() {
        expanded = false
    }

    internal companion object {
        val Saver: Saver<BoxloreMenuState, Boolean> = Saver(save = { it.expanded }, restore = { BoxloreMenuState(it) })
    }
}

/**
 * A saved [BoxloreMenuState]. Menus on list rows pass the row's stable id as [key] (an episode or
 * playlist id), so a reused row never opens another row's menu.
 */
@Composable
fun rememberBoxloreMenuState(key: Any? = null): BoxloreMenuState = rememberSaveable(key, saver = BoxloreMenuState.Saver) { BoxloreMenuState(false) }

/** One row of a menu, as [BoxloreMenuScope] collects it. */
internal sealed interface BoxloreMenuEntry {
    data class Label(val text: String) : BoxloreMenuEntry

    data class Action(val text: String, val onClick: () -> Unit, val leadingIcon: ImageVector?, val enabled: Boolean, val destructive: Boolean) : BoxloreMenuEntry

    data class Choice(val text: String, val selected: Boolean, val onClick: () -> Unit, val leadingIcon: ImageVector?) : BoxloreMenuEntry

    data class Toggle(val text: String, val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : BoxloreMenuEntry
}

@DslMarker
annotation class BoxloreMenuDsl

/** The rows of one group: an optional label, then items, choices and checkable rows. */
@BoxloreMenuDsl
class BoxloreMenuGroupScope internal constructor() {
    internal val entries = mutableListOf<BoxloreMenuEntry>()

    /** A small heading above the group's rows. */
    fun groupLabel(text: String) {
        entries += BoxloreMenuEntry.Label(text)
    }

    /** An action. A [destructive] one (delete, remove) is red and must sit in a group of its own. */
    fun item(text: String, onClick: () -> Unit, leadingIcon: ImageVector? = null, enabled: Boolean = true, destructive: Boolean = false) {
        entries += BoxloreMenuEntry.Action(text, onClick, leadingIcon, enabled, destructive)
    }

    /** One of several choices; the current one is a filled row with a check. Labels wrap to two lines. */
    fun selectableItem(text: String, selected: Boolean, onClick: () -> Unit, leadingIcon: ImageVector? = null) {
        entries += BoxloreMenuEntry.Choice(text, selected, onClick, leadingIcon)
    }

    /** An on/off row, only for labels that stay the same whichever way it is set. */
    fun checkableItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        entries += BoxloreMenuEntry.Toggle(text, checked, onCheckedChange)
    }
}

/**
 * Builds a [BoxloreDropdownMenu]: [group]s, or rows straight in the menu, which share one group. A
 * destructive [item] added straight to the menu gets a group of its own.
 */
@BoxloreMenuDsl
class BoxloreMenuScope internal constructor() {
    private val groups = mutableListOf<List<BoxloreMenuEntry>>()
    private var loose = BoxloreMenuGroupScope()

    fun group(content: BoxloreMenuGroupScope.() -> Unit) {
        closeLoose()
        val entries = BoxloreMenuGroupScope().apply(content).entries
        val actions = entries.filterIsInstance<BoxloreMenuEntry.Action>()
        require(actions.none { it.destructive } || entries.all { it is BoxloreMenuEntry.Label || (it is BoxloreMenuEntry.Action && it.destructive) }) {
            "Destructive menu items need a group of their own"
        }
        if (entries.isNotEmpty()) groups += entries.toList()
    }

    fun item(text: String, onClick: () -> Unit, leadingIcon: ImageVector? = null, enabled: Boolean = true, destructive: Boolean = false) {
        if (destructive) {
            group { item(text, onClick, leadingIcon, enabled, destructive = true) }
        } else {
            loose.item(text, onClick, leadingIcon, enabled)
        }
    }

    fun selectableItem(text: String, selected: Boolean, onClick: () -> Unit, leadingIcon: ImageVector? = null) = loose.selectableItem(text, selected, onClick, leadingIcon)

    fun checkableItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) = loose.checkableItem(text, checked, onCheckedChange)

    internal fun build(): List<List<BoxloreMenuEntry>> {
        closeLoose()
        return groups.toList()
    }

    private fun closeLoose() {
        if (loose.entries.isNotEmpty()) groups += loose.entries.toList()
        loose = BoxloreMenuGroupScope()
    }
}

/** The groups [content] describes, for tests and the menu. */
internal fun boxloreMenuGroups(content: BoxloreMenuScope.() -> Unit): List<List<BoxloreMenuEntry>> = BoxloreMenuScope().apply(content).build()

/**
 * boxlore's menu: rounded groups on `surfaceContainerHigh`, the current choice as a filled row, and
 * delete or remove actions in red in a group of their own. Put it in a `Box` with its anchor, as
 * with Material's `DropdownMenu`. It opens beside the anchor (its end lined up with the anchor's end
 * for [anchorAlignment] End) inside the fold-safe region that holds the anchor, so it never crosses
 * a hinge; a long menu scrolls inside that region and shows an edge where more rows wait.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxloreDropdownMenu(
    state: BoxloreMenuState,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset((-12).dp, 4.dp),
    anchorAlignment: Alignment.Horizontal = Alignment.End,
    content: BoxloreMenuScope.() -> Unit,
) {
    val foldSafe = rememberBoxloreFoldSafePopupPositionProvider(offset, preferEndAligned = anchorAlignment == Alignment.End)
    val placed = LocalBoxloreMenuPlacement.current
    val provider = remember(foldSafe, placed) { MenuPositionProvider(foldSafe, placed) }
    val scroll = rememberScrollState()
    DropdownMenuPopup(
        expanded = state.expanded,
        onDismissRequest = state::dismiss,
        popupPositionProvider = provider,
        modifier = modifier
            .menuHeightInRegion(foldSafe)
            .menuScrollCue(scroll, MaterialTheme.colorScheme.outlineVariant)
            .verticalScroll(scroll)
            .padding(MenuShadowRoom),
    ) {
        val groups = boxloreMenuGroups(content)
        groups.forEachIndexed { index, entries ->
            if (index > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(
                shapes = menuGroupShapes(index, groups.size),
                modifier = Modifier.fillMaxWidth().widthIn(min = MenuMinWidth, max = MenuMaxWidth),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = MenuShadowElevation,
            ) {
                MenuGroupRows(entries, state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuGroupRows(entries: List<BoxloreMenuEntry>, state: BoxloreMenuState) {
    val rows = entries.count { it !is BoxloreMenuEntry.Label }
    var row = 0
    entries.forEach { entry ->
        if (entry is BoxloreMenuEntry.Label) {
            MenuDefaults.DropdownMenuGroupLabel { Text(entry.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            return@forEach
        }
        val shapes = MenuDefaults.itemShape(row++, rows)
        when (entry) {
            is BoxloreMenuEntry.Action -> {
                val colors = if (entry.destructive) {
                    MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
                } else {
                    MenuDefaults.itemColors()
                }
                DropdownMenuItem(
                    onClick = {
                        state.dismiss()
                        entry.onClick()
                    },
                    text = { MenuText(entry.text) },
                    shape = shapes.shape,
                    leadingIcon = entry.leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
                    enabled = entry.enabled,
                    colors = colors,
                )
            }
            is BoxloreMenuEntry.Choice -> SelectableDropdownMenuItem(
                selected = entry.selected,
                onClick = {
                    state.dismiss()
                    entry.onClick()
                },
                text = { MenuText(entry.text) },
                shapes = shapes,
                leadingIcon = entry.leadingIcon?.let { icon -> { Icon(icon, contentDescription = null) } },
                selectedLeadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                colors = menuChoiceColors(),
            )
            is BoxloreMenuEntry.Toggle -> CheckableDropdownMenuItem(
                checked = entry.checked,
                onCheckedChange = entry.onCheckedChange,
                text = { MenuText(entry.text) },
                shapes = shapes,
                checkedLeadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                colors = menuChoiceColors(),
            )
            is BoxloreMenuEntry.Label -> Unit
        }
    }
}

/**
 * Choice and on/off rows: the current one filled in `secondaryContainer`, the others flush on the
 * group like plain rows, not a lighter row of their own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun menuChoiceColors() = MenuDefaults.selectableItemColors(
    containerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

/** Long labels wrap to a second line instead of being cut; only a third line is shortened. */
@Composable
private fun MenuText(text: String) {
    Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Each group's outer corners use the theme's extra-large shape; corners between groups stay small. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun menuGroupShapes(index: Int, count: Int): MenuGroupShapes {
    val outer = MaterialTheme.shapes.extraLarge
    val inner = CornerSize(MenuInnerCorner)
    val shape: CornerBasedShape = when {
        count == 1 -> outer
        index == 0 -> outer.copy(bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> outer.copy(topStart = inner, topEnd = inner)
        else -> outer.copy(all = inner)
    }
    return MenuGroupShapes(shape = shape, inactiveShape = shape)
}

/**
 * Caps the menu to the space beside its anchor in the fold-safe region it opened in (below or
 * above, whichever is larger), so a long menu scrolls instead of covering its anchor or running
 * past the hinge or the window. Read in layout, so placing it re-measures without recomposing.
 */
private fun Modifier.menuHeightInRegion(provider: BoxloreFoldSafePopupPositionProvider): Modifier = layout { measurable, constraints ->
    val beside = provider.heightBesideAnchorPx
    val capped = if (beside == Int.MAX_VALUE) {
        constraints
    } else {
        val max = beside.coerceAtLeast(MenuMinHeight.roundToPx())
        constraints.copy(maxHeight = minOf(constraints.maxHeight, max), minHeight = minOf(constraints.minHeight, max))
    }
    val placeable = measurable.measure(capped)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/**
 * A 1dp line and a 16dp fade at an edge with more rows beyond it, drawn only while the menu
 * scrolls. The line spans the groups, not the shadow room around them.
 */
private fun Modifier.menuScrollCue(scroll: ScrollState, line: Color): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = MenuScrollFade.toPx()
        val stroke = 1.dp.toPx()
        val start = MenuShadowRoom.toPx()
        val end = size.width - start
        if (scroll.canScrollBackward) {
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade), blendMode = BlendMode.DstIn)
            drawLine(line, Offset(start, stroke / 2), Offset(end, stroke / 2), stroke)
        }
        if (scroll.canScrollForward) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                blendMode = BlendMode.DstIn,
            )
            drawLine(line, Offset(start, size.height - stroke / 2), Offset(end, size.height - stroke / 2), stroke)
        }
    }

/** Material's expressive menu placement, through the fold-safe provider, with its open-from origin. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MenuPositionProvider(
    private val foldSafe: BoxloreFoldSafePopupPositionProvider,
    private val placed: ((IntRect) -> Unit)?,
) : DropdownMenuPopupPositionProvider {
    override var transformOrigin by mutableStateOf(TransformOrigin.Center)
        private set

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val position = foldSafe.calculatePosition(anchorBounds, windowSize, layoutDirection, popupContentSize)
        val bounds = IntRect(position, popupContentSize)
        transformOrigin = menuTransformOrigin(anchorBounds, bounds)
        placed?.invoke(bounds)
        return position
    }
}

/** Tests read where a menu was placed, in window px; null in the app. */
internal val LocalBoxloreMenuPlacement = staticCompositionLocalOf<((IntRect) -> Unit)?> { null }

/** Where the menu grows from: the side or edge it shares with the anchor, as Material's menus do. */
internal fun menuTransformOrigin(anchor: IntRect, menu: IntRect): TransformOrigin {
    fun pivot(anchorStart: Int, anchorEnd: Int, menuStart: Int, menuEnd: Int): Float {
        val length = menuEnd - menuStart
        return when {
            length <= 0 -> 0.5f
            menuStart >= anchorEnd -> 0f
            menuEnd <= anchorStart -> 1f
            else -> ((maxOf(anchorStart, menuStart) + minOf(anchorEnd, menuEnd)) / 2f - menuStart) / length
        }
    }
    return TransformOrigin(pivot(anchor.left, anchor.right, menu.left, menu.right), pivot(anchor.top, anchor.bottom, menu.top, menu.bottom))
}

private val MenuMinWidth = 240.dp
private val MenuMaxWidth = 320.dp
private val MenuInnerCorner = 4.dp
private val MenuShadowElevation = 8.dp
private val MenuShadowRoom = 4.dp
private val MenuScrollFade = 16.dp
private val MenuMinHeight = 48.dp
