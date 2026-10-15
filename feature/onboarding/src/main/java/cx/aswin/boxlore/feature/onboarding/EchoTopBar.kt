package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll

internal data class EchoTopBarActions(val onBack: () -> Unit, val onSearch: () -> Unit, val onPickTopics: () -> Unit)

/** The menu and About sheet are hoisted so Echo can pause while either is open. */
internal data class EchoMenuState(
    val menuOpen: Boolean,
    val onMenuOpenChange: (Boolean) -> Unit,
    val aboutOpen: Boolean,
    val onAboutOpenChange: (Boolean) -> Unit,
)

/**
 * Back, progress toward "enough to find your shows", Search and a menu. The progress line also
 * carries Echo's busy state: it ripples while a request runs, so no button has to spin.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EchoTopBar(progress: Float, busy: Boolean, searchEnabled: Boolean, actions: EchoTopBarActions, menu: EchoMenuState) {
    val onBack = actions.onBack
    val onSearch = actions.onSearch
    val onPickTopics = actions.onPickTopics
    val menuOpen = menu.menuOpen
    val onMenuOpenChange = menu.onMenuOpenChange
    val aboutOpen = menu.aboutOpen
    val onAboutOpenChange = menu.onAboutOpenChange
    val animated by animateFloatAsState(progress, WavyProgressIndicatorDefaults.ProgressAnimationSpec, label = "Echo progress")
    val progressLabel = stringResource(R.string.echo_progress)
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.echo_back)) }
        LinearWavyProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .testTag("echo_progress")
                .semantics {
                    contentDescription = progressLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
            amplitude = { if (busy) 1f else 0f },
            wavelength = 20.dp,
            waveSpeed = if (busy) 20.dp else 0.dp,
        )
        IconButton(onClick = onSearch, enabled = searchEnabled) { Icon(Icons.Rounded.Search, stringResource(R.string.echo_search)) }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }, modifier = Modifier.testTag("echo_more")) {
                Icon(Icons.Rounded.MoreVert, stringResource(R.string.echo_more_options))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.echo_about)) },
                    leadingIcon = { Icon(Icons.Outlined.Info, null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onAboutOpenChange(true)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.echo_pick_topics)) },
                    leadingIcon = { Icon(Icons.Rounded.Category, null) },
                    onClick = {
                        onMenuOpenChange(false)
                        onPickTopics()
                    },
                )
            }
        }
    }
    if (aboutOpen) EchoAboutSheet(onDismiss = { onAboutOpenChange(false) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EchoAboutSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().tabletSheetScroll().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.echo_about),
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stringResource(R.string.echo_about_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            EchoPrivacyNotice()
        }
    }
}
