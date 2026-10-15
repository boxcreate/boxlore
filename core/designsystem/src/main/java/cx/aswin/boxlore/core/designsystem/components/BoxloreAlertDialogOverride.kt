package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.BasicAlertDialogOverride
import androidx.compose.material3.BasicAlertDialogOverrideScope
import androidx.compose.material3.DefaultBasicAlertDialogOverride
import androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.foldSafeRegions

/** Retains every native Material slot and date picker; only folded tablet hosting changes. */
@OptIn(ExperimentalMaterial3ComponentOverrideApi::class)
internal object BoxloreAlertDialogOverride : BasicAlertDialogOverride {
    @Composable
    override fun BasicAlertDialogOverrideScope.BasicAlertDialog() {
        val window = LocalBoxloreWindowLayout.current
        if (!window.usesTabletLayout || window.foldSafeRegions().size == 1) {
            with(DefaultBasicAlertDialogOverride) { BasicAlertDialog() }
        } else {
            val dialogTitle = stringResource(androidx.compose.material3.R.string.m3c_dialog)
            BoxloreDialog(onDismissRequest, properties, maxWidth = 560.dp) {
                Box(modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp).semantics { paneTitle = dialogTitle }, propagateMinConstraints = true) { content() }
            }
        }
    }
}
