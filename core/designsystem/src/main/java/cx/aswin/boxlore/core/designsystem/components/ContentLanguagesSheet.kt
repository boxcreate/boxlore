package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.ContentLanguageSelection
import cx.aswin.boxlore.core.model.ContentRegions

/** Labels of the languages that can stand without English, in the order the sheet names them. */
private val ENGLISH_OFF_LABELS = listOf("de", "fr", "es", "nl", "pt", "ru").filter { it in ContentRegions.ENGLISH_OFF_LANGUAGES }

/**
 * The languages sheet (Library settings › Recommendations › Languages): "Include English" first,
 * then the picks, suggested for [country] first. [selectedLanguages] is the list in effect
 * (without English when it is switched off); [englishOff] is the listener's choice. English can be
 * switched off only when every pick has a deep enough pool ([ContentRegions.canSwitchEnglishOff]).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContentLanguagesSheet(
    country: String,
    selectedLanguages: List<String>,
    englishOff: Boolean,
    onLanguagesChange: (List<String>) -> Unit,
    onEnglishOffChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val resolvedCountry = ContentRegions.canonicalize(country)
    val english = ContentLanguageSelection.englishSwitch(selectedLanguages, englishOff)
    val effective = ContentRegions.normalizeLanguages(selectedLanguages, resolvedCountry, englishOff = !english.checked)
    val remainingSlots = (ContentRegions.MAX_LANGUAGES - effective.size).coerceAtLeast(0)
    val groups = ContentRegions.languageGroupsForCountry(resolvedCountry)
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
                .tabletSheetScroll()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Languages",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = "${effective.size} of ${ContentRegions.MAX_LANGUAGES} picked",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IncludeEnglishRow(
                state = english,
                picks = effective.filter { it != "en" },
                onEnglishOffChange = onEnglishOffChange,
            )

            val suggested = groups.recommended.filter { it != "en" }
            val others = groups.more.filter { it != "en" }
            val onToggle = { code: String ->
                ContentLanguageSelection
                    .applyToggle(effective, code, resolvedCountry, englishOff = !english.checked)
                    ?.let(onLanguagesChange)
                Unit
            }
            if (suggested.isNotEmpty()) {
                LanguageChips(
                    title = "Suggested for ${ContentRegions.displayLabel(resolvedCountry)}",
                    codes = suggested,
                    selected = effective,
                    remainingSlots = remainingSlots,
                    onToggle = onToggle,
                )
            }
            LanguageChips(
                title = if (suggested.isEmpty()) "Languages" else "Other languages",
                codes = others,
                selected = effective,
                remainingSlots = remainingSlots,
                onToggle = onToggle,
            )

            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun IncludeEnglishRow(state: ContentLanguageSelection.EnglishSwitch, picks: List<String>, onEnglishOffChange: (Boolean) -> Unit,) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = state.checked,
                enabled = state.enabled,
                role = Role.Switch,
                onValueChange = { includeEnglish -> onEnglishOffChange(!includeEnglish) },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Include English", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = includeEnglishSupportingText(state, picks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.checked, onCheckedChange = null, enabled = state.enabled)
        }
    }
}

/** What the "Include English" row says for [state], with [picks] the languages in effect besides English. */
internal fun includeEnglishSupportingText(state: ContentLanguageSelection.EnglishSwitch, picks: List<String>): String {
    val deep = joinLabels(ENGLISH_OFF_LABELS, conjunction = "or")
    return when {
        // English off: the pool is far smaller, so say what that costs before how it is softened.
        !state.checked && picks.size == 1 ->
            "There are far fewer ${label(picks.single())} shows, so recommendations won’t match your taste as closely. " +
                "If there aren’t enough, boxlore adds a few in English."
        !state.checked ->
            "There are far fewer shows in your languages, so recommendations won’t match your taste as closely. " +
                "If there aren’t enough, boxlore adds a few in English."
        state.enabled -> "Turn it off to get only shows in ${joinLabels(picks, conjunction = "and")}."
        state.keptOnBy.isNotEmpty() -> "Stays on with ${joinLabels(state.keptOnBy, conjunction = "and")}. You can turn it off when you pick only $deep."
        else -> "You can turn it off when you pick only $deep."
    }
}

private fun label(code: String): String = ContentRegions.LANGUAGE_LABELS[code] ?: code

private fun joinLabels(codes: List<String>, conjunction: String): String {
    val labels = codes.map(::label)
    return when (labels.size) {
        0 -> ""
        1 -> labels.single()
        else -> labels.dropLast(1).joinToString(", ") + " $conjunction " + labels.last()
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LanguageChips(title: String, codes: List<String>, selected: List<String>, remainingSlots: Int, onToggle: (String) -> Unit,) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            codes.forEach { code ->
                val isSelected = code in selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(code) },
                    enabled = isSelected || remainingSlots > 0,
                    label = { Text(label(code)) },
                    leadingIcon =
                    if (isSelected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
