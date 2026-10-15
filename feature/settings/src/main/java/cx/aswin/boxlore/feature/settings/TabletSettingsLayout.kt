package cx.aswin.boxlore.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding

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
        val split = LocalBoxloreWindowLayout.current.usesTabletLayout && maxWidth >= 672.dp && !config.isOnboarding && destination != ProfileSettingsDestination.Support
        val displayed = if (split && destination == ProfileSettingsDestination.Hub) ProfileSettingsDestination.SyncAndBackups else destination
        Layout(content = {
            Box(Modifier.testTag("settings_category_pane")) {
                if (split) TabletSettingsCategories(displayed, config, onNavigate)
            }
            Box(Modifier.testTag("settings_detail_pane").then(if (split) Modifier.clipToBounds() else Modifier)) {
                CompositionLocalProvider(
                    LocalSettingsSplitPane provides split,
                    LocalSettingsPaneBackVisible provides (!split || destination in listOf(ProfileSettingsDestination.Account, ProfileSettingsDestination.CustomTheme, ProfileSettingsDestination.Theme)),
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

private data class SettingsCategory(val destination: ProfileSettingsDestination, val title: String, val icon: ImageVector)

private val settingsCategories = listOf(
    SettingsCategory(ProfileSettingsDestination.SyncAndBackups, "Cloud Sync & Backups", Icons.Rounded.CloudSync),
    SettingsCategory(ProfileSettingsDestination.Library, "Library", Icons.AutoMirrored.Rounded.LibraryBooks),
    SettingsCategory(ProfileSettingsDestination.Appearance, "Appearance", Icons.Rounded.Palette),
    SettingsCategory(ProfileSettingsDestination.Playback, "Playback", Icons.Rounded.Tune),
    SettingsCategory(ProfileSettingsDestination.Downloads, "Downloads", Icons.Rounded.DownloadForOffline),
    SettingsCategory(ProfileSettingsDestination.Privacy, "Privacy", Icons.Rounded.PrivacyTip),
    SettingsCategory(ProfileSettingsDestination.About, "About", Icons.Rounded.Info),
    SettingsCategory(ProfileSettingsDestination.Support, "Support boxlore", Icons.Rounded.Favorite),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabletSettingsCategories(destination: ProfileSettingsDestination, config: SettingsScreenConfig, onNavigate: (ProfileSettingsDestination) -> Unit) {
    val selected = when (destination) {
        ProfileSettingsDestination.Account -> ProfileSettingsDestination.SyncAndBackups
        ProfileSettingsDestination.Theme, ProfileSettingsDestination.CustomTheme -> ProfileSettingsDestination.Appearance
        else -> destination
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                IconButton(onClick = config.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp), contentPadding = PaddingValues(bottom = boxloreContentBottomPadding(24.dp))) {
            settingsCategories.forEach { category ->
                item(key = category.destination) {
                    NavigationDrawerItem(
                        label = { Text(category.title) },
                        selected = selected == category.destination,
                        onClick = { onNavigate(category.destination) },
                        icon = { Icon(category.icon, contentDescription = null) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            config.onCheckForUpdates?.let { check ->
                item {
                    NavigationDrawerItem(label = { Text(stringResource(R.string.settings_check_for_updates)) }, selected = false, onClick = check, icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) })
                }
            }
        }
    }
}
