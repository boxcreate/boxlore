package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ContentLanguageSelection
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
 * Country + languages as one discovery preferences block (settings).
 * Languages sit under the country control so the recommended set reads as related.
 */
@Composable
fun ContentRegionLanguagePicker(
    activeRegion: String,
    selectedLanguages: List<String>,
    onSwitchRegion: (String) -> Unit,
    onLanguagesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val country = ContentRegions.canonicalize(activeRegion)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RegionSegmentedSelector(
            activeRegion = country,
            onSwitchRegion = onSwitchRegion,
            supportingText = "Charts and regional ranking",
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ContentLanguageChipRow(
            selectedLanguages = selectedLanguages,
            country = country,
            onLanguagesChange = onLanguagesChange,
        )
    }
}

/** Canonicalizes any known alias, or `null` if unknown. */
fun canonicalRegionCode(code: String): String? = ContentRegions.canonicalizeOrNull(code)

/** Display label for a stored region code (e.g. charts header chip). */
fun regionDisplayLabel(code: String): String = ContentRegions.displayLabel(code)

/**
 * Multi-select language chips. English is always selected and cannot be turned off.
 * Recommended-for-country languages are grouped above the full allowlist.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentLanguageChipRow(
    selectedLanguages: List<String>,
    country: String,
    onLanguagesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    showOffMarketHint: Boolean = true,
) {
    val resolvedCountry = ContentRegions.canonicalize(country)
    val normalized = ContentRegions.normalizeLanguages(selectedLanguages, resolvedCountry)
    val groups = ContentRegions.languageGroupsForCountry(resolvedCountry)
    val offMarket =
        normalized.filter { ContentRegions.isOffMarketLanguage(it, resolvedCountry) && it != "en" }
    val countryLabel = ContentRegions.displayLabel(resolvedCountry)
    val remainingSlots = (ContentRegions.MAX_LANGUAGES - normalized.size).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreferenceGlyph(icon = Icons.Rounded.Translate)
                Text(
                    text = "Languages",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text =
                    "Languages selected will influence your recommendations. " +
                        "Charts are bound to the selected country only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LanguageChipSection(
            title = "Suggested for $countryLabel",
            codes = groups.recommended,
            selected = normalized,
            country = resolvedCountry,
            remainingSlots = remainingSlots,
            onLanguagesChange = onLanguagesChange,
        )

        if (groups.more.isNotEmpty()) {
            LanguageChipSection(
                title = "More languages",
                codes = groups.more,
                selected = normalized,
                country = resolvedCountry,
                remainingSlots = remainingSlots,
                onLanguagesChange = onLanguagesChange,
            )
        }

        if (showOffMarketHint && offMarket.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Text(
                    text =
                        "Some picks aren’t typical for $countryLabel — that’s fine if you want them.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun RegionPickerTrigger(
    label: String,
    supportingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
private fun RegionPickerBottomSheet(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
                    .fillMaxWidth()
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
                text = "Used for charts and regional ranking. Changing region resets languages to a recommended set.",
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
private fun RegionPickerOptionRow(
    region: ContentRegion,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
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
private fun RegionPickerOptionCopy(
    label: String,
    recommended: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChipSection(
    title: String,
    codes: List<String>,
    selected: List<String>,
    country: String,
    remainingSlots: Int,
    onLanguagesChange: (List<String>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            codes.forEach { code ->
                val isSelected = code in selected
                val isEnglish = code == "en"
                val canSelectMore = remainingSlots > 0 || isSelected
                LanguagePreferenceChip(
                    label = ContentRegions.LANGUAGE_LABELS[code] ?: code,
                    selected = isSelected,
                    locked = isEnglish,
                    enabled = isEnglish || canSelectMore,
                    onClick = {
                        ContentLanguageSelection.applyToggle(
                            selectedLanguages = selected,
                            languageCode = code,
                            country = country,
                        )?.let(onLanguagesChange)
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguagePreferenceChip(
    label: String,
    selected: Boolean,
    locked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHighest
        }
    val contentColor =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = containerColor,
        contentColor = contentColor,
        border =
            if (selected) {
                null
            } else {
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                )
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) GoogleSansWeight.semiBold else GoogleSansWeight.medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
