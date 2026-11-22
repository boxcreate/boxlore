package cx.aswin.boxlore.core.designsystem.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.ContentLanguageSelection
import cx.aswin.boxlore.core.model.ContentRegions
import cx.aswin.boxlore.core.model.text.UiText
import java.util.Locale

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
    val locale = currentLocale()

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
                    text = stringResource(R.string.content_languages_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.content_languages_picked, effective.size, ContentRegions.MAX_LANGUAGES),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IncludeEnglishRow(
                state = english,
                picks = effective.filter { it != "en" },
                locale = locale,
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
                    title = stringResource(R.string.content_languages_suggested_for, contentRegionName(resolvedCountry, locale)),
                    codes = suggested,
                    selected = effective,
                    remainingSlots = remainingSlots,
                    locale = locale,
                    onToggle = onToggle,
                )
            }
            LanguageChips(
                title = stringResource(if (suggested.isEmpty()) R.string.content_languages_title else R.string.content_languages_other),
                codes = others,
                selected = effective,
                remainingSlots = remainingSlots,
                locale = locale,
                onToggle = onToggle,
            )

            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.content_languages_done))
            }
        }
    }
}

@Composable
private fun IncludeEnglishRow(
    state: ContentLanguageSelection.EnglishSwitch,
    picks: List<String>,
    locale: Locale,
    onEnglishOffChange: (Boolean) -> Unit,
) {
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
                Text(text = stringResource(R.string.content_languages_include_english), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = includeEnglishSupportingText(state, picks) { contentLanguageName(it, locale) }.asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.checked, onCheckedChange = null, enabled = state.enabled)
        }
    }
}

/**
 * What the "Include English" row says for [state], with [picks] the languages in effect besides
 * English and [name] a language's name as it reads inside a sentence.
 */
internal fun includeEnglishSupportingText(state: ContentLanguageSelection.EnglishSwitch, picks: List<String>, name: (String) -> String,): UiText {
    val deep = joinNames(ENGLISH_OFF_LABELS.map(name), R.string.list_end_or)
    return when {
        // English off: the pool is far smaller, so say what that costs before how it is softened.
        !state.checked && picks.size == 1 -> UiText.res(R.string.content_languages_english_off_one, name(picks.single()))
        !state.checked -> UiText.res(R.string.content_languages_english_off_many)
        state.enabled -> UiText.res(R.string.content_languages_english_can_turn_off, joinNames(picks.map(name), R.string.list_end_and))
        state.keptOnBy.isNotEmpty() ->
            UiText.res(R.string.content_languages_english_kept_on, joinNames(state.keptOnBy.map(name), R.string.list_end_and), deep)
        else -> UiText.res(R.string.content_languages_english_only, deep)
    }
}

/** [names] as one list, built from the end: "A, B and C" is list_middle(A, [end](B, C)). */
internal fun joinNames(names: List<String>, @StringRes end: Int): UiText = when (names.size) {
    0 -> UiText.raw("")
    1 -> UiText.raw(names.single())
    2 -> UiText.res(end, names[0], names[1])
    else -> UiText.res(R.string.list_middle, names.first(), joinNames(names.drop(1), end))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(
    title: String,
    codes: List<String>,
    selected: List<String>,
    remainingSlots: Int,
    locale: Locale,
    onToggle: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            codes.forEach { code ->
                val isSelected = code in selected
                BoxloreFilterChip(
                    selected = isSelected,
                    onClick = { onToggle(code) },
                    label = capitalizedIn(contentLanguageName(code, locale), locale),
                    enabled = isSelected || remainingSlots > 0,
                    container = BoxloreFilterChipDefaults.SheetContainer,
                    showCheckWhenSelected = true,
                )
            }
        }
    }
}
