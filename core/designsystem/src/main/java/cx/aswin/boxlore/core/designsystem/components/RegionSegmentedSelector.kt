package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ContentRegion
import cx.aswin.boxlore.core.model.ContentRegions

/**
 * Content region picker (11 chart storefronts). Opens a Material 3 bottom sheet list.
 * Shared by settings and onboarding.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSegmentedSelector(
    activeRegion: String,
    onSwitchRegion: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String = "Charts and regional ranking",
) {
    val selected = ContentRegions.canonicalize(activeRegion)
    val label = ContentRegions.displayLabel(selected)
    var showSheet by remember { mutableStateOf(false) }

    RegionPickerTrigger(
        label = label,
        supportingText = supportingText,
        onClick = { showSheet = true },
        modifier = modifier,
    )

    if (showSheet) {
        RegionPickerBottomSheet(
            selectedCode = selected,
            onSelect = { code ->
                showSheet = false
                if (code != selected) onSwitchRegion(code)
            },
            onDismiss = { showSheet = false },
        )
    }
}

/**
 * The country list on its own, opened from a settings row (Library settings › Charts › Country).
 * [onSwitchRegion] runs only for a different country.
 */
@Composable
fun ContentRegionPickerSheet(activeRegion: String, onSwitchRegion: (String) -> Unit, onDismiss: () -> Unit,) {
    val selected = ContentRegions.canonicalize(activeRegion)
    RegionPickerBottomSheet(
        selectedCode = selected,
        onSelect = { code ->
            onDismiss()
            if (code != selected) onSwitchRegion(code)
        },
        onDismiss = onDismiss,
    )
}

/** Canonicalizes any known alias, or `null` if unknown. */
fun canonicalRegionCode(code: String): String? = ContentRegions.canonicalizeOrNull(code)

/** Display label for a stored region code (e.g. charts header chip). */
fun regionDisplayLabel(code: String): String = ContentRegions.displayLabel(code)

@Composable
private fun RegionPickerTrigger(label: String, supportingText: String, onClick: () -> Unit, modifier: Modifier = Modifier,) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .expressiveClickable(
                shape = MaterialTheme.shapes.extraLarge,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreferenceGlyph(icon = Icons.Rounded.Public)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Content region",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = "Choose content region",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegionPickerBottomSheet(selectedCode: String, onSelect: (String) -> Unit, onDismiss: () -> Unit,) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth().tabletSheetScroll()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = "Content region",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Sets the top charts in Explore. Your languages stay as they are.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ContentRegions.all.forEach { region ->
                    RegionPickerOptionRow(
                        region = region,
                        selected = region.code == selectedCode,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegionPickerOptionRow(region: ContentRegion, selected: Boolean, onSelect: (String) -> Unit,) {
    val recommended =
        region.recommendedLanguages
            .mapNotNull { ContentRegions.LANGUAGE_LABELS[it] }
            .joinToString(" · ")
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh
    val contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface
    Surface(
        modifier =
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = { onSelect(region.code) },
                role = Role.RadioButton,
            ),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RegionPickerOptionCopy(
                label = region.label,
                recommended = recommended,
                selected = selected,
                modifier = Modifier.weight(1f),
            )
            RadioButton(
                selected = selected,
                onClick = null,
            )
        }
    }
}

@Composable
private fun RegionPickerOptionCopy(label: String, recommended: String, selected: Boolean, modifier: Modifier = Modifier,) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) GoogleSansWeight.semiBold else GoogleSansWeight.medium,
        )
        if (recommended.isNotEmpty()) {
            val suggestedColor =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            Text(
                text = "Suggested: $recommended",
                style = MaterialTheme.typography.bodySmall,
                color = suggestedColor,
            )
        }
    }
}

@Composable
private fun PreferenceGlyph(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
