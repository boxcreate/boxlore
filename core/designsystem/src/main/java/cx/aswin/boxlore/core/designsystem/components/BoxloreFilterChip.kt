package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressivePressFeedback
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/** Chip containers for the surface a chip row sits on. */
object BoxloreFilterChipDefaults {
    /** For chips on pages, whose background is `surface`. */
    val PageContainer: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer

    /** For chips on `surfaceContainer` sheets, so unselected chips stay visible on the sheet. */
    val SheetContainer: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
}

/**
 * boxlore's one filter chip (genres, languages, filters): a 48dp filled pill with no outline. The
 * selected chip is `primaryContainer`; an unselected one is [container], so pass
 * [BoxloreFilterChipDefaults.SheetContainer] on sheets. [showCheckWhenSelected] puts a check in
 * place of [leadingIcon] while selected. The label stays on one line and shortens with an ellipsis.
 */
@Composable
@Suppress("LongParameterList") // A chip's content, state and look, as Material's FilterChip takes them.
fun BoxloreFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    container: Color = BoxloreFilterChipDefaults.PageContainer,
    showCheckWhenSelected: Boolean = false,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    val icon = if (selected && showCheckWhenSelected) Icons.Rounded.Check else leadingIcon
    ChipBody(
        label = label,
        onClick = onClick,
        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else container,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        leadingIcon = icon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        modifier = modifier.semantics {
            role = Role.Checkbox
            this.selected = selected
        },
    )
}

/** The chip at the end of a chip row that opens the rest ("More"), with a down arrow. */
@Composable
fun BoxloreMoreChip(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, container: Color = BoxloreFilterChipDefaults.PageContainer) {
    ChipBody(
        label = label,
        onClick = onClick,
        containerColor = container,
        contentColor = MaterialTheme.colorScheme.primary,
        leadingIcon = null,
        trailingIcon = Icons.Rounded.ExpandMore,
        enabled = true,
        modifier = modifier.semantics { role = Role.Button },
    )
}

@Composable
@Suppress("LongParameterList")
private fun ChipBody(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    enabled: Boolean,
    modifier: Modifier,
) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .heightIn(min = BoxloreFilterChipMinHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .expressiveClickable(enabled = enabled, shape = CircleShape, feedback = ExpressivePressFeedback.Control, onClick = onClick)
                .background(containerColor, CircleShape)
                .padding(start = if (leadingIcon != null) 12.dp else 16.dp, end = if (trailingIcon != null) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ChipIconSize)) }
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = GoogleSansWeight.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(ChipIconSize)) }
        }
    }
}

/** Every boxlore filter chip is at least this tall: a full touch target. */
val BoxloreFilterChipMinHeight = 48.dp
private val ChipIconSize = 18.dp
private const val DISABLED_ALPHA = 0.38f
