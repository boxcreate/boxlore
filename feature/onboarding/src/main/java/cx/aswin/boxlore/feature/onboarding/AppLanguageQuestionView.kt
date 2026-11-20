package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.prefs.AppLanguage

/**
 * The language question: the suggested language and English as buttons, then "Other languages",
 * which opens the rest of the list. A pick applies at once and onboarding moves on to Welcome. The column
 * scrolls, so short and split windows reach every choice.
 */
@Composable
internal fun AppLanguageQuestionContent(
    question: AppLanguageQuestion,
    onChoose: (AppLanguage) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .boxloreCardWidth(BoxloreCardFamily.Utility)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
                }
            }
            Text(
                stringResource(R.string.onboarding_app_language_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
            )
            Text(
                stringResource(R.string.onboarding_app_language_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            question.quickChoices.forEachIndexed { index, language ->
                QuickLanguageButton(language, primary = index == 0, onClick = { onChoose(language) })
            }
            if (question.showsAll) {
                OtherLanguagesList(question.otherChoices, onChoose)
            } else {
                TextButton(onClick = onShowAll, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.onboarding_app_language_other))
                }
            }
        }
    }
}

@Composable
private fun QuickLanguageButton(language: AppLanguage, primary: Boolean, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    val padding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
    val label = @Composable {
        Text(languageLabel(language), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
    if (primary) {
        Button(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), contentPadding = padding) { label() }
    } else {
        FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(28.dp), contentPadding = padding) { label() }
    }
}

@Composable
private fun OtherLanguagesList(languages: List<AppLanguage>, onChoose: (AppLanguage) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
            languages.forEachIndexed { index, language ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { onChoose(language) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    Text(
                        languageLabel(language),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The language in its own words; the phone's language reads "System default" in the screen's language. */
@Composable
private fun languageLabel(language: AppLanguage): String = language.nativeName ?: stringResource(R.string.onboarding_app_language_system)
