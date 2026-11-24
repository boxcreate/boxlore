package cx.aswin.boxlore.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsIconContainer
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportEntryImpression
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportEntrySurface

internal val LocalSettingsSplitPane = staticCompositionLocalOf { false }
internal val LocalSettingsPaneBackVisible = staticCompositionLocalOf { true }

/** Both slots remain composed at the same location when the window crosses the split threshold. */
@Composable
internal fun AdaptiveSettingsLayout(
    destination: ProfileSettingsDestination,
    config: SettingsScreenConfig,
    onNavigate: (ProfileSettingsDestination) -> Unit,
    content: @Composable (ProfileSettingsDestination) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // The app's split rule (wide landscape windows), plus room for a page beside the list.
        val split = LocalBoxloreWindowLayout.current.usesSplitLayout && maxWidth >= 672.dp && !config.isOnboarding && destination != ProfileSettingsDestination.Support
        val displayed = if (split && destination == ProfileSettingsDestination.Hub) ProfileSettingsDestination.SyncAndBackups else destination
        Layout(content = {
            Box(Modifier.testTag("settings_category_pane")) {
                if (split) TabletSettingsCategories(displayed, config, onNavigate)
            }
            Box(Modifier.testTag("settings_detail_pane").then(if (split) Modifier.clipToBounds() else Modifier)) {
                CompositionLocalProvider(
                    LocalSettingsSplitPane provides split,
                    // Pages below a category (Appearance's pages, the theme editor, Account) keep their Back arrow in the split.
                    LocalSettingsPaneBackVisible provides (!split || destination == ProfileSettingsDestination.Account || destination.parent != null),
                ) { content(displayed) }
            }
        }) { measurables, constraints ->
            val gap = if (split) 16.dp.roundToPx() else 0
            val masterWidth = if (split) (constraints.maxWidth * 0.30f).toInt().coerceIn(224.dp.roundToPx(), 296.dp.roundToPx()) else 0
            val master = measurables[0].measure(Constraints.fixed(masterWidth, constraints.maxHeight))
            val detail = measurables[1].measure(Constraints.fixed(constraints.maxWidth - masterWidth - gap, constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (split) master.placeRelative(0, 0)
                detail.placeRelative(masterWidth + gap, 0)
            }
        }
    }
}

/** The colour pair a category wears: its card on the hub and its icon chip in the tablet list. */
internal enum class SettingsCategoryTone { Primary, Secondary, Tertiary, Neutral }

/** [tone]'s container and content colours in the current theme. */
@Composable
internal fun settingsCategoryColors(tone: SettingsCategoryTone): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (tone) {
        SettingsCategoryTone.Primary -> colors.primaryContainer to colors.onPrimaryContainer
        SettingsCategoryTone.Secondary -> colors.secondaryContainer to colors.onSecondaryContainer
        SettingsCategoryTone.Tertiary -> colors.tertiaryContainer to colors.onTertiaryContainer
        SettingsCategoryTone.Neutral -> colors.surfaceContainerHighest to colors.onSurface
    }
}

private data class SettingsCategory(val destination: ProfileSettingsDestination, @StringRes val title: Int, val icon: ImageVector, val tone: SettingsCategoryTone)

private val settingsCategories = listOf(
    SettingsCategory(ProfileSettingsDestination.SyncAndBackups, R.string.settings_page_sync, Icons.Rounded.CloudSync, SettingsCategoryTone.Primary),
    SettingsCategory(ProfileSettingsDestination.Library, R.string.settings_page_library, Icons.AutoMirrored.Rounded.LibraryBooks, SettingsCategoryTone.Secondary),
    SettingsCategory(ProfileSettingsDestination.Appearance, R.string.settings_page_appearance, Icons.Rounded.Palette, SettingsCategoryTone.Primary),
    SettingsCategory(ProfileSettingsDestination.Playback, R.string.settings_page_playback, Icons.Rounded.Tune, SettingsCategoryTone.Tertiary),
    SettingsCategory(ProfileSettingsDestination.Downloads, R.string.settings_page_downloads, Icons.Rounded.DownloadForOffline, SettingsCategoryTone.Neutral),
    SettingsCategory(ProfileSettingsDestination.WearOs, R.string.settings_page_wear_os, Icons.Rounded.Watch, SettingsCategoryTone.Primary),
    SettingsCategory(ProfileSettingsDestination.Privacy, R.string.settings_privacy_title, Icons.Rounded.PrivacyTip, SettingsCategoryTone.Secondary),
    SettingsCategory(ProfileSettingsDestination.About, R.string.settings_category_about, Icons.Rounded.Info, SettingsCategoryTone.Tertiary),
    SettingsCategory(ProfileSettingsDestination.Support, R.string.settings_page_support, Icons.Rounded.Favorite, SettingsCategoryTone.Secondary),
)

/** The tone of [destination]'s category, shared by the hub's cards and the tablet list. */
internal fun settingsCategoryTone(destination: ProfileSettingsDestination): SettingsCategoryTone =
    settingsCategories.first { it.destination == destination }.tone

/** Test tag on the tablet list's Check for updates button. */
internal const val SETTINGS_CHECK_UPDATES_TAG = "settings_check_updates"

/** The categories the split lists; Wear OS appears only for a phone linked with a watch. */
private fun settingsCategoriesFor(showWearOs: Boolean): List<SettingsCategory> =
    if (showWearOs) settingsCategories else settingsCategories.filterNot { it.destination == ProfileSettingsDestination.WearOs }

/** The category the split highlights for [destination]: its top-level parent, and Cloud Sync for Account. */
internal fun settingsCategoryOf(destination: ProfileSettingsDestination): ProfileSettingsDestination =
    if (destination == ProfileSettingsDestination.Account) {
        ProfileSettingsDestination.SyncAndBackups
    } else {
        generateSequence(destination) { it.parent }.last()
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletSettingsCategories(destination: ProfileSettingsDestination, config: SettingsScreenConfig, onNavigate: (ProfileSettingsDestination) -> Unit) {
    val selected = settingsCategoryOf(destination)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_page_hub), style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
                IconButton(onClick = config.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp), contentPadding = PaddingValues(bottom = boxloreContentBottomPadding(24.dp))) {
            settingsCategoriesFor(showWearOs = config.wearOs != null).forEach { category ->
                item(key = category.destination) {
                    if (category.destination == ProfileSettingsDestination.Support) {
                        SupportEntryImpression(SupportEntrySurface.SettingsCategoryList)
                    }
                    val (container, content) = settingsCategoryColors(category.tone)
                    NavigationDrawerItem(
                        label = { Text(stringResource(category.title)) },
                        selected = selected == category.destination,
                        onClick = { onNavigate(category.destination) },
                        icon = { SettingsIconContainer(category.icon, containerColor = container, contentColor = content, size = 36.dp) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            // An action, not a page: set apart under the categories and never shown as picked.
            config.onCheckForUpdates?.let { check ->
                item(key = "check_for_updates") {
                    SettingsDivider()
                    TextButton(
                        onClick = check,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp).heightIn(min = 48.dp).testTag(SETTINGS_CHECK_UPDATES_TAG),
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_check_for_updates), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
