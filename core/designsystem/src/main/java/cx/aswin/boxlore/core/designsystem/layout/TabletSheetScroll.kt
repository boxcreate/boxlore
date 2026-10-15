package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/** For finite form/menu columns; lazy lists continue to own their own bounded viewport. */
fun Modifier.tabletSheetScroll(): Modifier = composed {
    if (LocalBoxloreWindowLayout.current.usesTabletLayout) verticalScroll(rememberScrollState()) else this
}
