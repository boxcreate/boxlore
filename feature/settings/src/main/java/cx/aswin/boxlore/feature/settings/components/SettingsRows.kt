package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberSectionHeaderFontFamily
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.roundToInt

private val CategoryCardHeight = 104.dp
private val SettingsRowHeight = 72.dp
private val SettingsRowHeightCompact = 56.dp
private val SettingsLeadingIconSize = 40.dp
private val SettingsRowItemGap = 12.dp

@Composable
internal fun SettingsCategoryCard(
    title: String,
    description: String,
    icon: ImageVector,
    /** The card's container colour, then the colour of its text and icon. */
    colors: Pair<Color, Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    descriptionMaxLines: Int = 3,
) {
    val (containerColor, contentColor) = colors
    val shape = MaterialTheme.shapes.extraLarge
    // Long names and large text take more lines, and the card grows to hold them.
    Card(
        modifier =
        modifier
            .fillMaxWidth()
            .heightIn(min = CategoryCardHeight)
            .expressiveClickable(shape = shape, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = CategoryCardHeight)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconContainer(
                icon = icon,
                containerColor = contentColor.copy(alpha = 0.14f),
                contentColor = contentColor,
                size = 48.dp,
                shape = MaterialTheme.shapes.large,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = rememberSectionHeaderFontFamily(),
                    fontWeight = FontWeight.Normal,
                    color = contentColor,
                    // Two lines at any size: on two-column rows a longer language's name needs them.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                    // Three lines at any size by default: two-column cards and longer languages need the room.
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Filled, expressive settings section. The card provides the surface fill so
 * rows layered on top can stay transparent.
 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
        if (footer != null) {
            Text(
                text = footer,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SettingsInfoTip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                modifier =
                Modifier
                    .padding(top = 1.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Free-form content slot inside a filled group (chips, grids, custom UI). */
@Composable
internal fun SettingsContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

/** Discrete duration control used by playback settings. Values are committed after dragging. */
internal data class SettingsDurationSliderValue(
    val seconds: Int,
    val range: IntRange,
    val stepSeconds: Int,
)

internal data class SettingsDurationSliderIcon(
    val image: ImageVector,
    val mirrored: Boolean = false,
)

@Composable
internal fun SettingsDurationSliderRow(
    title: String,
    value: SettingsDurationSliderValue,
    onValueCommitted: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: SettingsDurationSliderIcon? = null,
    zeroLabel: String = stringResource(R.string.settings_duration_off),
) {
    var pendingValue by remember(value.seconds) {
        mutableFloatStateOf(value.seconds.toFloat())
    }
    val snappedSeconds =
        ((pendingValue / value.stepSeconds).roundToInt() * value.stepSeconds)
            .coerceIn(value.range.first, value.range.last)
    val valueLabel = if (snappedSeconds == 0) zeroLabel else pluralStringResource(R.plurals.settings_duration_seconds, snappedSeconds, snappedSeconds)

    Column(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SettingsRowItemGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SettingsIconContainer(
                    icon = icon.image,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = SettingsLeadingIconSize,
                    shape = MaterialTheme.shapes.medium,
                    mirrorIcon = icon.mirrored,
                )
            }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = GoogleSansWeight.medium,
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = pendingValue,
            onValueChange = { pendingValue = it },
            onValueChangeFinished = { onValueCommitted(snappedSeconds) },
            valueRange = value.range.first.toFloat()..value.range.last.toFloat(),
            steps = 0,
            modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$title, $valueLabel"
                },
        )
    }
}

/** Icon tint pair reused by settings row composables, kept as one parameter to limit arity. */
internal data class SettingsRowIconColors(
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
internal fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    trailingText: String? = null,
    icon: ImageVector? = null,
    iconColors: SettingsRowIconColors =
        SettingsRowIconColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
) {
    SettingsRowScaffold(
        text = SettingsRowTextStyle(title = title, supportingText = supportingText),
        onClick = onClick,
        modifier = modifier,
        icon = icon?.let { SettingsRowIcon(it, iconColors.containerColor, iconColors.contentColor) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
) {
    val defaultIconColors =
        SettingsRowIconColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    SettingsRowScaffold(
        text = SettingsRowTextStyle(title = title, supportingText = supportingText),
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        icon = icon?.let { SettingsRowIcon(it, defaultIconColors.containerColor, defaultIconColors.contentColor) },
        rowSemantics = Modifier.switchRowSemantics(checked),
        trailing = {
            // The row is the one switch screen readers and taps reach; this only shows the state.
            Switch(
                checked = checked,
                onCheckedChange = null,
                // Without its own click the switch loses its 48dp box; keep it so rows keep their height.
                modifier = Modifier.minimumInteractiveComponentSize(),
            )
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    badge: String? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .expressiveClickable(shape = MaterialTheme.shapes.medium, onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsRowItemGap),
    ) {
        if (leading != null) {
            leading()
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Long names wrap rather than cut, in one weight whether picked or not (the radio shows the pick).
            FlowRow(
                itemVerticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (badge != null) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        RadioButton(
            selected = selected,
            onClick = null,
            // Keeps the radio's 48dp box (and the row's height) without a second click target.
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
    }
}

@Composable
internal fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val accent =
        if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    SettingsRowScaffold(
        text =
        SettingsRowTextStyle(
            title = title,
            titleColor = accent,
            supportingText = supportingText,
            supportingColor =
            if (destructive) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        onClick = onClick,
        modifier = modifier,
        icon =
        icon?.let {
            SettingsRowIcon(
                icon = it,
                containerColor =
                if (destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor =
                if (destructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
        },
        trailing = trailing,
    )
}

/** Icon + tint colors for a settings row, kept as one parameter to limit [SettingsRowScaffold] arity. */
private data class SettingsRowIcon(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

/** Title/supporting text + colors for a settings row, kept as one parameter to limit [SettingsRowScaffold] arity. */
private data class SettingsRowTextStyle(
    val title: String,
    val titleColor: Color = Color.Unspecified,
    val supportingText: String? = null,
    val supportingColor: Color = Color.Unspecified,
)

@Composable
private fun SettingsRowScaffold(
    text: SettingsRowTextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: SettingsRowIcon? = null,
    rowSemantics: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val titleColor = text.titleColor.takeOrElse { MaterialTheme.colorScheme.onSurface }
    val supportingColor = text.supportingColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = if (text.supportingText != null) SettingsRowHeight else SettingsRowHeightCompact,
            ).expressiveClickable(shape = MaterialTheme.shapes.medium, onClick = onClick)
            .then(rowSemantics)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SettingsRowItemGap),
    ) {
        if (icon != null) {
            SettingsIconContainer(
                icon = icon.icon,
                containerColor = icon.containerColor,
                contentColor = icon.contentColor,
                size = SettingsLeadingIconSize,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = text.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (text.supportingText != null) {
                Text(
                    text = text.supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/** A switch row reads as one switch with its state; the row's own click toggles it. */
private fun Modifier.switchRowSemantics(checked: Boolean): Modifier = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(checked)
}

@Composable
internal fun SettingsIconContainer(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = SettingsLeadingIconSize,
    shape: Shape = MaterialTheme.shapes.medium,
    mirrorIcon: Boolean = false,
) {
    Surface(
        modifier = modifier.size(size),
        color = containerColor,
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier =
                Modifier
                    .size(size * 0.5f)
                    .graphicsLayer(scaleX = if (mirrorIcon) -1f else 1f),
            )
        }
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
