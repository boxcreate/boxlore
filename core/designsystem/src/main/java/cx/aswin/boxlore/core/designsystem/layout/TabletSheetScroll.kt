package cx.aswin.boxlore.core.designsystem.layout

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * For finite sheet and dialog columns; scrolls on every window, so the last action stays reachable
 * on a sideways phone, a split-screen half or a foldable's cover screen. Lazy lists and `weight`
 * children must not sit inside it (they would get an unbounded height); give them a bounded height
 * or keep their own viewport. On a phone sheet the scroll hands its overscroll to the sheet's drag.
 */
fun Modifier.tabletSheetScroll(): Modifier = composed { verticalScroll(rememberScrollState()) }
