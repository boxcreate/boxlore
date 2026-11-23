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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ContentRegion
import cx.aswin.boxlore.core.model.ContentRegions
import java.util.Locale

/**
 * Content region picker (11 chart storefronts). Opens a Material 3 bottom sheet list.
 * Shared by settings and onboarding. [compact] shows a small chip with the country instead of the
 * labelled card, for a line under a section title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionSegmentedSelector(
    activeRegion: String,
    onSwitchRegion: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String = stringResource(R.string.content_region_supporting),
    compact: Boolean = false,
) {
    val selected = ContentRegions.canonicalize(activeRegion)
    val label = contentRegionName(selected, currentLocale())
    var showSheet by remember { mutableStateOf(false) }

    if (compact) {
        RegionPickerChip(label = label, onClick = { showSheet = true }, modifier = modifier)
    } else {
        RegionPickerTrigger(
            label = label,
            supportingText = supportingText,
            onClick = { showSheet = true },
            modifier = modifier,
        )
    }

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

/** Display label for a stored region code (e.g. charts header chip) in [locale]; see [contentRegionName]. */
fun regionDisplayLabel(code: String, locale: Locale = Locale.ENGLISH): String = contentRegionName(code, locale)

/** A 32dp tonal pill (globe, country, arrow) inside a 48dp touch target. */
@Composable
private fun RegionPickerChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .expressiveClickable(shape = CircleShape, onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            Modifier.heightIn(min = 32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Read with the country, as "Content region, <country>".
                Icon(Icons.Rounded.Public, stringResource(R.string.content_region_label), Modifier.size(18.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
            }
        }
    }
}

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
                    text = stringResource(R.string.content_region_label),
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
                contentDescription = stringResource(R.string.content_region_choose),
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
                text = stringResource(R.string.content_region_label),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.content_region_sheet_body),
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
    val locale = currentLocale()
    val recommended =
        region.recommendedLanguages
            .filter { it in ContentRegions.LANGUAGE_LABELS }
            .joinToString(" · ") { capitalizedIn(contentLanguageName(it, locale), locale) }
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
                label = contentRegionName(region.code, locale),
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
                text = stringResource(R.string.content_region_suggested, recommended),
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
