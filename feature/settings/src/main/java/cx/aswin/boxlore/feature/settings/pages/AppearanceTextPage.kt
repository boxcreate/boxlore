package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.designsystem.theme.buildGoogleSansFamily
import cx.aswin.boxlore.core.designsystem.theme.buildSectionHeaderFontFamily
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsPictureGrid
import cx.aswin.boxlore.feature.settings.components.SettingsPictureTile
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsTileSection

/** Text: the letter shape as tiles drawn in each shape, and a preview that is always shown. */
@Composable
internal fun AppearanceTextPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    TrackScreenView("appearance_text")
    val selectedKey = FontRoundness.sanitizeKey(state.currentFontRoundness)
    val context = LocalContext.current
    val axis = remember(selectedKey) { FontRoundness.axisValue(selectedKey) }
    val titleFamily = remember(context, axis) { buildGoogleSansFamily(context, axis, weight = 600) }
    val bodyFamily = remember(context, axis) { buildGoogleSansFamily(context, axis, weight = 400) }
    val captionFamily = remember(context, axis) { buildGoogleSansFamily(context, axis, weight = 500) }
    val headerFamily = remember(context, axis) { buildSectionHeaderFontFamily(context, axis) }
    SettingsScaffold(title = "Text", onBack = onBack) {
        SettingsTileSection(title = "Letter shape", footer = "How rounded letters look across the app. Most visible in large titles; smaller text changes less.") {
            SettingsPictureGrid(FontRoundness.entries, columns = 3) { entry, modifier ->
                val family = remember(context, entry.axis) { buildGoogleSansFamily(context, entry.axis, weight = 600) }
                SettingsPictureTile(
                    label = entry.label,
                    selected = entry.key == selectedKey,
                    onClick = { actions.onSetFontRoundness(entry.key) },
                    modifier = modifier,
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                        Text("Aa", fontFamily = family, fontSize = 40.sp, lineHeight = 44.sp)
                    }
                }
            }
        }
        SettingsGroup(title = "Preview", footer = "Some phones, such as Xiaomi with HyperOS, may show the system font instead.") {
            SettingsContent {
                LetteringPreviewSamples(titleFamily = titleFamily, bodyFamily = bodyFamily, captionFamily = captionFamily, headerFamily = headerFamily)
            }
        }
    }
}

@Composable
private fun LetteringPreviewSamples(
    titleFamily: FontFamily,
    bodyFamily: FontFamily,
    captionFamily: FontFamily,
    headerFamily: FontFamily,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LetteringPreviewLine("Section", "Your morning stack", headerFamily, 22f, 26f)
        LetteringPreviewLine("Title", "The Daily Briefing", titleFamily, 18f, 22f)
        LetteringPreviewLine("Body", "A short episode about curiosity, craft, and listening habits.", bodyFamily, 15f, 22f)
        LetteringPreviewLine("Caption", "Updated just now · 24 min", captionFamily, 12f, 16f)
    }
}

@Composable
private fun LetteringPreviewLine(label: String, sample: String, fontFamily: FontFamily, fontSizeSp: Float, lineHeightSp: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = sample,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = fontSizeSp.sp,
            lineHeight = lineHeightSp.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
