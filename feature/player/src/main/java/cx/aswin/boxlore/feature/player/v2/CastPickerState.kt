package cx.aswin.boxlore.feature.player.v2

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.SystemOutputSwitcherDialogController

/**
 * Opens boxlore's Cast sheet, or on Android 17 first asks for local network access and opens Android's
 * output picker when that's declined. The player holds one above its phone and tablet layouts, so the
 * sheet and a permission answer survive a fold, unfold or resize that swaps the layout mid-request.
 */
@Stable
internal class CastPickerState(sheetVisible: MutableState<Boolean>, val onCastClick: () -> Unit) {
    var showSheet by sheetVisible
}

internal val LocalCastPicker = staticCompositionLocalOf<CastPickerState?> { null }

@Composable
internal fun rememberCastPickerState(
    sdkInt: Int = Build.VERSION.SDK_INT,
    hasLocalNetworkAccess: (Context) -> Boolean = ::hasLocalNetworkAccess,
    showSystemPicker: (Context) -> Boolean = ::showSystemCastPicker,
): CastPickerState {
    val context = LocalContext.current
    val sheetVisible = rememberSaveable { mutableStateOf(false) }
    val runCastAction: (CastButtonAction) -> Unit = { action ->
        sheetVisible.value = action == CastButtonAction.SHOW_SHEET ||
            (action == CastButtonAction.SHOW_SYSTEM_PICKER && !showSystemPicker(context))
    }
    val localNetworkRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            runCastAction(castActionAfterLocalNetworkRequest(granted))
        }
    return remember(sheetVisible, localNetworkRequest, context, sdkInt) {
        CastPickerState(sheetVisible) {
            when (val action = castButtonAction(sdkInt, hasLocalNetworkAccess(context))) {
                CastButtonAction.REQUEST_LOCAL_NETWORK -> localNetworkRequest.launch(ACCESS_LOCAL_NETWORK)
                else -> runCastAction(action)
            }
        }
    }
}

/** The Cast sheet for [picker], when it's open. */
@Composable
internal fun CastPickerSheetHost(picker: CastPickerState, isCasting: Boolean) {
    if (picker.showSheet) {
        CastRoutePickerSheet(isCasting = isCasting, onDismiss = { picker.showSheet = false })
    }
}

private fun hasLocalNetworkAccess(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

/** Android's output picker; false when this device can't show it, so the caller falls back to the sheet. */
private fun showSystemCastPicker(context: Context): Boolean =
    runCatching { SystemOutputSwitcherDialogController.showDialog(context) }.getOrDefault(false)
